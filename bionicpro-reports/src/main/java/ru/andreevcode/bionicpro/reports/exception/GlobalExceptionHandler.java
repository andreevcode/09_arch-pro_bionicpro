package ru.andreevcode.bionicpro.reports.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Ловим ошибки от @PreAuthorize
    @ExceptionHandler(ReportAccessDeniedException.class)
    public ResponseEntity<String> handleReportAccess(ReportAccessDeniedException e) {
        log.error("Security violation: User {} attempted to view reports for user {}",
                e.getTokenUserId(), e.getRequestedUserId());

        return ResponseEntity.status(403).body("Access denied: You can only view your own reports");
    }

    // Ловим ошибки валидации (@NotBlank, @DateTimeFormat)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidation(MethodArgumentNotValidException e) {
        log.warn("Validation failed: {}", e.getBindingResult().getAllErrors());
        return ResponseEntity.status(400).body("Invalid request parameters");
    }

    // Ловим ошибки неготовности витрины
    @ExceptionHandler(ReportPeriodNotReadyException.class)
    public ResponseEntity<String> handlePeriodNotReady(ReportPeriodNotReadyException e) {
        log.warn("Report data not ready: {}", e.getMessage());
        return ResponseEntity.status(400).body(e.getMessage());
    }
}
