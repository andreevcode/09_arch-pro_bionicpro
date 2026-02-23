package ru.andreevcode.security.bionicproauth.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.io.IOException;

/**
 * Хотя Spring Security защищает от Session Fixation при логине,
 * задание требует более агрессивной политики — менять ID сессии при каждом обращении к защищенному ресурсу.
 *
 * Этот фильтр будет перехватывать запросы, проверять наличие сессии и принудительно менять её ID.
 */
@Slf4j
@Component
public class SessionRotationFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpSession session = httpRequest.getSession(false);

        if (session != null) {
            String oldId = session.getId();
            // Магия Сервлет-контейнера: меняет ID, сохраняя все атрибуты (токены)
            String newId = httpRequest.changeSessionId();
            log.info("🌀 Ротация сессии: {} -> {}", oldId, newId);
        }

        chain.doFilter(request, response);
    }
}