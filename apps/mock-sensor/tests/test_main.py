import random
import sys
import unittest
from pathlib import Path
from uuid import UUID


sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from sensorhub_mock_sensor.main import (  # noqa: E402
    DEFAULT_HARDWARE_UUID,
    ConfigError,
    DeviceResolutionError,
    DeviceResolver,
    Measurement,
    MeasurementGenerator,
    MeasurementRepository,
    ValueRange,
    parse_hardware_uuids,
    read_config,
)


class ConfigTest(unittest.TestCase):
    def test_reads_default_config(self):
        config = read_config({})

        self.assertEqual(config.db.host, "postgres")
        self.assertEqual(config.db.port, 5432)
        self.assertEqual(config.hardware_uuids, (DEFAULT_HARDWARE_UUID,))
        self.assertEqual(config.interval_seconds, 5.0)

    def test_parses_multiple_hardware_uuids_and_removes_duplicates(self):
        first = "b0fee3a6-ae91-4265-9365-36f793f32f06"
        second = "44dc9ffd-b376-4ceb-8f80-f7c7be550a4f"

        parsed = parse_hardware_uuids(f"{first}, {second}, {first}")

        self.assertEqual(parsed, (first, second))

    def test_rejects_invalid_hardware_uuid(self):
        with self.assertRaises(ConfigError):
            parse_hardware_uuids("not-a-uuid")

    def test_rejects_invalid_interval(self):
        with self.assertRaises(ConfigError):
            read_config({"SENSORHUB_MEASUREMENT_INTERVAL_SECONDS": "0"})

    def test_rejects_invalid_ranges(self):
        with self.assertRaises(ConfigError):
            read_config(
                {
                    "SENSORHUB_TEMPERATURE_MIN": "33.0",
                    "SENSORHUB_TEMPERATURE_MAX": "20.0",
                }
            )

    def test_rejects_negative_steps(self):
        with self.assertRaises(ConfigError):
            read_config({"SENSORHUB_HUMIDITY_STEP_MAX": "-0.1"})


class MeasurementGeneratorTest(unittest.TestCase):
    def test_generates_values_inside_ranges(self):
        generator = MeasurementGenerator(
            ValueRange(18.0, 32.0, 0.4),
            ValueRange(35.0, 80.0, 1.5),
            random.Random(1),
        )

        measurement = generator.next_measurement("device-1")

        self.assertGreaterEqual(measurement.temperature, 18.0)
        self.assertLessEqual(measurement.temperature, 32.0)
        self.assertGreaterEqual(measurement.humidity, 35.0)
        self.assertLessEqual(measurement.humidity, 80.0)

    def test_consecutive_values_respect_step_max(self):
        generator = MeasurementGenerator(
            ValueRange(18.0, 32.0, 0.4),
            ValueRange(35.0, 80.0, 1.5),
            random.Random(7),
        )

        previous = generator.next_measurement("device-1")
        current = generator.next_measurement("device-1")

        self.assertLessEqual(abs(current.temperature - previous.temperature), 0.4)
        self.assertLessEqual(abs(current.humidity - previous.humidity), 1.5)

    def test_rounds_values_to_two_decimal_places(self):
        generator = MeasurementGenerator(
            ValueRange(18.0, 32.0, 0.4),
            ValueRange(35.0, 80.0, 1.5),
            random.Random(3),
        )

        measurement = generator.next_measurement("device-1")

        self.assertRegex(f"{measurement.temperature:.2f}", r"^\d+\.\d{2}$")
        self.assertRegex(f"{measurement.humidity:.2f}", r"^\d+\.\d{2}$")


