CREATE DATABASE IF NOT EXISTS bionicpro_olap;

USE bionicpro_olap;

CREATE TABLE IF NOT EXISTS daily_user_reports (
    report_date Date,
    user_id String,
    device_id String,
    total_actions UInt32,
    avg_response_ms Float32,
    max_noise_level Float32,
    avg_battery_drain UInt8,
    critical_errors UInt16,
    updated_at DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(updated_at)
ORDER BY (user_id, report_date);