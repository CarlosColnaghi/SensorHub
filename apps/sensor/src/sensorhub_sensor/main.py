from __future__ import annotations

import json
import logging
import os
import random
import signal
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from typing import Callable, Protocol
from uuid import UUID


DEFAULT_HARDWARE_UUID = "b0fee3a6-ae91-4265-9365-36f793f32f06"
TEMPERATURE_UNIT = "CELSIUS"
HUMIDITY_UNIT = "RELATIVE_PERCENT"

LOGGER = logging.getLogger("sensorhub_sensor")


class ConfigError(ValueError):
    """Raised when environment configuration is invalid."""


@dataclass(frozen=True)
class MqttConfig:
    host: str
    port: int
    topic: str
    client_id: str
    qos: int


@dataclass(frozen=True)
class ValueRange:
    minimum: float
    maximum: float
    step_max: float

    def validate(self, label: str) -> None:
        if self.minimum > self.maximum:
            raise ConfigError(f"{label} minimum must be less than or equal to maximum")
        if self.step_max < 0:
            raise ConfigError(f"{label} step max must be greater than or equal to zero")


@dataclass(frozen=True)
class SensorConfig:
    mqtt: MqttConfig
    hardware_uuids: tuple[str, ...]
    interval_seconds: float
    temperature: ValueRange
    humidity: ValueRange


@dataclass(frozen=True)
class Measurement:
    temperature: float
    humidity: float
    measured_at: datetime


class Publisher(Protocol):
    def __enter__(self) -> Publisher:
        ...

    def __exit__(self, exc_type, exc, traceback) -> None:
        ...

    def publish(self, topic: str, payload: str, qos: int) -> None:
        ...


def read_config(environ: dict[str, str] | None = None) -> SensorConfig:
    values = environ if environ is not None else os.environ
    interval_seconds = _read_float(values, "SENSORHUB_MEASUREMENT_INTERVAL_SECONDS", 5.0)
    if interval_seconds <= 0:
        raise ConfigError("SENSORHUB_MEASUREMENT_INTERVAL_SECONDS must be greater than zero")

    mqtt_port = _read_int(values, "SENSORHUB_MQTT_PORT", 1883)
    if mqtt_port <= 0:
        raise ConfigError("SENSORHUB_MQTT_PORT must be greater than zero")

    mqtt_qos = _read_int(values, "SENSORHUB_MQTT_QOS", 0)
    if mqtt_qos not in (0, 1, 2):
        raise ConfigError("SENSORHUB_MQTT_QOS must be 0, 1, or 2")

    temperature = ValueRange(
        _read_float(values, "SENSORHUB_TEMPERATURE_MIN", 18.0),
        _read_float(values, "SENSORHUB_TEMPERATURE_MAX", 32.0),
        _read_float(values, "SENSORHUB_TEMPERATURE_STEP_MAX", 0.4),
    )
    humidity = ValueRange(
        _read_float(values, "SENSORHUB_HUMIDITY_MIN", 35.0),
        _read_float(values, "SENSORHUB_HUMIDITY_MAX", 80.0),
        _read_float(values, "SENSORHUB_HUMIDITY_STEP_MAX", 1.5),
    )
    temperature.validate("temperature")
    humidity.validate("humidity")

    return SensorConfig(
        mqtt=MqttConfig(
            host=values.get("SENSORHUB_MQTT_HOST", "mqtt"),
            port=mqtt_port,
            topic=values.get("SENSORHUB_MQTT_TOPIC", "sensorhub/measurements"),
            client_id=values.get("SENSORHUB_MQTT_CLIENT_ID", "sensorhub-sensor"),
            qos=mqtt_qos,
        ),
        hardware_uuids=parse_hardware_uuids(
            values.get("SENSORHUB_HARDWARE_UUIDS", DEFAULT_HARDWARE_UUID)
        ),
        interval_seconds=interval_seconds,
        temperature=temperature,
        humidity=humidity,
    )


def parse_hardware_uuids(raw_value: str) -> tuple[str, ...]:
    candidates = [item.strip() for item in raw_value.split(",") if item.strip()]
    if not candidates:
        raise ConfigError("SENSORHUB_HARDWARE_UUIDS must contain at least one UUID")

    parsed: list[str] = []
    seen: set[str] = set()
    for candidate in candidates:
        try:
            normalized = str(UUID(candidate))
        except ValueError as exc:
            raise ConfigError(f"invalid hardware UUID: {candidate}") from exc

        if normalized not in seen:
            parsed.append(normalized)
            seen.add(normalized)

    return tuple(parsed)


def connect_mqtt(config: MqttConfig) -> Publisher:
    import paho.mqtt.client as mqtt

    client = mqtt.Client(client_id=config.client_id)
    client.connect(config.host, config.port, keepalive=60)
    client.loop_start()
    return PahoMqttPublisher(client)


