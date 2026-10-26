package com.ris.rms.portal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PortalCompanyScope {
    private final JdbcTemplate jdbc;

    private static final String REQUEST_FROM = " FROM rms.resource_request rr LEFT JOIN rms.demand d ON d.demandid=rr.demand_id LEFT JOIN rms.project p ON p.project_id=rr.project_id LEFT JOIN rms.user_account u ON u.user_id=rr.requester_id ";
    private static final String ASSIGNED = "(i.employee_id IS NULL OR i.interviewer_id=? OR rr.requester_id=? OR (CASE WHEN jsonb_typeof(i.level_progress)='array' THEN i.level_progress ELSE '[]'::jsonb END) @> jsonb_build_array(jsonb_build_object('interviewerUserId', ?::bigint)))";

    public void check(String kind, long id, Set<Long> allowed) {
        String sql = switch (kind) {
            case "demandid" -> "SELECT company_id FROM rms.demand WHERE demandid=?";
            case "projectid" -> "SELECT company_id FROM rms.project WHERE project_id=?";
            case "accountid" -> "SELECT company_id FROM rms.account WHERE account_id=?";
            case "departmentid" -> "SELECT company_id FROM rms.department WHERE department_id=?";
            case "employeeid" -> "SELECT company_id FROM rms.employee WHERE employee_id=?";
            case "requestid" -> "SELECT coalesce(d.company_id,p.company_id,u.company_id)" + REQUEST_FROM + " WHERE rr.request_id=?";
            case "groupid" -> "SELECT company_id FROM rms.resource_request_group WHERE group_id=?";
            case "interviewid" -> "SELECT coalesce(d.company_id,p.company_id,u.company_id) FROM rms.interview i JOIN rms.resource_request rr ON rr.request_id=i.request_id LEFT JOIN rms.demand d ON d.demandid=rr.demand_id LEFT JOIN rms.project p ON p.project_id=rr.project_id LEFT JOIN rms.user_account u ON u.user_id=rr.requester_id WHERE i.interview_id=?";
            case "allocationid" -> "SELECT coalesce(p.company_id,d.company_id,e.company_id,c.company_id) FROM rms.allocation a LEFT JOIN rms.project p ON p.project_id=a.project_id LEFT JOIN rms.resource_request rr ON rr.request_id=a.request_id LEFT JOIN rms.demand d ON d.demandid=rr.demand_id LEFT JOIN rms.employee e ON e.employee_id=a.employee_id LEFT JOIN rms.candidate c ON c.candidate_id=a.candidate_id WHERE a.allocation_id=?";
            default -> null;
        };
        if (sql == null) return;
        var companies = jdbc.queryForList(sql, Long.class, id);
        if (id <= 0 || companies.isEmpty() || companies.stream().anyMatch(company -> company == null || !allowed.contains(company))) throw new AccessDeniedException("Record is outside your RMS company access");
    }

    public List<Long> interviewIds() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return null;
        Object value = attributes.getRequest().getAttribute(PortalAccessClient.Access.class.getName());
        if (!(value instanceof PortalAccessClient.Access access)) return null;
        var companies = access.companyIds();
        if (companies.isEmpty()) return List.of();
        String placeholders = String.join(",", Collections.nCopies(companies.size(), "?"));
        String sql = "SELECT i.interview_id FROM rms.interview i JOIN rms.resource_request rr ON rr.request_id=i.request_id LEFT JOIN rms.demand d ON d.demandid=rr.demand_id LEFT JOIN rms.project p ON p.project_id=rr.project_id LEFT JOIN rms.user_account u ON u.user_id=rr.requester_id WHERE coalesce(d.company_id,p.company_id,u.company_id) IN (" + placeholders + ")";
        List<Object> args = new ArrayList<>(companies);
        if (!access.permissions().contains("RMS_INTERVIEW_ALL")) {
            Object userId = attributes.getRequest().getAttribute("rms.userId");
            if (!(userId instanceof Long)) return List.of();
            sql += " AND " + ASSIGNED;
            args.add(userId); args.add(userId); args.add(userId);
        }
        return jdbc.queryForList(sql, Long.class, args.toArray());
    }

    public void checkInterviewAssignment(long id, PortalAccessClient.Access access, long userId) {
        if (access.permissions().contains("RMS_INTERVIEW_ALL")) return;
        Long count = jdbc.queryForObject("SELECT count(*) FROM rms.interview i JOIN rms.resource_request rr ON rr.request_id=i.request_id WHERE i.interview_id=? AND " + ASSIGNED, Long.class, id, userId, userId, userId);
        if (count == null || count != 1) throw new AccessDeniedException("This internal interview is not assigned to you or requested by you");
    }
}
