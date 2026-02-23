package ru.andreevcode.security.bionicproauth.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import ru.andreevcode.security.bionicproauth.filter.CsrfCookieFilter;
import ru.andreevcode.security.bionicproauth.filter.SessionRotationFilter;
import ru.andreevcode.security.bionicproauth.filter.TokenLoggingFilter;

import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final SessionRotationFilter sessionRotationFilter;
    private final TokenLoggingFilter tokenLoggingFilter;
    private final CsrfCookieFilter csrfCookieFilter;

    public SecurityConfig(SessionRotationFilter sessionRotationFilter,
                          TokenLoggingFilter tokenLoggingFilter,
                          CsrfCookieFilter csrfCookieFilter) {
        this.sessionRotationFilter = sessionRotationFilter;
        this.tokenLoggingFilter = tokenLoggingFilter;
        this.csrfCookieFilter = csrfCookieFilter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Разрешаем конкретно наш фронтенд
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type"));
        // КРИТИЧЕСКИ ВАЖНО для передачи JSESSIONID
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                // 1. ВКЛЮЧАЕМ CORS (берет настройки из бина выше)
                .cors(Customizer.withDefaults())

                // 2.1 Смена sessionId на каждом запросе
                .addFilterAfter(sessionRotationFilter, SecurityContextHolderFilter.class)

                // 2.2 Логгер токенов
                // в порядке их добавления: сначала ротация, потом логгер.
                .addFilterAfter(tokenLoggingFilter, SecurityContextHolderFilter.class)

                // 3. Все запросы к /api/** должны быть авторизованы
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login/**", "/oauth2/**").permitAll()
                        .anyRequest().authenticated()
                )

                // 4. ГОВОРИМ "НЕТ" РЕДИРЕКТАМ ДЛЯ API
                // Если запрос на /api/** не авторизован, возвращаем 401 Unauthorized вместо 302 Redirect
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getServletPath().startsWith("/api/")
                        )
                )

                // 5. Включаем OAuth2 Login (Code Flow)
                .oauth2Login(oauth2 -> oauth2
                        // После успешного входа отправляем на главную фронта
                        // "localhost", а не "frontend" - потому что переход делает браузер
                        .defaultSuccessUrl("http://localhost:3000/", true)
                )
                // 6. Управление сессиями (Пункт 12 задания: Session Fixation)
                .sessionManagement(session -> session
                        // По умолчанию Spring меняет ID сессии при логине (migrateSession)
                        // Это защищает от session fixation attack
                        .sessionFixation().migrateSession()
                )
                // 7. Настройка CSRF
                // Поскольку у нас SPA + Cookies, нам нужен CookieCsrfTokenRepository
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfRequestHandler())
                        .ignoringRequestMatchers("/logout")
                )
                // ДОБАВЛЯЕМ ФИЛЬТР, который "будит" CSRF-токен
                .addFilterAfter(csrfCookieFilter, BasicAuthenticationFilter.class);

        return http.build();
    }

    // Бин для автоматического рефреша токенов
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository authorizedClientRepository) {

        OAuth2AuthorizedClientProvider authorizedClientProvider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .authorizationCode()
                        .refreshToken()
                        .build();

        DefaultOAuth2AuthorizedClientManager authorizedClientManager =
                new DefaultOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientRepository);

        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        // 1. Подключаем вынесенный обработчик УСПЕХА (для логирования)
        authorizedClientManager.setAuthorizationSuccessHandler(
                oAuth2AuthorizationSuccessHandler(authorizedClientRepository));

        // 2. Подключаем вынесенный обработчик ОШИБКИ (для фикса рассинхрона сессий через убийство сессии)
        authorizedClientManager.setAuthorizationFailureHandler(
                oAuth2AuthorizationFailureHandler(authorizedClientRepository));

        return authorizedClientManager;
    }

    // ==================================================================================
    // ПРИВАТНЫЕ МЕТОДЫ-ОБРАБОТЧИКИ
    // ==================================================================================

    // Обработчик, который отключает XOR-защиту (упрощает жизнь SPA)
    // Это заставляет Spring использовать имя заголовка по умолчанию (X-XSRF-TOKEN)
    private CsrfTokenRequestAttributeHandler csrfRequestHandler() {
        var csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName(null);
        return csrfRequestHandler;
    }

    // Обработчик УСПЕШНОГО обновления токена
    private OAuth2AuthorizationSuccessHandler oAuth2AuthorizationSuccessHandler(
            OAuth2AuthorizedClientRepository authorizedClientRepository) {

        return (authorizedClient, principal, attributes) -> {
            HttpServletRequest request = (HttpServletRequest) attributes.get(HttpServletRequest.class.getName());
            HttpServletResponse response = (HttpServletResponse) attributes.get(HttpServletResponse.class.getName());

            String sessionId = request != null ? request.getRequestedSessionId() : "N/A";
            String user = principal.getName();

            var at = authorizedClient.getAccessToken();
            var rt = authorizedClient.getRefreshToken();

            String rtExpires = (rt != null && rt.getExpiresAt() != null) ? rt.getExpiresAt().toString() : "Not mapped by Spring";

            String atValue = at.getTokenValue();
            String atStart = atValue.substring(0, 15);
            String atEnd = atValue.substring(atValue.length() - 15);

            String rtValue = rt != null ? rt.getTokenValue() : null;
            String rtStart = rtValue == null ? "" : rtValue.substring(0, 15);
            String rtEnd = rtValue == null ? "" : rtValue.substring(rtValue.length() - 15);

            log.info("=== AUTO-REFRESH SUCCESS ===");
            log.info("User: {}", user);
            log.info("JSESSIONID: {}", sessionId);
            log.info("New AT: {}...{} | Expires: {}", atStart, atEnd, at.getExpiresAt());

            if (rt != null) {
                log.info("New RT: {}...{} | Expires: {}", rtStart, rtEnd, rtExpires);
            }

            // Обязательно сохраняем обратно в сессию!
            authorizedClientRepository.saveAuthorizedClient(authorizedClient, principal, request, response);
        };
    }

    // Обработчик ОШИБКИ обновления токена (Убиваем сессию)
    private OAuth2AuthorizationFailureHandler oAuth2AuthorizationFailureHandler(
            OAuth2AuthorizedClientRepository authorizedClientRepository) {

        // Сохраняем дефолтную логику удаления битого клиента из репозитория
        RemoveAuthorizedClientOAuth2AuthorizationFailureHandler defaultFailureHandler =
                new RemoveAuthorizedClientOAuth2AuthorizationFailureHandler(
                        (clientRegistrationId, principal, attributes) -> {
                            HttpServletRequest request = (HttpServletRequest) attributes.get(HttpServletRequest.class.getName());
                            HttpServletResponse response = (HttpServletResponse) attributes.get(HttpServletResponse.class.getName());
                            authorizedClientRepository.removeAuthorizedClient(clientRegistrationId, principal, request, response);
                        });

        return (authorizationException, principal, attributes) -> {
            log.error("💥 Ошибка рефреша токена: {}. Убиваем локальную сессию.", authorizationException.getMessage());

            HttpServletRequest request = (HttpServletRequest) attributes.get(HttpServletRequest.class.getName());
            if (request != null) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    // Жестко уничтожаем JSESSIONID, чтобы при следующем запросе юзер стал анонимом
                    session.invalidate();
                }
            }

            // Очищаем контекст безопасности текущего потока
            SecurityContextHolder.clearContext();

            // Вызываем дефолтную очистку (удаление старых токенов)
            defaultFailureHandler.onAuthorizationFailure(authorizationException, principal, attributes);
        };
    }
}