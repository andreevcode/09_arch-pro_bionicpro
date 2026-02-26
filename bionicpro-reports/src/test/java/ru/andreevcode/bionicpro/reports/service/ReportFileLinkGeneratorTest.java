package ru.andreevcode.bionicpro.reports.service;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ReportFileLinkGeneratorTest {

    private final String TEST_SALT = "super_secret_test_salt";
    private final ReportFileLinkGenerator generator = new ReportFileLinkGenerator(TEST_SALT);

    @Test
    void testSameInputProducesSameHash() {
        String userId = "user-123";
        LocalDate start = LocalDate.of(2026, 2, 20);
        LocalDate end = LocalDate.of(2026, 2, 22);
        LocalDateTime updated = LocalDateTime.of(2026, 2, 23, 10, 0);

        String key1 = generator.generateObjectKey(userId, start, end, updated);
        String key2 = generator.generateObjectKey(userId, start, end, updated);

        assertEquals(key1, key2, "Хеши должны совпадать для идентичных данных");
        assertTrue(key1.endsWith(".json"));
        assertEquals(37, key1.length(), "Длина должна быть 32 символа хеша + 5 символов '.json'");
    }

    @Test
    void testDifferentUpdatedAtProducesDifferentHash() {
        String userId = "user-123";
        LocalDate start = LocalDate.of(2026, 2, 20);
        LocalDate end = LocalDate.of(2026, 2, 22);

        // Старая версия отчета
        LocalDateTime updatedOld = LocalDateTime.of(2026, 2, 23, 10, 0);
        // Airflow пересчитал данные через час
        LocalDateTime updatedNew = LocalDateTime.of(2026, 2, 23, 11, 0);

        String keyOld = generator.generateObjectKey(userId, start, end, updatedOld);
        String keyNew = generator.generateObjectKey(userId, start, end, updatedNew);

        assertNotEquals(keyOld, keyNew, "При изменении updated_at ссылка должна полностью поменяться (Cache Busting)");
    }

    @Test
    void testDifferentUserProducesDifferentHash() {
        LocalDate start = LocalDate.of(2026, 2, 20);
        LocalDate end = LocalDate.of(2026, 2, 22);
        LocalDateTime updated = LocalDateTime.of(2026, 2, 23, 10, 0);

        String keyUser1 = generator.generateObjectKey("user-123", start, end, updated);
        String keyUser2 = generator.generateObjectKey("user-999", start, end, updated);

        assertNotEquals(keyUser1, keyUser2, "Для разных пользователей должны генерироваться разные ссылки");
    }
}