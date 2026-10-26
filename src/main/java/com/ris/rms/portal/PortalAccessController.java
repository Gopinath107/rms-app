package com.ris.rms.portal;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PortalAccessController {
    private final PortalAccessProperties properties;
    public PortalAccessController(PortalAccessProperties properties) { this.properties = properties; }

    @GetMapping("/api/portal/access")
    public ResponseEntity<Map<String, Object>> access(HttpServletRequest request) {
        Object access = request.getAttribute(PortalAccessClient.Access.class.getName());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("success", true,
                "result", Map.of("managed", properties.isEnabled(), "permissions", access instanceof PortalAccessClient.Access context ? context.permissions() : java.util.Set.of())));
    }
}
