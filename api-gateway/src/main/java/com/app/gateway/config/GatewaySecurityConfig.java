package com.app.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * ══════════════════════════════════════════════════════════════════
 * THIS FILE WAS THE ROOT CAUSE OF ALL 403 FORBIDDEN ERRORS
 * ══════════════════════════════════════════════════════════════════
 *
 * The api-gateway has spring-boot-starter-security on the classpath
 * (required for JWT parsing in JwtAuthFilter), but had NO SecurityConfig.
 *
 * Spring Security's defaults then applied automatically:
 *   ✗ CSRF protection ENABLED  → every POST/PUT/DELETE from browser → 403
 *   ✗ HTTP Basic login enabled → adds unwanted challenge responses
 *   ✗ Form login enabled       → redirects instead of returning 403/401
 *
 * This caused 100% of the "403 Forbidden" errors seen in the browser console:
 *   POST /api/v1/auth/login    → 403  (CSRF blocked)
 *   POST /api/v1/auth/register → 403  (CSRF blocked)
 *   POST /api/v1/quantities/convert → 403  (CSRF blocked)
 *
 * The gateway is a STATELESS reverse-proxy. All security decisions
 * (JWT validation, route-level auth) are handled inside JwtAuthFilter
 * (a GatewayFilterFactory). Spring Security at the gateway level should
 * simply pass all traffic through — routing rules own access control.
 *
 * FIX: Disable CSRF + permit all at the Spring Security level.
 *       The gateway's own JwtAuthFilter still enforces JWT on protected routes.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            // ── CSRF: DISABLE ──────────────────────────────────────────────
            // Gateway is stateless; CSRF tokens are meaningless here.
            // Without disabling this, every POST from the browser gets 403.
            .csrf(ServerHttpSecurity.CsrfSpec::disable)

            // ── HTTP Basic: DISABLE ────────────────────────────────────────
            // Prevents the browser from showing a Basic Auth dialog.
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)

            // ── Form Login: DISABLE ────────────────────────────────────────
            // Prevents Spring from redirecting unauthenticated requests
            // to a login page (which would break API clients).
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)

            // ── Authorization: PERMIT ALL ──────────────────────────────────
            // The gateway's JwtAuthFilter (a GatewayFilterFactory) is what
            // enforces JWT on protected routes — not Spring Security here.
            // Locking down here would double-block and interfere with routing.
            .authorizeExchange(exchanges -> exchanges
                .anyExchange().permitAll()
            );

        return http.build();
    }
}
