package com.app.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * FIX: This file was MISSING — SecurityConfig required CorsConfigurationSource
 * as a constructor argument but no bean was defined anywhere, causing
 * auth-service to FAIL TO START with NoSuchBeanDefinitionException.
 *
 * This bean is injected into SecurityConfig and applied via:
 *   http.cors(cors -> cors.configurationSource(corsConfigurationSource))
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Allow the frontend origin (Vite dev server)
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));

        // Allow all standard HTTP methods including OPTIONS (preflight)
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Allow all headers the frontend sends
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "Origin"
        ));

        // Expose Authorization so the frontend can read it
        config.setExposedHeaders(List.of("Authorization"));

        // Required for sending cookies / Authorization headers cross-origin
        config.setAllowCredentials(true);

        // Cache preflight response for 1 hour
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
