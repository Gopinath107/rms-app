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
                    "http://127.0.0.1:5174"            // Vite IPv4 fallback
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders("Authorization", "X-Total-Count") // Expose custom headers if needed
                .allowCredentials(true) 
                .maxAge(3600); 
                
    }
}