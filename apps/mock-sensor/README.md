# Mock Sensor

Python script that generates simulated SensorHub measurements and writes them to PostgreSQL.

## Run locally

```bash
PYTHONPATH=src python -m sensorhub_mock_sensor.main
```

## Environment

- `SENSORHUB_DB_HOST`: PostgreSQL host. Default: `postgres`.
- `SENSORHUB_DB_PORT`: PostgreSQL port. Default: `5432`.
- `SENSORHUB_DB_NAME`: database name. Default: `sensorhub`.
- `SENSORHUB_DB_USER`: database user. Default: `sensorhub`.
- `SENSORHUB_DB_PASSWORD`: database password. Default: `sensorhub`.
- `SENSORHUB_HARDWARE_UUIDS`: comma-separated hardware UUIDs. Default: `b0fee3a6-ae91-4265-9365-36f793f32f06`.
- `SENSORHUB_MEASUREMENT_INTERVAL_SECONDS`: generation interval. Default: `5`.
- `SENSORHUB_TEMPERATURE_MIN`: minimum temperature. Default: `18.0`.
- `SENSORHUB_TEMPERATURE_MAX`: maximum temperature. Default: `32.0`.
- `SENSORHUB_TEMPERATURE_STEP_MAX`: max temperature variation per cycle. Default: `0.4`.
- `SENSORHUB_HUMIDITY_MIN`: minimum humidity. Default: `35.0`.
- `SENSORHUB_HUMIDITY_MAX`: maximum humidity. Default: `80.0`.
- `SENSORHUB_HUMIDITY_STEP_MAX`: max humidity variation per cycle. Default: `1.5`.

