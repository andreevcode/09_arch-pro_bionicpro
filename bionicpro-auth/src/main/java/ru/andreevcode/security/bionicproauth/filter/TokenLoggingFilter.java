package ru.andreevcode.security.bionicproauth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class TokenLoggingFilter extends OncePerRequestFilter {

    private final OAuth2AuthorizedClientRepository authorizedClientRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof OAuth2AuthenticationToken oauthToken) {
            OAuth2AuthorizedClient client = authorizedClientRepository
                    .loadAuthorizedClient(oauthToken.getAuthorizedClientRegistrationId(), auth, request);

            if (client != null) {
                String sessionId = request.getSession(false) != null ? request.getSession(false).getId() : "N/A";

                String user = auth.getName();

                var at = client.getAccessToken();
                var rt = client.getRefreshToken();

                String rtExpires = (rt != null && rt.getExpiresAt() != null) ? rt.getExpiresAt().toString() : "Not mapped by Spring";

                String atValue = at.getTokenValue();
                String atStart = atValue.substring(0, 15);
                String atEnd = atValue.substring(atValue.length() - 15);

                String rtValue = rt != null ? rt.getTokenValue() : null;
                String rtStart = rtValue == null ? "" : rtValue.substring(0, 15);
                String rtEnd = rtValue == null ? "" : rtValue.substring(rtValue.length() - 15);

                log.info("=== INCOMING REQUEST ===");
                log.info("URI: {}", request.getRequestURI());
                log.info("User: {}", user);
                log.info("JSESSIONID: {}", sessionId);
                log.info("AT: {}...{} | Expires: {}", atStart, atEnd, at.getExpiresAt());

                if (rt != null) {
                    log.info("RT: {}...{} | Expires: {}", rtStart, rtEnd, rtExpires);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}