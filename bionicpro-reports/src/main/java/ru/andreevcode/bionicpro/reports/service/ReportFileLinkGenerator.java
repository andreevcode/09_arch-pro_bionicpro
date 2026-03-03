package ru.andreevcode.bionicpro.reports.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class ReportFileLinkGenerator {
    private String salt;

    public ReportFileLinkGenerator(@Value("${app.reports.link-salt}") String salt) {
        this.salt = salt;
    }

    public String generateObjectKey(
            String userId,
            LocalDate startDate,
            LocalDate endDate,
            LocalDateTime maxUpdatedAt
            , boolean stream) {
        // Формируем базовую строку
        String rawParamsData = String.format("%s_%s_%s_%s_%s",
                userId, startDate, endDate, maxUpdatedAt.toString(), salt);

        // Хешируем параметры с salt через SHA-256
        String paramsHash = bytesToHex(hashSha256(rawParamsData)).substring(0, 32);

        // Итоговый ключ: {userId}/{startDate}_{endDate}/{type}_{paramsHash}.json
        return String.format("%s/%s_%s/%s_%s.json",
                userId,
                startDate.toString(),
                endDate.toString(),
                (stream ? "stream" : "batch"),
                paramsHash
        );
    }

    private byte[] hashSha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}