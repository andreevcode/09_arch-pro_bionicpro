package ru.andreevcode.security.bionicproauth.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpStatusCodeException;

@RestControllerAdvice
public class GlobalProxyExceptionHandler {
    private final Logger log = LoggerFactory.getLogger(GlobalProxyExceptionHandler.class);

    // Ловим все ошибки статусов от бэкендов (4xx и 5xx)
    @ExceptionHandler(HttpStatusCodeException.class)
    public ResponseEntity<byte[]> handleHttpStatusCodeException(HttpStatusCodeException e) {
        log.warn("Proxy error: {} - {}", e.getStatusCode(), e.getStatusText());

        // Просто пробрасываем статус и тело ответа обратно клиенту
        return ResponseEntity
                .status(e.getStatusCode())
                .headers(e.getResponseHeaders())
                .body(e.getResponseBodyAsByteArray());
    }

    // Ловим ошибки, если бэкенд вообще недоступен (например, Connection Refused)
    @ExceptionHandler(org.springframework.web.client.ResourceAccessException.class)
    public ResponseEntity<String> handleResourceAccessException(Exception e) {
        log.error("Backend service is unreachable: {}", e.getMessage());
        return ResponseEntity.status(502).body("Service Unavailable (Backend is down)");
    }
}
