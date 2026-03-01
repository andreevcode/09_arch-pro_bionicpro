-- 1. Staging таблицы v2 для потоковой передачи
create table bionicpro_olap.staging_devices_v2(
      device_id        String,
      user_id          String,
      model_name       String,
      firmware_version String
) engine = ReplacingMergeTree ORDER BY device_id;

create table bionicpro_olap.staging_users_v2
(
    id         String,
    full_name  String,
    email      String,
    created_at DateTime64(6)
) engine = ReplacingMergeTree ORDER BY id;


create table bionicpro_olap.staging_telemetry_v2
(
    event_id         UInt64,
    device_id        String,
    event_ts         DateTime64(6),
    action_type      String,
    response_time_ms Int32,
    myo_noise_level  Float32,
    battery_level    Int32,
    has_error        UInt8
) engine = ReplacingMergeTree ORDER BY (toDate(event_ts), device_id, event_id)


-- 2. Розетки для подключения к Kafka
CREATE TABLE bionicpro_olap.kafka_users_stream (
   id         String,
   full_name  String,
   email      String,
   created_at Int64
) ENGINE = Kafka
  SETTINGS
      kafka_broker_list = 'kafka:9092',
      kafka_topic_list = 'bionicpro_oltp.crm.users',
      kafka_group_name = 'ch_users_group',
      kafka_format = 'JSONEachRow';

CREATE TABLE bionicpro_olap.kafka_devices_stream (
     device_id        String,
     user_id          String,
     model_name       String,
     firmware_version String
) ENGINE = Kafka
  SETTINGS
      kafka_broker_list = 'kafka:9092',
      kafka_topic_list = 'bionicpro_oltp.crm.devices',
      kafka_group_name = 'ch_devices_group',
      kafka_format = 'JSONEachRow';

CREATE TABLE bionicpro_olap.kafka_telemetry_stream (
       event_id         UInt64,
       device_id        String,
       event_ts         Int64,
       action_type      String,
       response_time_ms Int32,
       myo_noise_level  Float32,
       battery_level    Int32,
       has_error        UInt8
) ENGINE = Kafka
  SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'bionicpro_oltp.telemetry.events',
    kafka_group_name = 'ch_telemetry_group',
    kafka_format = 'JSONEachRow';

-- После проверки Kafka
-- 3. Насосы (MV) с накаткой staging_v2 CRM таблиц users и devices
CREATE MATERIALIZED VIEW bionicpro_olap.mv_users
TO bionicpro_olap.staging_users_v2 AS
SELECT
    id,
    full_name,
    email,
    toDateTime64(created_at / 1000000.0, 6) AS created_at -- Конвертируем микросекунды в нормальный DateTime64(6)
FROM bionicpro_olap.kafka_users_stream;

CREATE MATERIALIZED VIEW bionicpro_olap.mv_devices
TO bionicpro_olap.staging_devices_v2 AS
SELECT * FROM bionicpro_olap.kafka_devices_stream;

-- 4. Новая потоковая витрина
CREATE TABLE IF NOT EXISTS bionicpro_olap.daily_user_reports_v2 (
    report_date Date,
    user_id String,
    user_full_name String,
    device_id String,
    device_model_name String,
    total_actions AggregateFunction(count, UInt32),
    avg_response_ms AggregateFunction(avg, Int32),
    max_noise_level AggregateFunction(max, Float32),
    avg_battery_drain AggregateFunction(avg, Int32),
    critical_errors AggregateFunction(sum, UInt8),
    updated_at SimpleAggregateFunction(max, DateTime)
    ) ENGINE = AggregatingMergeTree()
ORDER BY (user_id, device_id, report_date, user_full_name, device_model_name);

-- Надо выполнять одновременно, иначе в одну из таблиц могут не прихеать данные
-- 5. Насосы (mv) из в новые стейджинг таблицу телеметрии и потоковую витрину
CREATE MATERIALIZED VIEW IF NOT EXISTS bionicpro_olap.mv_telemetry_staging
TO bionicpro_olap.staging_telemetry_v2 AS
SELECT
    event_id,
    device_id,
    toDateTime64(event_ts / 1000000.0, 6) AS event_ts,
    action_type,
    response_time_ms,
    myo_noise_level,
    battery_level,
    has_error
FROM bionicpro_olap.kafka_telemetry_stream;
--
CREATE MATERIALIZED VIEW IF NOT EXISTS bionicpro_olap.mv_daily_user_reports
TO bionicpro_olap.daily_user_reports_v2 AS
SELECT
    toDate(toDateTime64(t.event_ts / 1000000.0, 6)) AS report_date,
    u.id AS user_id,
    u.full_name AS user_full_name,
    t.device_id AS device_id,
    d.model_name AS device_model_name,
    -- Функции с суффиксом State подготавливают данные для AggregatingMergeTree
    countState(toUInt32(1)) AS total_actions,
    avgState(t.response_time_ms) AS avg_response_ms,
    maxState(t.myo_noise_level) AS max_noise_level,
    avgState(t.battery_level) AS avg_battery_drain,
    sumState(t.has_error) AS critical_errors,
    now() AS updated_at
FROM bionicpro_olap.kafka_telemetry_stream AS t
-- Джойним сырой поток с надежно сохраненными справочниками
LEFT JOIN bionicpro_olap.staging_devices_v2 AS d ON t.device_id = d.device_id
LEFT JOIN bionicpro_olap.staging_users_v2 AS u ON d.user_id = u.id
GROUP BY report_date, user_id, device_id, full_name, model_name;

