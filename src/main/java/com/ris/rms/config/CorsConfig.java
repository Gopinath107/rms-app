package com.ris.rms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(
                    // Production HTTPS origins (when using Nginx SSL)
                    "https://16.112.94.107",           // Production HTTPS server
                    "http://16.112.94.107:3001",       // Dev server on production IP
                    "http://localhost:*",              // Any local dev port (3000, 3001, 5173, 5174, 5175, etc.)
                    "http://127.0.0.1:*",              // Any local IPv4 port
                    "http://[::1]:*"                   // Any local IPv6 port
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders("Authorization", "X-Total-Count") // Expose custom headers if needed
                .allowCredentials(true) 
                .maxAge(3600); 
                
    }

    @org.springframework.context.annotation.Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        configuration.setAllowedOriginPatterns(java.util.Arrays.asList(
            "https://16.112.94.107",
            "http://16.112.94.107:3001",
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://[::1]:*"
        ));
        configuration.setAllowedMethods(java.util.Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(java.util.Arrays.asList("*"));
        configuration.setExposedHeaders(java.util.Arrays.asList("Authorization", "X-Total-Count"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}