package ru.andreevcode.bionicpro.reports.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.andreevcode.bionicpro.reports.dto.ReportResponse;
import ru.andreevcode.bionicpro.reports.dto.ReportFileUrlResponse;
import ru.andreevcode.bionicpro.reports.exception.ReportPeriodNotReadyException;
import ru.andreevcode.bionicpro.reports.repository.ReportRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceV2 {

    private final ReportRepository reportRepository;
    private final ReportFileLinkGenerator linkGenerator;
    private final MinioService minioService;
    private final ObjectMapper objectMapper;

    @Value("${cdn.base-url}")
    private String cdnBaseUrl;

    public ReportFileUrlResponse getReportUrl(String userId, LocalDate startDate, LocalDate endDate, boolean stream) {
        // 1. Проверяем доступность периода отчёта (обработан ли диапазон пайплайном)
        LocalDate globalMaxDate = reportRepository.findGlobalMaxReportDate(stream);
        if (globalMaxDate == null || endDate.isAfter(globalMaxDate)) {
            throw new ReportPeriodNotReadyException("Данные за запрошенный период еще не полностью обработаны. Доступно по: " + globalMaxDate);
        }

        // 2. Узнаем дату последнего обновления (Cache Busting)
        // Если данных нет, явно используем 1970 год, чтобы сгенерировать ссылку на пустой файл
        // потом можно приготовить 1 пустой файл, чтобы не генерить кучу пустых файлов для каждого юзера
        LocalDateTime maxUpdatedAt = reportRepository.findMaxUpdatedAt(userId, startDate, endDate, stream)
                .orElse(LocalDateTime.of(1970, 1, 1, 0, 0));

        // 3. Генерируем уникальный ключ файла по уникальным параметрам (userId, startDate, endDate) + соль +
        // maxUpdatedAt. Добавляем префикс, чтобы ключи кэша стрима и батча не пересекались!
        String objectKey = linkGenerator.generateObjectKey(userId, startDate, endDate, maxUpdatedAt, stream);

        // 4. Проверяем, есть ли уже такой файл в MinIO
        if (!minioService.isObjectExists(objectKey)) {
            log.info("Cache miss for {}. Generating new report...", objectKey);

            // Если нет — вытягиваем данные из БД (используем нужную витрину)
            List<ReportResponse> reports = reportRepository.findReports(userId, startDate, endDate, stream);

            try {
                // Превращаем в JSON
                String jsonBody = objectMapper.writeValueAsString(reports);
                // Грузим в minio через S3 API
                minioService.uploadJson(objectKey, jsonBody);
            } catch (Exception e) {
                throw new RuntimeException("Error serializing report data", e);
            }
        } else {
            log.info("Cache hit for {}. Returning existing link.", objectKey);
        }

        // 4. Возвращаем ссылку на Nginx CDN
        String fullUrl = cdnBaseUrl + "/" + objectKey;
        return new ReportFileUrlResponse(fullUrl);
    }
}