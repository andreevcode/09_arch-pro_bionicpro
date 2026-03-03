package ru.andreevcode.bionicpro.reports.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReportResponse(
        LocalDate reportDate,
        int totalActions,
        float avgResponseMs,
        float maxNoiseLevel,
        int batteryDrain,
        int errors,
        LocalDateTime updatedAt
) {
}
