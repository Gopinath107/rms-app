package com.ris.rms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                
                .allowedOrigins(
                    // Production HTTPS origins (when using Nginx SSL)
                    "https://16.112.94.107",           // Production HTTPS server
                    "http://16.112.94.107:3001",       // Dev server on production IP
                    "http://localhost:3001",           // Local React dev server (CRA)
                    "http://localhost:3000",           // Alternative local port
                    "http://127.0.0.1:3001",           // Local IPv4
                    "http://127.0.0.1:3000",           // Alternative local IPv4
                    "http://localhost:5173",           // Vite dev server (default)
                    "http://localhost:5174",           // Vite dev server (fallback port)
                    "http://127.0.0.1:5173",           // Vite IPv4
                    "http://127.0.0.1:5174",           // Vite IPv4 fallback
                    "http://[::1]:5173",               // Vite IPv6
                    "http://[::1]:5174",               // Vite IPv6 fallback
                    "http://[::1]:3000",               // Local IPv6
                    "http://[::1]:3001"                // Local IPv6 fallback
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
        configuration.setAllowedOrigins(java.util.Arrays.asList(
            "https://16.112.94.107",
            "http://16.112.94.107:3001",
            "http://localhost:3001",
            "http://localhost:3000",
            "http://127.0.0.1:3001",
            "http://127.0.0.1:3000",
            "http://localhost:5173",
            "http://localhost:5174",
            "http://127.0.0.1:5173",
            "http://127.0.0.1:5174",
            "http://[::1]:5173",
            "http://[::1]:5174",
            "http://[::1]:3000",
            "http://[::1]:3001"
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