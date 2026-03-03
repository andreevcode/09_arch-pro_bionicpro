CREATE SCHEMA IF NOT EXISTS crm;
CREATE SCHEMA IF NOT EXISTS telemetry;

-- Таблица пользователей (CRM)
CREATE TABLE crm.users (
   id VARCHAR(50) PRIMARY KEY, -- Сюда ляжет uid из LDAP (john.doe и т.д.)
   full_name VARCHAR(100),
   email VARCHAR(100),
   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Таблица устройств (CRM)
CREATE TABLE crm.devices (
     device_id VARCHAR(50) PRIMARY KEY,
     user_id VARCHAR(50) REFERENCES crm.users(id),
     model_name VARCHAR(50),
     firmware_version VARCHAR(20)
);

-- Таблица сырой телеметрии (Telemetry)
CREATE TABLE telemetry.events (
      event_id BIGSERIAL PRIMARY KEY,
      device_id VARCHAR(50) REFERENCES crm.devices(device_id),
      event_ts TIMESTAMP,
      action_type VARCHAR(20),
      response_time_ms INTEGER,
      myo_noise_level NUMERIC(5,2),
      battery_level INTEGER,
      has_error BOOLEAN
);