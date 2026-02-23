package ru.andreevcode.security.bionicproauth.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api")
public class ProxyController {
    private final Logger log = LoggerFactory.getLogger(ProxyController.class);

    @Value("${REPORTS_BACKEND_URL:http://reports-backend:8000}")
    private String reportsBackendUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/reports/**")
    public ResponseEntity<?> proxyReports(
            HttpServletRequest request,
            @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient authorizedClient) {

        log.info("Proxying request to backend: {}", request.getRequestURI());
        // 1. Достаем Access Token из авторизованного клиента (он лежит в сессии)
        String accessToken = authorizedClient.getAccessToken().getTokenValue();

        // 2. Формируем запрос к реальному бэкенду (localhost:8000)
        String backendUrl = reportsBackendUrl + request.getRequestURI().replace("/api", "");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken); // Приклеиваем токен!

        HttpEntity<String> entity = new HttpEntity<>(headers);

        log.info("Sending request to backend with headers: {}", headers);
        // 4. Проксируем и возвращаем ответ фронтенду
        return restTemplate.exchange(backendUrl, HttpMethod.GET, entity, byte[].class);
    }
}
