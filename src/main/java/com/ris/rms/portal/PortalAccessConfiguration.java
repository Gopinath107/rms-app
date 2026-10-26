package com.ris.rms.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ris.rms.repository.UserAccountRepository;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class PortalAccessConfiguration {
    @Bean PortalAccessFilter portalAccessFilter(PortalAccessProperties properties, PortalAccessClient client,
            UserAccountRepository users, InternalResourcingPolicy resourcing, ObjectMapper mapper, JdbcTemplate jdbc, PortalCompanyScope scope) {
        return new PortalAccessFilter(properties, client, users, resourcing, mapper, jdbc, scope);
    }
    @Bean FilterRegistrationBean<PortalAccessFilter> portalFilterRegistration(PortalAccessFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
