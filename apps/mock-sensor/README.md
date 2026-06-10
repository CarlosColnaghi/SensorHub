# Mock Sensor

Python script that generates simulated SensorHub measurements and publishes them to MQTT.

## Run locally

```bash
PYTHONPATH=src python -m sensorhub_mock_sensor.main
```

## Environment

- `SENSORHUB_MQTT_HOST`: MQTT broker host. Default: `mqtt`.
- `SENSORHUB_MQTT_PORT`: MQTT broker port. Default: `1883`.
- `SENSORHUB_MQTT_TOPIC`: telemetry topic. Default: `sensorhub/measurements`.
- `SENSORHUB_MQTT_CLIENT_ID`: MQTT client id. Default: `sensorhub-mock-sensor`.
- `SENSORHUB_MQTT_QOS`: MQTT publish QoS. Default: `0`.
- `SENSORHUB_HARDWARE_UUIDS`: comma-separated hardware UUIDs. Default: `b0fee3a6-ae91-4265-9365-36f793f32f06`.
- `SENSORHUB_MEASUREMENT_INTERVAL_SECONDS`: generation interval. Default: `5`.
- `SENSORHUB_TEMPERATURE_MIN`: minimum temperature. Default: `18.0`.
- `SENSORHUB_TEMPERATURE_MAX`: maximum temperature. Default: `32.0`.
- `SENSORHUB_TEMPERATURE_STEP_MAX`: max temperature variation per cycle. Default: `0.4`.
- `SENSORHUB_HUMIDITY_MIN`: minimum humidity. Default: `35.0`.
- `SENSORHUB_HUMIDITY_MAX`: maximum humidity. Default: `80.0`.
- `SENSORHUB_HUMIDITY_STEP_MAX`: max humidity variation per cycle. Default: `1.5`.