class DeviceResolverTest(unittest.TestCase):
    def test_resolves_active_device_and_caches_result(self):
        hardware_uuid = "b0fee3a6-ae91-4265-9365-36f793f32f06"
        device_uuid = str(UUID("fe0a2a2e-3222-45ef-91e5-e285ccbe70a2"))
        connection = FakeConnection(rows=[(device_uuid, "ACTIVATED")])
        resolver = DeviceResolver()

        first = resolver.resolve(connection, hardware_uuid)
        second = resolver.resolve(connection, hardware_uuid)

        self.assertEqual(first, device_uuid)
        self.assertEqual(second, device_uuid)
        self.assertEqual(len(connection.executed), 1)

    def test_rejects_missing_device(self):
        connection = FakeConnection(rows=[None])
        resolver = DeviceResolver()

        with self.assertRaises(DeviceResolutionError):
            resolver.resolve(connection, DEFAULT_HARDWARE_UUID)

    def test_skips_inactivated_device(self):
        hardware_uuid = "b0fee3a6-ae91-4265-9365-36f793f32f06"
        device_uuid = str(UUID("fe0a2a2e-3222-45ef-91e5-e285ccbe70a2"))
        connection = FakeConnection(rows=[(device_uuid, "INACTIVATED")])
        resolver = DeviceResolver()

        with self.assertLogs("sensorhub_mock_sensor", level="WARNING") as logs:
            resolved = resolver.resolve(connection, hardware_uuid)

        self.assertIsNone(resolved)
        self.assertEqual(resolver.cache, {})
        self.assertIn("will not be simulated", logs.output[0])

    def test_rejects_when_no_active_devices_are_available(self):
        hardware_uuid = "b0fee3a6-ae91-4265-9365-36f793f32f06"
        device_uuid = str(UUID("fe0a2a2e-3222-45ef-91e5-e285ccbe70a2"))
        connection = FakeConnection(rows=[(device_uuid, "INACTIVATED")])
        resolver = DeviceResolver()

        with self.assertLogs("sensorhub_mock_sensor", level="WARNING"):
            with self.assertRaises(DeviceResolutionError):
                resolver.resolve_all(connection, (hardware_uuid,))

    def test_resolve_all_keeps_active_devices_when_some_are_inactivated(self):
        active_hardware_uuid = "b0fee3a6-ae91-4265-9365-36f793f32f06"
        inactive_hardware_uuid = "44dc9ffd-b376-4ceb-8f80-f7c7be550a4f"
        active_device_uuid = str(UUID("fe0a2a2e-3222-45ef-91e5-e285ccbe70a2"))
        inactive_device_uuid = str(UUID("3858f268-0281-462a-a965-e0ef76447ea0"))
        connection = FakeConnection(
            rows=[
                (active_device_uuid, "ACTIVATED"),
                (inactive_device_uuid, "INACTIVATED"),
            ]
        )
        resolver = DeviceResolver()

        with self.assertLogs("sensorhub_mock_sensor", level="WARNING"):
            resolved = resolver.resolve_all(
                connection,
                (active_hardware_uuid, inactive_hardware_uuid),
            )

        self.assertEqual(resolved, {active_hardware_uuid: active_device_uuid})


class MeasurementRepositoryTest(unittest.TestCase):
    def test_inserts_measurement_and_updates_last_seen_in_one_transaction(self):
        connection = FakeConnection()
        repository = MeasurementRepository()
        measurement = Measurement(
            temperature=23.45,
            humidity=67.89,
            measured_at="2026-06-04T10:00:00Z",
        )
        device_uuid = "fe0a2a2e-3222-45ef-91e5-e285ccbe70a2"

        repository.insert(connection, device_uuid, measurement)

        self.assertEqual(connection.commits, 1)
        self.assertEqual(connection.rollbacks, 0)
        self.assertEqual(len(connection.executed), 2)
        insert_sql, insert_params = connection.executed[0]
        update_sql, update_params = connection.executed[1]
        self.assertIn("INSERT INTO measurements", insert_sql)
        self.assertEqual(insert_params[0], device_uuid)
        self.assertEqual(insert_params[1], 23.45)
        self.assertEqual(insert_params[2], "CELSIUS")
        self.assertEqual(insert_params[3], 67.89)
        self.assertEqual(insert_params[4], "RELATIVE_PERCENT")
        self.assertIn("UPDATE devices", update_sql)
        self.assertEqual(update_params[1], device_uuid)

    def test_rolls_back_on_insert_failure(self):
        connection = FakeConnection(raise_on_execute=True)
        repository = MeasurementRepository()
        measurement = Measurement(
            temperature=23.45,
            humidity=67.89,
            measured_at="2026-06-04T10:00:00Z",
        )

        with self.assertRaises(RuntimeError):
            repository.insert(connection, "device-1", measurement)

        self.assertEqual(connection.commits, 0)
        self.assertEqual(connection.rollbacks, 1)


class FakeConnection:
    def __init__(self, rows=None, raise_on_execute=False):
        self.rows = list(rows or [])
        self.raise_on_execute = raise_on_execute
        self.executed = []
        self.commits = 0
        self.rollbacks = 0

    def cursor(self):
        return FakeCursor(self)

    def commit(self):
        self.commits += 1

    def rollback(self):
        self.rollbacks += 1


class FakeCursor:
    def __init__(self, connection):
        self.connection = connection

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        return False

    def execute(self, sql, params):
        if self.connection.raise_on_execute:
            raise RuntimeError("database error")
        self.connection.executed.append((" ".join(sql.split()), params))

    def fetchone(self):
        if not self.connection.rows:
            return None
        return self.connection.rows.pop(0)


if __name__ == "__main__":
    unittest.main()
