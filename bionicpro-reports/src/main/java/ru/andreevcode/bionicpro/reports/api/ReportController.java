package ru.andreevcode.bionicpro.reports.api;

import lombok.extern.slf4j.Slf4j;
import ru.andreevcode.bionicpro.reports.dto.ReportResponse;
import ru.andreevcode.bionicpro.reports.exception.ReportAccessDeniedException;
import ru.andreevcode.bionicpro.reports.service.ReportService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Validated
@Slf4j
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    @PreAuthorize("hasRole('prothetic_user')") // Только для пользователей с протезами
    public ResponseEntity<List<ReportResponse>> getUserReports(
            @RequestParam @NotBlank String user_id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start_date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end_date,
            @AuthenticationPrincipal Jwt jwt // Достаем токен текущего юзера
    ) {
        // Проверка: можно смотреть только СВОЙ отчет (sub в токене == user_id в запросе)
        String tokenUserId = jwt.getClaim("preferred_username"); // В Keycloak uid маппится сюда

        if (!tokenUserId.equals(user_id)) {
            throw new ReportAccessDeniedException(tokenUserId, user_id);
        }

        log.info("Send back  requested reports for user={} between {} and {}", tokenUserId, user_id, start_date,
                end_date);
        return ResponseEntity.ok(reportService.getReports(user_id, start_date, end_date));
    }
}