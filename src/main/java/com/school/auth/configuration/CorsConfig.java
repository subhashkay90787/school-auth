package com.school.auth.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Cross-origin access for the browser (Flutter web) app. Off unless app.cors.allowed-origins
 * (env CORS_ALLOWED_ORIGINS, comma separated, e.g. https://app.example.com,http://localhost:5173) is set.
 * Patterns are allowed (http://localhost:*). Calls carry bearer tokens, never cookies, so credentials stay off.
 */
@Configuration
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:}") String origins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> list = Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (list.isEmpty()) return source;
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOriginPatterns(list);
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setExposedHeaders(List.of("Location", "Content-Disposition", "Retry-After", "X-Request-Id"));
        c.setAllowCredentials(false);
        c.setMaxAge(Duration.ofHours(1));
        source.registerCorsConfiguration("/**", c);
        return source;
    }
}
