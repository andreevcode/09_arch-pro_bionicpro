package ru.andreevcode.bionicpro.reports.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.andreevcode.bionicpro.reports.dto.ReportResponse;
import ru.andreevcode.bionicpro.reports.dto.ReportFileUrlResponse;
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
    private final ObjectMapper objectMapper; // Для конвертации List<ReportResponse> в JSON

    @Value("${cdn.base-url}")
    private String cdnBaseUrl;

    public ReportFileUrlResponse getReportUrl(String userId, LocalDate startDate, LocalDate endDate) {
        // 1. Узнаем дату последнего обновления (Cache Busting)
        LocalDateTime maxUpdatedAt = reportRepository.findMaxUpdatedAt(userId, startDate, endDate)
                .orElseThrow(() -> new RuntimeException("No data found for the requested period"));

        // 2. Генерируем уникальный ключ файла по уникальным параметрам (userId, startDate, endDate) + соль +
        // maxUpdatedAt (добавка для смены ключа, чтобы инвалидировать файлы кэша в CDN в случае пересчета отчета в OLAP)
        String objectKey = linkGenerator.generateObjectKey(userId, startDate, endDate, maxUpdatedAt);

        // 3. Проверяем, есть ли уже такой файл в MinIO
        if (!minioService.isObjectExists(objectKey)) {
            log.info("Cache miss for {}. Generating new report...", objectKey);

            // Если нет — вытягиваем данные из БД (используем старый метод репозитория)
            List<ReportResponse> reports = reportRepository.findReports(userId, startDate, endDate);

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
