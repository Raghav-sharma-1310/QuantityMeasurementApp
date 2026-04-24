package com.app.auth.security.oauth2;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.app.auth.entity.User;
import com.app.auth.security.UserPrincipal;
import com.app.auth.security.jwt.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final String redirectUri;

    public OAuth2AuthenticationSuccessHandler(
            JwtTokenProvider jwtTokenProvider,
            @Value("${app.oauth2.redirect-uri:http://localhost:5173/}")
            String redirectUri) {

        this.jwtTokenProvider = jwtTokenProvider;
        this.redirectUri = redirectUri;
    }
    
    @Override
    public void onAuthenticationSuccess(HttpServletRequest  request,
                                        HttpServletResponse response,
                                        Authentication      authentication) throws IOException {
        
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userPrincipal.getUser();

        String roleAuthority = userPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "));

        String token = jwtTokenProvider.generateTokenFromEmail(user.getEmail(), roleAuthority);

        // Encode the actual user's name to handle spaces (e.g., "John Doe" -> "John%20Doe")
        String encodedName = URLEncoder.encode(user.getName(), StandardCharsets.UTF_8.toString());

        log.info("OAuth2 login successful for: " + user.getEmail() + " — Redirecting to frontend.");

        // Attach BOTH the token and the real name to the URL
        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("token", token)
                .queryParam("name", encodedName)
                .build()
                .toUriString();

        super.clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}