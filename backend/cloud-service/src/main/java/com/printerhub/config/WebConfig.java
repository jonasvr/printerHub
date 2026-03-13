package com.printerhub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Central CORS configuration.
 *
 * Replaces the per-controller @CrossOrigin annotations so allowed origins
 * are controlled from application.yml (or the PRINTERHUB_ALLOWED_ORIGINS
 * environment variable) rather than being hardcoded in the source.
 *
 * To allow multiple origins in production set:
 *   printerhub.allowed-origins: https://app.example.com,https://admin.example.com
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${printerhub.allowed-origins:http://localhost:4200}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
