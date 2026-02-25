package ru.andreevcode.bionicpro.reports.repository;

import ru.andreevcode.bionicpro.reports.dto.ReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReportRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<ReportResponse> findReports(String userId, LocalDate start, LocalDate end) {
        String sql = """
            SELECT report_date, total_actions, avg_response_ms, 
                   max_noise_level, avg_battery_drain, critical_errors
            FROM daily_user_reports
            WHERE user_id = ? AND report_date BETWEEN ? AND ?
            ORDER BY report_date ASC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new ReportResponse(
                        rs.getObject("report_date", LocalDate.class), // JDBC 4.2+ напрямую мапит в LocalDate
                        rs.getInt("total_actions"),
                        rs.getFloat("avg_response_ms"),
                        rs.getFloat("max_noise_level"),
                        rs.getInt("avg_battery_drain"),
                        rs.getInt("critical_errors")
                ),
                userId, start, end);
    }
}