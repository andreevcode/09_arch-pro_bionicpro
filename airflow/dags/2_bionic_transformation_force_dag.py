import requests
from datetime import datetime
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.hooks.base import BaseHook

def get_ch_connection():
    conn = BaseHook.get_connection('clickhouse_http_conn')
    ch_url = f"http://{conn.host}:{conn.port}/"
    auth = (conn.login, conn.password)
    return ch_url, auth

def create_mart_table():
    ch_url, auth = get_ch_connection()

    query = """
            CREATE TABLE IF NOT EXISTS bionicpro_olap.daily_user_reports (
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
                ORDER BY (report_date, user_id); \
            """
    requests.post(ch_url, data=query, auth=auth)

def cleanup_mart_period(**kwargs):
    ch_url, auth = get_ch_connection()

    # Берем даты из конфигурации запуска или ставим дефолт для тестов
    conf = kwargs.get('dag_run').conf or {}
    start_date = conf.get('start_date', '2026-02-20')
    end_date = conf.get('end_date', '2026-02-22')

    print(f"Очистка данных в витрине за период с {start_date} по {end_date}...")

    # В ClickHouse DELETE — это мутация.
    # Мы удаляем старые записи за период, чтобы вставить новые с текущим updated_at.
    query = f"ALTER TABLE bionicpro_olap.daily_user_reports DELETE WHERE report_date BETWEEN '{start_date}' AND '{end_date}'"

    response = requests.post(ch_url, data=query, auth=auth)
    if response.status_code != 200:
        raise Exception(f"Ошибка при удалении периода: {response.text}")
    print("Команда на удаление отправлена (выполняется асинхронно).")

def run_clickhouse_transformation_force(**kwargs):
    ch_url, auth = get_ch_connection()

    conf = kwargs.get('dag_run').conf or {}
    start_date = conf.get('start_date', '2026-02-20')
    end_date = conf.get('end_date', '2026-02-22')

    print(f"Запущена принудительная вставка данных за период с {start_date} по {end_date}...")

    # Пересчитываем только указанный период
    build_mart_sql = f"""
        INSERT INTO bionicpro_olap.daily_user_reports
        SELECT 
            toDate(t.event_ts) as report_date,
            u.id as user_id,
            d.device_id as device_id,
            toUInt32(count()) as total_actions,
            toFloat32(avg(t.response_time_ms)) as avg_response_ms,
            toFloat32(max(t.myo_noise_level)) as max_noise_level,
            toUInt8(max(t.battery_level) - min(t.battery_level)) as avg_battery_drain,
            toUInt16(sum(t.has_error)) as critical_errors,
            now() as updated_at
        FROM bionicpro_olap.staging_telemetry t
        JOIN bionicpro_olap.staging_devices d ON t.device_id = d.device_id
        JOIN bionicpro_olap.staging_users u ON d.user_id = u.id
        WHERE toDate(t.event_ts) BETWEEN '{start_date}' AND '{end_date}'
        GROUP BY report_date, user_id, device_id;
    """

    response = requests.post(ch_url, data=build_mart_sql, auth=auth)
    if response.status_code != 200:
        raise Exception(f"Ошибка принудительной сборки витрины: {response.text}")

    print("Данные успешно обновлены!")

with DAG(
        dag_id='2_bionic_transformation_force',
        start_date=datetime(2026, 2, 20),
        schedule_interval=None,
        catchup=False,
        tags=['bionicpro', 'force']
) as dag:

    init_mart_task = PythonOperator(
        task_id='create_mart_table',
        python_callable=create_mart_table
    )

    cleanup_task = PythonOperator(
        task_id='cleanup_mart_period',
        python_callable=cleanup_mart_period
    )

    build_mart_task = PythonOperator(
        task_id='build_daily_mart_force',
        python_callable=run_clickhouse_transformation_force
    )

    init_mart_task >> cleanup_task >> build_mart_task