class PahoMqttPublisher:
    def __init__(self, client) -> None:
        self.client = client

    def __enter__(self) -> PahoMqttPublisher:
        return self

    def __exit__(self, exc_type, exc, traceback) -> None:
        self.client.loop_stop()
        self.client.disconnect()

    def publish(self, topic: str, payload: str, qos: int) -> None:
        result = self.client.publish(topic, payload=payload, qos=qos)
        result.wait_for_publish()
        if result.rc != 0:
            raise RuntimeError(f"MQTT publish failed with rc={result.rc}")


class MeasurementGenerator:
    def __init__(
        self,
        temperature_range: ValueRange,
        humidity_range: ValueRange,
        random_source: random.Random | None = None,
    ) -> None:
        self.temperature_range = temperature_range
        self.humidity_range = humidity_range
        self.random = random_source or random.Random()
        self._last_temperature_by_hardware_uuid: dict[str, float] = {}
        self._last_humidity_by_hardware_uuid: dict[str, float] = {}

    def next_measurement(self, hardware_uuid: str) -> Measurement:
        temperature = self._next_value(
            self.temperature_range,
            self._last_temperature_by_hardware_uuid.get(hardware_uuid),
        )
        humidity = self._next_value(
            self.humidity_range,
            self._last_humidity_by_hardware_uuid.get(hardware_uuid),
        )
        self._last_temperature_by_hardware_uuid[hardware_uuid] = temperature
        self._last_humidity_by_hardware_uuid[hardware_uuid] = humidity

        return Measurement(
            temperature=temperature,
            humidity=humidity,
            measured_at=datetime.now(timezone.utc),
        )

    def _next_value(self, value_range: ValueRange, previous: float | None) -> float:
        if previous is None:
            value = self.random.uniform(value_range.minimum, value_range.maximum)
        else:
            delta = self.random.uniform(-value_range.step_max, value_range.step_max)
            value = previous + delta

        value = min(max(value, value_range.minimum), value_range.maximum)
        return _round_two(value)


def build_payload(hardware_uuid: str, measurement: Measurement) -> str:
    return json.dumps(
        {
            "hardwareUuid": hardware_uuid,
            "temperature": measurement.temperature,
            "temperatureUnit": TEMPERATURE_UNIT,
            "humidity": measurement.humidity,
            "humidityUnit": HUMIDITY_UNIT,
            "measuredAt": _format_instant(measurement.measured_at),
        },
        separators=(",", ":"),
    )


class SensorRunner:
    def __init__(
        self,
        config: SensorConfig,
        publisher_factory: Callable[[MqttConfig], Publisher] = connect_mqtt,
        sleep: Callable[[float], None] = time.sleep,
    ) -> None:
        self.config = config
        self.publisher_factory = publisher_factory
        self.sleep = sleep
        self.generator = MeasurementGenerator(config.temperature, config.humidity)
        self.running = True

    def stop(self, signum=None, frame=None) -> None:
        self.running = False

    def run_forever(self) -> None:
        with self.publisher_factory(self.config.mqtt) as publisher:
            LOGGER.info(
                "publishing telemetry to mqtt://%s:%s topic=%s devices=%s",
                self.config.mqtt.host,
                self.config.mqtt.port,
                self.config.mqtt.topic,
                len(self.config.hardware_uuids),
            )
            while self.running:
                for hardware_uuid in self.config.hardware_uuids:
                    measurement = self.generator.next_measurement(hardware_uuid)
                    payload = build_payload(hardware_uuid, measurement)
                    try:
                        publisher.publish(
                            self.config.mqtt.topic,
                            payload,
                            self.config.mqtt.qos,
                        )
                        LOGGER.info(
                            "published measurement hardware_uuid=%s temperature=%.2f humidity=%.2f",
                            hardware_uuid,
                            measurement.temperature,
                            measurement.humidity,
                        )
                    except Exception:
                        LOGGER.exception(
                            "failed to publish measurement for hardware_uuid=%s",
                            hardware_uuid,
                        )

                if self.running:
                    self.sleep(self.config.interval_seconds)


def configure_logging() -> None:
    logging.basicConfig(
        level=os.environ.get("SENSORHUB_LOG_LEVEL", "INFO"),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )


def main() -> int:
    configure_logging()
    try:
        config = read_config()
        runner = SensorRunner(config)
        signal.signal(signal.SIGTERM, runner.stop)
        signal.signal(signal.SIGINT, runner.stop)
        runner.run_forever()
        return 0
    except ConfigError as exc:
        LOGGER.error("%s", exc)
        return 1
    except Exception:
        LOGGER.exception("sensor failed")
        return 1


def _read_float(values: dict[str, str], key: str, default: float) -> float:
    raw_value = values.get(key)
    if raw_value is None:
        return default
    try:
        return float(raw_value)
    except ValueError as exc:
        raise ConfigError(f"{key} must be a number") from exc


def _read_int(values: dict[str, str], key: str, default: int) -> int:
    raw_value = values.get(key)
    if raw_value is None:
        return default
    try:
        return int(raw_value)
    except ValueError as exc:
        raise ConfigError(f"{key} must be an integer") from exc


def _round_two(value: float) -> float:
    return float(Decimal(str(value)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))


def _format_instant(value: datetime) -> str:
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


if __name__ == "__main__":
    raise SystemExit(main())
