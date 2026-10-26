package com.ris.rms.portal;

import com.ris.rms.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class InternalResourcingPolicy {
    private final PortalAccessProperties properties;
    private final PortalAccessClient client;
    private final EmployeeRepository employees;

    public boolean enabled() { return properties.isEnabled(); }

    /** Staff account administration is independent from eligibility for job demands. */
    public boolean filterDirectory() {
        if (!enabled()) return false;
        if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
                instanceof org.springframework.web.context.request.ServletRequestAttributes attributes) {
            var request = attributes.getRequest();
            Object value = request.getAttribute(PortalAccessClient.Access.class.getName());
            if ("RMS_ACCOUNT".equals(request.getParameter("purpose")) && value instanceof PortalAccessClient.Access access
                    && access.permissions().contains("RMS_ADMIN")) return false;
        }
        return true;
    }

    public Set<Long> eligible(long companyId) { return client.eligible(companyId); }

    public void requireAvailable(long employeeId) {
        if (!enabled()) return;
        var employee = employees.findById(employeeId).orElseThrow(() -> new AccessDeniedException("Internal employee not found"));
        if (!eligible(employee.getCompanyId()).contains(employeeId)) throw new AccessDeniedException("This internal employee is not available for resourcing in RIS Portal");
    }
}
