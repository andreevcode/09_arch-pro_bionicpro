package ru.andreevcode.bionicpro.reports.repository;

import ru.andreevcode.bionicpro.reports.dto.ReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReportRepository {

    private static final String SQL_FOR_BATCH_MARKT = """
            SELECT report_date, total_actions, avg_response_ms,
                   max_noise_level, avg_battery_drain, critical_errors,
                   updated_at
            FROM daily_user_reports
            WHERE user_id = ? AND report_date BETWEEN ? AND ?
            ORDER BY report_date ASC
            """;

    private static final String SQL_FOR_STREAM_MARKT = """
            SELECT report_date, 
                   countMerge(total_actions) AS total_actions, 
                   avgMerge(avg_response_ms) AS avg_response_ms, 
                   maxMerge(max_noise_level) AS max_noise_level, 
                   avgMerge(avg_battery_drain) AS avg_battery_drain,
                   sumMerge(critical_errors) AS critical_errors,
                   max(updated_at) AS updated_at
            FROM daily_user_reports_v2
            WHERE user_id = ? AND report_date BETWEEN ? AND ?
            GROUP BY report_date, user_id, device_id, user_full_name, device_model_name
            ORDER BY report_date ASC
            """;

    private final JdbcTemplate jdbcTemplate;

    public List<ReportResponse> findReports(String userId, LocalDate start, LocalDate end, boolean stream) {
        String sql = stream ? SQL_FOR_STREAM_MARKT : SQL_FOR_BATCH_MARKT;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new ReportResponse(
                        rs.getObject("report_date", LocalDate.class), // JDBC 4.2+ напрямую мапит в LocalDate
                        rs.getInt("total_actions"),
                        rs.getFloat("avg_response_ms"),
                        rs.getFloat("max_noise_level"),
                        rs.getInt("avg_battery_drain"),
                        rs.getInt("critical_errors"),
                        rs.getObject("updated_at", LocalDateTime.class)
                ),
                userId, start, end);
    }

    public Optional<LocalDateTime> findMaxUpdatedAt(String userId, LocalDate start, LocalDate end, boolean stream) {
        String marktName = stream ? "daily_user_reports_v2" : "daily_user_reports";
        String sql = String.format("""
            SELECT MAX(updated_at)
            FROM %s
            WHERE user_id = ? AND report_date BETWEEN ? AND ?
            """, marktName);

        LocalDateTime maxUpdated = jdbcTemplate.queryForObject(
                sql,
                LocalDateTime.class,
                userId, start, end
        );

        return Optional.ofNullable(maxUpdated);
    }
}