-- 1. Обновленный запрос к потовой витрине запрос для бэкенд сервиса API
SELECT report_date,
   user_id,
   user_full_name,
   device_id,
   device_model_name,
   countMerge(total_actions) AS total_actions,
   avgMerge(avg_response_ms) AS avg_response_ms,
   maxMerge(max_noise_level) AS max_noise_level,
   avgMerge(avg_battery_drain) AS avg_battery_drain,
   sumMerge(critical_errors) AS critical_errors,
   max(updated_at) AS updated_at FROM bionicpro_olap.daily_user_reports_v2
GROUP BY
    report_date,
    user_id,
    user_full_name,
    device_id,
    device_model_name;

-- 2. Технический запрос, чтобы посмотреть не смердженные микробатчи
SELECT
    report_date,
    user_id,
    device_id,
    -- Превращаем бинарный стейт в число для каждой строки отдельно
    finalizeAggregation(total_actions) AS current_total,
    finalizeAggregation(avg_response_ms) AS current_avg,
    -- Полезные системные колонки, чтобы увидеть "микробатчи"
    finalizeAggregation(avg_battery_drain) AS avg_battery_drain,
    finalizeAggregation(critical_errors) AS critical_errors,
    -- Получаем время последнего обновления агрегата
    updated_at AS updated_at,
    _part AS part_name,    -- Имя куска (файла) на диске
    a._part_index as part_index
FROM bionicpro_olap.daily_user_reports_v2 a
WHERE report_date = '2026-02-23';


SELECT
    report_date,
    user_id,
    user_full_name,
    device_id,
    device_model_name,
    countMerge(total_actions) AS total_actions,
    avgMerge(avg_response_ms) AS avg_response_ms,
    maxMerge(max_noise_level) AS max_noise_level,
    avgMerge(avg_battery_drain) AS avg_battery_drain,
    sumMerge(critical_errors) AS critical_errors,
    -- Получаем время последнего обновления агрегата
    max(updated_at) AS updated_at
FROM bionicpro_olap.daily_user_reports_v2
WHERE report_date = '2026-02-23'
-- WHERE user_id = 'alex.johnson' AND report_date BETWEEN '2026-02-22' AND '2026-02-24'
GROUP BY
    report_date,
    user_id,
    user_full_name,
    device_id,
    device_model_name
ORDER BY report_date desc, user_id, total_actions DESC;
select * FROM bionicpro_olap.daily_user_reports  ORDER BY report_date desc, user_id, total_actions DESC;