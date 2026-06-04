from __future__ import annotations

import logging
import os
import random
import signal
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from typing import Callable
from uuid import UUID


DEFAULT_HARDWARE_UUID = "b0fee3a6-ae91-4265-9365-36f793f32f06"
TEMPERATURE_UNIT = "CELSIUS"
HUMIDITY_UNIT = "RELATIVE_PERCENT"

LOGGER = logging.getLogger("sensorhub_mock_sensor")


class ConfigError(ValueError):
    """Raised when environment configuration is invalid."""


class DeviceResolutionError(RuntimeError):
    """Raised when a hardware UUID cannot be mapped to an active device."""


@dataclass(frozen=True)
class DbConfig:
    host: str
    port: int
    name: str
    user: str
    password: str


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
class MockSensorConfig:
    db: DbConfig
    hardware_uuids: tuple[str, ...]
    interval_seconds: float
    temperature: ValueRange
    humidity: ValueRange


@dataclass(frozen=True)
class Measurement:
    temperature: float
    humidity: float
    measured_at: datetime


def read_config(environ: dict[str, str] | None = None) -> MockSensorConfig:
    values = environ if environ is not None else os.environ
    interval_seconds = _read_float(values, "SENSORHUB_MEASUREMENT_INTERVAL_SECONDS", 5.0)
    if interval_seconds <= 0:
        raise ConfigError("SENSORHUB_MEASUREMENT_INTERVAL_SECONDS must be greater than zero")

    db_port = _read_int(values, "SENSORHUB_DB_PORT", 5432)
    if db_port <= 0:
        raise ConfigError("SENSORHUB_DB_PORT must be greater than zero")

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

    return MockSensorConfig(
        db=DbConfig(
            host=values.get("SENSORHUB_DB_HOST", "postgres"),
            port=db_port,
            name=values.get("SENSORHUB_DB_NAME", "sensorhub"),
            user=values.get("SENSORHUB_DB_USER", "sensorhub"),
            password=values.get("SENSORHUB_DB_PASSWORD", "sensorhub"),
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


def connect(db: DbConfig):
    import psycopg

    return psycopg.connect(
        host=db.host,
        port=db.port,
        dbname=db.name,
        user=db.user,
        password=db.password,
    )


class DeviceResolver:
    def __init__(self) -> None:
        self.cache: dict[str, str] = {}

    def resolve(self, connection, hardware_uuid: str) -> str | None:
        if hardware_uuid in self.cache:
            return self.cache[hardware_uuid]

        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT uuid, status
                FROM devices
                WHERE hardware_uuid = %s
                """,
                (hardware_uuid,),
            )
            row = cursor.fetchone()

        if row is None:
            raise DeviceResolutionError(f"hardware UUID {hardware_uuid} was not found")

        if row[1] != "ACTIVATED":
            LOGGER.warning(
                "hardware UUID %s has status %s and will not be simulated",
                hardware_uuid,
                row[1],
            )
            return None

        device_uuid = str(row[0])
        self.cache[hardware_uuid] = device_uuid
        return device_uuid

    def resolve_all(self, connection, hardware_uuids: tuple[str, ...]) -> dict[str, str]:
        for hardware_uuid in hardware_uuids:
            self.resolve(connection, hardware_uuid)
        if not self.cache:
            raise DeviceResolutionError("no ACTIVATED devices available for simulation")
        return dict(self.cache)


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
        self._last_temperature_by_device: dict[str, float] = {}
        self._last_humidity_by_device: dict[str, float] = {}

    def next_measurement(self, device_uuid: str) -> Measurement:
        temperature = self._next_value(
            self.temperature_range,
            self._last_temperature_by_device.get(device_uuid),
        )
        humidity = self._next_value(
            self.humidity_range,
            self._last_humidity_by_device.get(device_uuid),
        )
        self._last_temperature_by_device[device_uuid] = temperature
        self._last_humidity_by_device[device_uuid] = humidity

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


class MeasurementRepository:
    def insert(self, connection, device_uuid: str, measurement: Measurement) -> None:
        try:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    INSERT INTO measurements (
                        device_uuid,
                        temperature,
                        temperature_unit,
                        humidity,
                        humidity_unit,
                        measured_at,
                        received_at
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s)
                    """,
                    (
                        device_uuid,
                        measurement.temperature,
                        TEMPERATURE_UNIT,
                        measurement.humidity,
                        HUMIDITY_UNIT,
                        measurement.measured_at,
                        measurement.measured_at,
                    ),
                )
                cursor.execute(
                    """
                    UPDATE devices
                    SET last_seen_at = %s,
                        updated_at = now()
                    WHERE uuid = %s
                    """,
                    (measurement.measured_at, device_uuid),
                )
            connection.commit()
        except Exception:
            connection.rollback()
            raise


class MockSensorRunner:
    def __init__(
        self,
        config: MockSensorConfig,
        connection_factory: Callable[[DbConfig], object] = connect,
        sleep: Callable[[float], None] = time.sleep,
    ) -> None:
        self.config = config
        self.connection_factory = connection_factory
        self.sleep = sleep
        self.resolver = DeviceResolver()
        self.generator = MeasurementGenerator(config.temperature, config.humidity)
        self.repository = MeasurementRepository()
        self.running = True

    def stop(self, signum=None, frame=None) -> None:
        self.running = False

    def run_forever(self) -> None:
        with self.connection_factory(self.config.db) as connection:
            device_map = self.resolver.resolve_all(connection, self.config.hardware_uuids)
            LOGGER.info("resolved %s active device(s)", len(device_map))

            while self.running:
                for hardware_uuid, device_uuid in device_map.items():
                    measurement = self.generator.next_measurement(device_uuid)
                    try:
                        self.repository.insert(connection, device_uuid, measurement)
                        LOGGER.info(
                            "inserted measurement hardware_uuid=%s device_uuid=%s temperature=%.2f humidity=%.2f",
                            hardware_uuid,
                            device_uuid,
                            measurement.temperature,
                            measurement.humidity,
                        )
                    except Exception:
                        LOGGER.exception(
                            "failed to insert measurement for hardware_uuid=%s",
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
        runner = MockSensorRunner(config)
        signal.signal(signal.SIGTERM, runner.stop)
        signal.signal(signal.SIGINT, runner.stop)
        runner.run_forever()
        return 0
    except (ConfigError, DeviceResolutionError) as exc:
        LOGGER.error("%s", exc)
        return 1
    except Exception:
        LOGGER.exception("mock sensor failed")
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


if __name__ == "__main__":
    raise SystemExit(main())
