package ru.andreevcode.security.bionicproauth.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserController {

    @GetMapping("/user/me")
    public Map<String, Object> getUserInfo(@AuthenticationPrincipal OAuth2User principal) {
        // Если пользователь не авторизован, Spring Security сам вернет 401
        // благодаря настройке .anyRequest().authenticated()

        // Возвращаем только публичные данные для фронтенда
        return Map.of(
                "name", principal.getAttribute("name"),
                "email", principal.getAttribute("email"),
                "username", principal.getAttribute("preferred_username")
        );
    }
}