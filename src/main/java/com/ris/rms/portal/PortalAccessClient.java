package com.ris.rms.portal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ris.rms.entity.UserAccount;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PortalAccessClient {
    private final PortalAccessProperties properties;
    private final RestTemplate http;
    private final ObjectMapper mapper;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();
    public record Access(long portalUserId, long portalEmployeeId, Set<String> permissions, Set<Long> companyIds) { }
    private record Cached(Instant expires, Long companyId, String email, Access access) { }

    public PortalAccessClient(PortalAccessProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000); factory.setReadTimeout(5000);
        this.http = new RestTemplate(factory);
    }

    public Access access(UserAccount user) {
        if (!properties.isEnabled()) return null;
        Cached existing = cache.get(user.getUserId());
        if (existing != null && existing.expires().isAfter(Instant.now())
                && Objects.equals(existing.companyId(), user.getCompanyId()) && Objects.equals(existing.email(), user.getEmail())) return existing.access();
        JsonNode result = request("/integrations/rms/access", HttpMethod.POST, Map.of(
                "rmsUserId", user.getUserId(), "rmsCompanyId", user.getCompanyId(), "email", user.getEmail()));
        Set<String> permissions = new HashSet<>();
        result.path("permissions").forEach(code -> permissions.add(code.asText()));
        Set<Long> companies = new HashSet<>();
        result.path("companyIds").forEach(id -> companies.add(id.asLong()));
        if (!permissions.contains("RMS_ACCESS") || !companies.contains(user.getCompanyId())) throw new AccessDeniedException("Your account is not authorized for RMS in RIS Portal");
        Access access = new Access(result.path("portalUserId").asLong(), result.path("portalEmployeeId").asLong(), Set.copyOf(permissions), Set.copyOf(companies));
        if (cache.size() > 2000) cache.clear();
        cache.put(user.getUserId(), new Cached(Instant.now().plusSeconds(5), user.getCompanyId(), user.getEmail(), access));
        return access;
    }

    public Set<Long> eligible(long companyId) {
        JsonNode result = request("/integrations/rms/internal-employees?companyId=" + companyId, HttpMethod.GET, null);
        if (!result.isArray()) throw new Unavailable();
        Set<Long> ids = new HashSet<>();
        result.forEach(row -> { if (row.path("rmsCompanyId").asLong() == companyId) ids.add(row.path("rmsEmployeeId").asLong()); });
        return Set.copyOf(ids);
    }

    private JsonNode request(String path, HttpMethod method, Object data) {
        try {
            URI base = URI.create(properties.getApiUrl().replaceAll("/+$", ""));
            if (properties.getIntegrationKey().isBlank() || base.getHost() == null || base.getUserInfo() != null
                    || base.getQuery() != null || base.getFragment() != null || !Set.of("http", "https").contains(base.getScheme())) throw new Unavailable();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-RIS-Integration-Key", properties.getIntegrationKey());
            String body = http.exchange(base + path, method, new HttpEntity<>(data, headers), String.class).getBody();
            if (body == null || body.length() > 2_000_000) throw new Unavailable();
            JsonNode envelope = mapper.readTree(body);
            if (!envelope.path("success").asBoolean() || envelope.path("result").isMissingNode()) throw new Unavailable();
            return envelope.path("result");
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.Unauthorized exception) {
            throw new AccessDeniedException("Your RMS access has not been granted or was removed in RIS Portal");
        } catch (AccessDeniedException exception) { throw exception; }
        catch (Exception exception) { throw new Unavailable(); }
    }

    public static class Unavailable extends RuntimeException {
        public Unavailable() { super("RIS Portal access verification is temporarily unavailable. Please retry."); }
    }
}
