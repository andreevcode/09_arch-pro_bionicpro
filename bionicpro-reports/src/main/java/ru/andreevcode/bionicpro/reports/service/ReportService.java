package ru.andreevcode.bionicpro.reports.service;

import ru.andreevcode.bionicpro.reports.dto.ReportResponse;
import ru.andreevcode.bionicpro.reports.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final ReportRepository reportRepository;

    public List<ReportResponse> getReports(String userId, LocalDate start, LocalDate end) {
        log.info("Запрос отчета для пользователя {} в период с {} по {}", userId, start, end);

        // Здесь можно добавить дополнительную логику, например,
        // кэширование или проверку существования пользователя в системе

        return reportRepository.findReports(userId, start, end);
    }
}
