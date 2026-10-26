package com.ris.rms.portal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ris.rms.repository.UserAccountRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Portal access is enforced for existing RMS sessions as well as new logins. */
public class PortalAccessFilter extends OncePerRequestFilter {
    private final PortalAccessProperties properties;
    private final PortalAccessClient client;
    private final UserAccountRepository users;
    private final InternalResourcingPolicy resourcing;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final PortalCompanyScope companyScope;
    private static final Set<String> SERVICE_READS = Set.of("/api/demands/list", "/api/demands/DemandFlowList", "/api/companies/list", "/api/employees/list");

    public PortalAccessFilter(PortalAccessProperties properties, PortalAccessClient client, UserAccountRepository users,
                              InternalResourcingPolicy resourcing, ObjectMapper mapper, JdbcTemplate jdbc, PortalCompanyScope companyScope) {
        this.properties = properties; this.client = client; this.users = users;
        this.resourcing = resourcing; this.mapper = mapper; this.jdbc = jdbc;
        this.companyScope = companyScope;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!properties.isEnabled() || !path.startsWith("/api/") || request.getMethod().equals("OPTIONS")
                || path.startsWith("/api/auth/")) { chain.doFilter(request, response); return; }
        try {
            String key = request.getHeader("X-RIS-Integration-Key");
            if (key != null && !properties.getIntegrationKey().isBlank() && request.getMethod().equals("GET") && SERVICE_READS.contains(path)
                    && MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), properties.getIntegrationKey().getBytes(StandardCharsets.UTF_8))) {
                var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("RIS_INTEGRATION", null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute("ris.integration", true);
                chain.doFilter(request, response); return;
            }
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) throw new AccessDeniedException("Sign in with an RMS account authorized in RIS Portal");
            var user = users.findByEmailIgnoreCase(authentication.getName()).filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                    .orElseThrow(() -> new AccessDeniedException("RMS account is inactive"));
            PortalAccessClient.Access access = client.access(user);
            request.setAttribute(PortalAccessClient.Access.class.getName(), access);
            request.setAttribute("rms.userId", user.getUserId());
            String permission = permission(path, request.getMethod());
            if (permission != null && !access.permissions().contains(permission)) throw new AccessDeniedException("This RMS action has not been granted in RIS Portal");
            // Candidate records and their workflows stay in RMS; only internal employee eligibility is checked below.
            boolean candidateEndpoint = path.startsWith("/api/candidates") || path.equals("/api/candidateFlows") || path.equals("/api/flowCandidateID");
            JsonNode body = mapper.createObjectNode();
            HttpServletRequest secured = request;
            if (!candidateEndpoint && request.getContentType() != null && request.getContentType().startsWith("application/json")) {
                byte[] bytes = request.getInputStream().readNBytes(1_048_577);
                if (bytes.length > 1_048_576) { error(response, 413, "Request is too large"); return; }
                body = bytes.length == 0 ? mapper.createObjectNode() : mapper.readTree(bytes);
                secured = new BodyRequest(request, bytes);
            }
            if (!candidateEndpoint) {
                checkCompany(request.getParameter("companyId"), access);
                if (body.hasNonNull("companyId")) checkCompany(body.get("companyId").asText(), access);
                for (String name : List.of("demandId", "projectId", "accountId", "departmentId", "requestId", "interviewId", "allocationId", "groupId")) {
                    Long id = number(request.getParameter(name));
                    if (id != null) {
                        companyScope.check(name.toLowerCase(Locale.ROOT), id, access.companyIds());
                        if (name.equals("interviewId")) companyScope.checkInterviewAssignment(id, access, user.getUserId());
                    }
                }
                checkBodyScope(body, access, user.getUserId());
                String[] pathParts = path.split("/");
                if (pathParts.length >= 3) {
                    String kind = switch (pathParts[2]) { case "employees" -> "employeeid"; case "demands" -> "demandid"; case "projects" -> "projectid"; case "accounts" -> "accountid"; case "interviews" -> "interviewid"; case "allocations" -> "allocationid"; case "resource-requests" -> "requestid"; default -> null; };
                    String raw = pathParts.length >= 4 && pathParts[3].matches("[0-9]+") ? pathParts[3] : request.getParameter("id");
                    if (kind != null && raw != null) {
                        long id = Long.parseLong(raw);
                        companyScope.check(kind, id, access.companyIds());
                        if (kind.equals("interviewid")) companyScope.checkInterviewAssignment(id, access, user.getUserId());
                    }
                }
                Long employeeId = number(request.getParameter("employeeId"));
                if (employeeId == null && body.hasNonNull("employeeId")) employeeId = number(body.path("employeeId").asText());
                if (employeeId != null) checkEmployeeCompany(employeeId, access);
                boolean newPlacement = path.contains("share-resume") || path.equals("/api/allocations/create") || path.equals("/api/resource-requests/create");
                if (newPlacement && employeeId != null) resourcing.requireAvailable(employeeId);
                if (newPlacement && body.path("employeeIds").isArray()) for (JsonNode id : body.path("employeeIds")) { checkEmployeeCompany(id.asLong(), access); resourcing.requireAvailable(id.asLong()); }
            }
            Long defaultCompany = user.getCompanyId();
            HttpServletRequest delegate = secured;
            final boolean preserveCandidateParameters = candidateEndpoint;
            secured = new HttpServletRequestWrapper(delegate) {
                @Override public String getParameter(String name) {
                    String value = super.getParameter(name);
                    return !preserveCandidateParameters && name.equals("companyId") && (value == null || value.isBlank()) ? defaultCompany.toString() : value;
                }
                @Override public String[] getParameterValues(String name) {
                    return !preserveCandidateParameters && name.equals("companyId") ? new String[]{getParameter(name)} : super.getParameterValues(name);
                }
                @Override public Map<String, String[]> getParameterMap() {
                    Map<String, String[]> map = new HashMap<>(super.getParameterMap());
                    if (!preserveCandidateParameters) map.put("companyId", getParameterValues("companyId"));
                    return Collections.unmodifiableMap(map);
                }
            };
            chain.doFilter(secured, response);
        } catch (AccessDeniedException exception) { error(response, 403, exception.getMessage()); }
        catch (PortalAccessClient.Unavailable exception) { error(response, 503, exception.getMessage()); }
        catch (NumberFormatException exception) { error(response, 400, "Invalid RMS identifier"); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) { error(response, 400, "Invalid request body"); }
        catch (org.springframework.dao.DataAccessException exception) { error(response, 503, "RMS access verification is temporarily unavailable. Please retry."); }
    }

    static String permission(String path, String method) {
        boolean read = method.equals("GET");
        if (path.startsWith("/api/employees/")) {
            if (path.contains("viewResume")) return "RMS_RESUME_READ";
            if (path.contains("share-resume")) return "RMS_RESUME_SHARE";
            return read ? "RMS_EMPLOYEE_READ" : "RMS_EMPLOYEE_WRITE";
        }
        if (path.startsWith("/api/demands/")) return read ? "RMS_DEMAND_READ" : "RMS_DEMAND_WRITE";
        if (path.startsWith("/api/resource-requests/")) return read ? "RMS_DEMAND_READ" : "RMS_DEMAND_WRITE";
        if (path.equals("/api/employeeFlows") || path.equals("/api/flowEmployeeID")) return "RMS_EMPLOYEE_READ";
        if (path.startsWith("/api/interviews/")) return read ? "RMS_INTERVIEW_READ" : "RMS_INTERVIEW_WRITE";
        if (path.startsWith("/api/allocations/")) return read ? "RMS_ALLOCATION_READ" : "RMS_ALLOCATION_WRITE";
        if (path.startsWith("/api/user-accounts/") || path.startsWith("/api/roles/")) return "RMS_ADMIN";
        return null;
    }

    private void checkEmployeeCompany(long employeeId, PortalAccessClient.Access access) {
        var companies = jdbc.queryForList("SELECT company_id FROM rms.employee WHERE employee_id=?", Long.class, employeeId);
        if (companies.size() != 1 || !access.companyIds().contains(companies.getFirst())) throw new AccessDeniedException("Internal employee is outside your company access");
    }

    private void checkCompany(String value, PortalAccessClient.Access access) {
        if (value != null && !value.isBlank() && !access.companyIds().contains(Long.parseLong(value))) throw new AccessDeniedException("Company is outside your RMS access");
    }

    private void checkBodyScope(JsonNode node, PortalAccessClient.Access access, long userId) {
        if (node.isArray()) { node.forEach(item -> checkBodyScope(item, access, userId)); return; }
        if (!node.isObject()) return;
        node.fields().forEachRemaining(entry -> {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (Set.of("demandid", "projectid", "accountid", "departmentid", "requestid", "interviewid", "allocationid", "groupid", "employeeid").contains(key) && !entry.getValue().isNull()) {
                companyScope.check(key, Long.parseLong(entry.getValue().asText()), access.companyIds());
                if (key.equals("interviewid")) companyScope.checkInterviewAssignment(Long.parseLong(entry.getValue().asText()), access, userId);
            } else if (entry.getValue().isContainerNode()) checkBodyScope(entry.getValue(), access, userId);
        });
    }

    private Long number(String value) { return value == null || value.isBlank() ? null : Long.valueOf(value); }

    private void error(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), Map.of("success", false, "message", message, "errors", List.of(message)));
    }

    private static class BodyRequest extends HttpServletRequestWrapper {
        private final byte[] bytes;
        BodyRequest(HttpServletRequest request, byte[] bytes) { super(request); this.bytes = bytes; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(bytes);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
    }
}
