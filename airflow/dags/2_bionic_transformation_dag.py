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

def run_clickhouse_transformation():
    ch_url, auth = get_ch_connection()

    get_max_date_sql = "SELECT MAX(report_date) FROM bionicpro_olap.daily_user_reports"
    resp = requests.post(ch_url, data=get_max_date_sql, auth=auth)

    if resp.status_code != 200:
        raise Exception(f"Ошибка получения MAX(report_date): {resp.text}")

    max_date = resp.text.strip()

    if not max_date or max_date == '\\N' or max_date.startswith('0000'):
        max_date = '1970-01-01'

    print(f"Последняя дата в витрине: {max_date}. Собираем данные новее...")

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
        WHERE toDate(t.event_ts) > '{max_date}'
        GROUP BY report_date, user_id, device_id;
    """

    response = requests.post(ch_url, data=build_mart_sql, auth=auth)
    if response.status_code != 200:
        raise Exception(f"Ошибка сборки витрины: {response.text}")

    print("Витрина успешно обновлена!")

with DAG(
        dag_id='2_bionic_transformation',
        start_date=datetime(2026, 2, 20),
        schedule_interval='@daily',
        catchup=False,
        tags=['bionicpro']
) as dag:

    init_mart_task = PythonOperator(
        task_id='create_mart_table',
        python_callable=create_mart_table
    )

    build_mart_task = PythonOperator(
        task_id='build_daily_mart_high_watermark',
        python_callable=run_clickhouse_transformation
    )

    init_mart_task >> build_mart_task