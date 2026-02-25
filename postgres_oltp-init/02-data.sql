-- 1. Заполняем пользователей из LDAP
INSERT INTO crm.users (id, full_name, email) VALUES
             ('john.doe', 'John Doe', 'john@example.com'),
             ('jane.smith', 'Jane Smith', 'jane@example.com'),
             ('alex.johnson', 'Alex Johnson', 'alex@example.com');

-- 2. Раздаем им протезы
INSERT INTO crm.devices (device_id, user_id, model_name, firmware_version) VALUES
       ('DEV-001', 'john.doe', 'BionicHand Pro', 'v1.2.0'),
       ('DEV-002', 'jane.smith', 'BionicHand Lite', 'v1.1.5'),
       ('DEV-003', 'alex.johnson', 'BionicArm Max', 'v2.0.1');

-- 3. Генерируем телеметрию
DO $$
DECLARE
    dev RECORD;
    ts TIMESTAMP;
    is_err BOOLEAN;
    action_arr VARCHAR[] := ARRAY['GRIP', 'PINCH', 'RELAX'];
BEGIN
    -- Проходимся по каждому устройству
    FOR dev IN SELECT device_id FROM crm.devices LOOP
         -- Генерируем события каждые 5 минут с 20 по 22 февраля 2026 года
        FOR ts IN
            SELECT generate_series(timestamp '2026-02-20 08:00:00', timestamp '2026-02-22 22:00:00', interval '5 minutes') LOOP
            -- Имитация инцидента: 21 февраля с 14:00 до 15:00 что-то пошло не так (например, кривое обновление по воздуху)
            IF ts >= '2026-02-21 14:00:00' AND ts <= '2026-02-21 15:00:00' THEN
                is_err := random() < 0.85; -- 85% вероятность ошибки
            ELSE
                is_err := random() < 0.02; -- 2% фоновых ошибок в обычное время
            END IF;

            INSERT INTO telemetry.events (
                device_id, event_ts, action_type, response_time_ms, myo_noise_level, battery_level, has_error
            )
            VALUES (
               dev.device_id,
               ts,
               action_arr[floor(random() * 3 + 1)], -- Случайный жест
               floor(random() * 80 + 20),           -- Скорость реакции от 20 до 100 мс
               random() * 10,                       -- Шум от 0 до 10
               100 - floor(extract(epoch from (ts::time - '08:00:00'::time))/3600 * 5), -- Фейковый разряд батареи
               is_err
           );
        END LOOP;
    END LOOP;
END $$;