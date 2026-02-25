import json
import requests
from datetime import datetime
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.providers.postgres.hooks.postgres import PostgresHook
from airflow.hooks.base import BaseHook

def get_ch_connection():
    conn = BaseHook.get_connection('clickhouse_http_conn')
    ch_url = f"http://{conn.host}:{conn.port}/"
    auth = (conn.login, conn.password)
    return ch_url, auth

def create_staging_tables():
    ch_url, auth = get_ch_connection()

    queries = [
        "CREATE DATABASE IF NOT EXISTS bionicpro_olap",

        """CREATE TABLE IF NOT EXISTS bionicpro_olap.staging_users (
               id String, full_name String, email String, created_at DateTime64(6)
            ) ENGINE = ReplacingMergeTree() ORDER BY id""",

        """CREATE TABLE IF NOT EXISTS bionicpro_olap.staging_devices (
                device_id String, user_id String, model_name String, firmware_version String
           ) ENGINE = ReplacingMergeTree() ORDER BY device_id""",

        """CREATE TABLE IF NOT EXISTS bionicpro_olap.staging_telemetry (
                event_id UInt64, 
                device_id String, 
                event_ts DateTime64(6),
                action_type String, 
                response_time_ms Int32, 
                myo_noise_level Float32,
                battery_level Int32, 
                has_error UInt8
            ) ENGINE = ReplacingMergeTree() ORDER BY (toDate(event_ts), device_id, event_id)"""
    ]
    for query in queries:
        requests.post(ch_url, data=query, auth=auth)

def fetch_and_load(base_pg_sql, ch_table, is_dict=False):
    pg_hook = PostgresHook(postgres_conn_id='postgres_oltp_conn')
    ch_url, auth = get_ch_connection()

    if is_dict:
        requests.post(ch_url, data=f"TRUNCATE TABLE bionicpro_olap.{ch_table}", auth=auth)
        final_pg_sql = base_pg_sql
    else:
        # Узнаем MAX(event_ts)
        resp = requests.post(ch_url, data=f"SELECT MAX(event_ts) FROM bionicpro_olap.{ch_table}", auth=auth)
        max_ts = resp.text.strip()

        if not max_ts or max_ts == '\\N' or max_ts.startswith('0000'):
            max_ts = '1970-01-01 00:00:00'

        print(f"Последний event_ts в {ch_table}: {max_ts}. Тянем новые данные...")
        final_pg_sql = f"{base_pg_sql} WHERE event_ts > '{max_ts}'"

    records = pg_hook.get_records(final_pg_sql)

    if not records:
        print(f"Нет новых данных для {ch_table}.")
        return

    payload = ""
    for row in records:
        if ch_table == 'staging_users':
            data = {"id": row[0], "full_name": row[1], "email": row[2], "created_at": str(row[3])}
        elif ch_table == 'staging_devices':
            data = {"device_id": row[0], "user_id": row[1], "model_name": row[2], "firmware_version": row[3]}
        elif ch_table == 'staging_telemetry':
            data = {
                "event_id": row[0], "device_id": row[1], "event_ts": str(row[2]),
                "action_type": row[3], "response_time_ms": row[4],
                "myo_noise_level": float(row[5]), "battery_level": row[6], "has_error": int(row[7])
            }
        payload += json.dumps(data) + "\n"

    ch_params = {"query": f"INSERT INTO bionicpro_olap.{ch_table} FORMAT JSONEachRow"}
    response = requests.post(ch_url, params=ch_params, data=payload, auth=auth)

    if response.status_code != 200:
        raise Exception(f"Ошибка загрузки в {ch_table}: {response.text}")
    print(f"Загружено {len(records)} строк в {ch_table}")

with DAG(
        dag_id='1_bionic_ingestion',
        start_date=datetime(2026, 2, 20),
        schedule_interval='@daily',
        catchup=False,
        tags=['bionicpro']
) as dag:

    init_tables = PythonOperator(
        task_id='create_ch_tables',
        python_callable=create_staging_tables
    )

    load_users = PythonOperator(
        task_id='load_users', python_callable=fetch_and_load,
        op_kwargs={'base_pg_sql': "SELECT * FROM crm.users", 'ch_table': 'staging_users', 'is_dict': True}
    )

    load_devices = PythonOperator(
        task_id='load_devices', python_callable=fetch_and_load,
        op_kwargs={'base_pg_sql': "SELECT * FROM crm.devices", 'ch_table': 'staging_devices', 'is_dict': True}
    )

    load_telemetry = PythonOperator(
        task_id='load_telemetry_delta_high_watermark', python_callable=fetch_and_load,
        op_kwargs={'base_pg_sql': "SELECT * FROM telemetry.events", 'ch_table': 'staging_telemetry', 'is_dict': False}
    )

    init_tables >> [load_users, load_devices, load_telemetry]