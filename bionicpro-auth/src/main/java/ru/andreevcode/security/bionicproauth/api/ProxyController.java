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

    @Value("${REPORTS_BACKEND_URL:http://bionicpro-reports:8082}")
    private String reportsBackendUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/v1/reports/**")
    public ResponseEntity<?> proxyReports(
            HttpServletRequest request,
            @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient authorizedClient) {

        log.info("Proxying request to backend: {}", request.getRequestURI());
        // 1. Достаем Access Token из авторизованного клиента (он лежит в сессии)
        String accessToken = authorizedClient.getAccessToken().getTokenValue();

        // Важно: Сохраняем query string (user_id, даты), иначе бэкенд их не увидит
        String queryString = request.getQueryString() != null ? "?" + request.getQueryString() : "";

        // 2. Формируем запрос к реальному бэкенду (localhost:8000)
        // Если бэкенд ожидает /api/v1/reports, то заменяем аккуратно.
        // Если мы в BFF вызвали /api/v1/reports, то URI будет "/api/v1/reports"
        String backendUrl = reportsBackendUrl + request.getRequestURI() + queryString;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken); // Приклеиваем токен!

        log.info("Sending request to backend with headers: {}", headers);
        // 4. Проксируем и возвращаем ответ фронтенду
        return restTemplate.exchange(
                backendUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class
        );
    }
}