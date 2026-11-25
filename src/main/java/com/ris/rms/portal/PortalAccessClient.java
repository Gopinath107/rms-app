package com.ris.rms.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ris.rms.entity.UserAccount;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PortalAccessClient {
    private final PortalAccessProperties properties;
    private final RisPortalEmployeeService risPortalEmployeeService;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    public record Access(long portalUserId, long portalEmployeeId, Set<String> permissions, Set<Long> companyIds) { }
    private record Cached(Instant expires, Long companyId, String email, Access access) { }

    public PortalAccessClient(PortalAccessProperties properties, RisPortalEmployeeService risPortalEmployeeService) {
        this.properties = properties;
        this.risPortalEmployeeService = risPortalEmployeeService;
    }

    public Access access(UserAccount user) {
        if (!properties.isEnabled()) return null;
        Cached existing = cache.get(user.getUserId());
        if (existing != null && existing.expires().isAfter(Instant.now())
                && Objects.equals(existing.companyId(), user.getCompanyId()) && Objects.equals(existing.email(), user.getEmail())) {
            return existing.access();
        }

        // Direct verification using RIS Identity database (Single Source of Truth)
        Optional<Access> directAccess = risPortalEmployeeService.getPortalAccess(user.getEmail(), user.getCompanyId());
        if (directAccess.isPresent()) {
            Access access = directAccess.get();
            if (cache.size() > 2000) cache.clear();
            cache.put(user.getUserId(), new Cached(Instant.now().plusSeconds(60), user.getCompanyId(), user.getEmail(), access));
            return access;
        }

        throw new AccessDeniedException("Your RMS access has not been granted or was removed in RIS Portal");
    }

    public Set<Long> eligible(long companyId) {
        Set<Long> ids = risPortalEmployeeService.getEligibleInternalEmployees(companyId);
        if (!ids.isEmpty()) {
            return ids;
        }
        return Set.of();
    }

    public static class Unavailable extends RuntimeException {
        public Unavailable() { super("RIS Portal access verification is temporarily unavailable. Please retry."); }
    }
}
