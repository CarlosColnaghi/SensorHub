# MQTT Ingestor

Java 25 worker that consumes SensorHub telemetry from MQTT and persists valid measurements to PostgreSQL.

## Stack

- Java 25
- Eclipse Paho MQTT client
- PostgreSQL JDBC
- Jackson

## Run locally

With PostgreSQL and MQTT available:

```text
mvn test
mvn package
java -jar target/mqtt-ingestor-0.0.1-SNAPSHOT.jar
```

The normal local path is Docker Compose from the repository root:

```text
docker compose up postgres mqtt api mqtt-ingestor mock-sensor
```

## Environment

- `SENSORHUB_MQTT_HOST`: MQTT broker host. Default: `mqtt`.
- `SENSORHUB_MQTT_PORT`: MQTT broker port. Default: `1883`.
- `SENSORHUB_MQTT_TOPIC`: telemetry topic. Default: `sensorhub/measurements`.
- `SENSORHUB_MQTT_CLIENT_ID`: MQTT client id. Default: `sensorhub-mqtt-ingestor`.
- `SENSORHUB_MQTT_QOS`: MQTT subscription QoS. Default: `0`.
- `SENSORHUB_DB_HOST`: PostgreSQL host. Default: `postgres`.
- `SENSORHUB_DB_PORT`: PostgreSQL port. Default: `5432`.
- `SENSORHUB_DB_NAME`: PostgreSQL database. Default: `sensorhub`.
- `SENSORHUB_DB_USER`: PostgreSQL user. Default: `sensorhub`.
- `SENSORHUB_DB_PASSWORD`: PostgreSQL password. Default: `sensorhub`.
- `SENSORHUB_DEVICE_CACHE_TTL_SECONDS`: device cache TTL. Default: `300`.
