package ru.andreevcode.bionicpro.reports.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class MinioService {

    private final MinioClient minioClient;
    private final String bucketName;

    public MinioService(
            @Value("${minio.endpoint}") String endpoint,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey,
            @Value("${minio.bucket}") String bucketName) {

        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucketName = bucketName;
    }

    // легкий HTTP HEAD запрос, чтобы узнать, есть ли файл
    public boolean isObjectExists(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new RuntimeException("Error checking object in MinIO", e);
        } catch (Exception e) {
            throw new RuntimeException("MinIO communication error", e);
        }
    }

    // Загрузка JSON в бакет
    public void uploadJson(String objectKey, String jsonContent) {
        try {
            byte[] bytes = jsonContent.getBytes(StandardCharsets.UTF_8);
            try (InputStream is = new ByteArrayInputStream(bytes)) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectKey)
                        .stream(is, bytes.length, -1)
                        .contentType("application/json")
                        .build());
                log.info("Report {} successfully uploaded to MinIO bucket {}", objectKey, bucketName);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload report to MinIO", e);
        }
    }
}
