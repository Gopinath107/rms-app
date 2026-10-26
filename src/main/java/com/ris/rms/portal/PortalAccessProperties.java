package com.ris.rms.portal;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties("app.portal")
public class PortalAccessProperties {
    private boolean enabled;
    private String apiUrl = "";
    private String integrationKey = "";
}
