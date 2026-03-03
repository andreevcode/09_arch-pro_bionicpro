package ru.andreevcode.bionicpro.reports.api;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.andreevcode.bionicpro.reports.dto.ReportFileUrlResponse;
import ru.andreevcode.bionicpro.reports.exception.ReportAccessDeniedException;
import ru.andreevcode.bionicpro.reports.service.ReportServiceV2;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v2/reports")
@RequiredArgsConstructor
@Validated
@Slf4j
public class ReportControllerV2 {

    private final ReportServiceV2 reportServiceV2;

    @GetMapping
    @PreAuthorize("hasRole('prothetic_user')")
    public ResponseEntity<ReportFileUrlResponse> getReportUrl(
            @RequestParam @NotBlank String user_id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start_date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end_date,
            @RequestParam(defaultValue = "false") boolean stream,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String tokenUserId = jwt.getClaim("preferred_username");

        if (!tokenUserId.equals(user_id)) {
            throw new ReportAccessDeniedException(tokenUserId, user_id);
        }

        log.info("V2: Requesting report URL for user={} between {} and {}, stream={}", tokenUserId, start_date, end_date, stream);
        ReportFileUrlResponse response = reportServiceV2.getReportUrl(user_id, start_date, end_date, stream);

        return ResponseEntity.ok(response);
    }
}