import json
import random
import sys
import unittest
from datetime import datetime, timezone
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from sensorhub_mock_sensor.main import (  # noqa: E402
    DEFAULT_HARDWARE_UUID,
    ConfigError,
    Measurement,
    MeasurementGenerator,
    MockSensorRunner,
    ValueRange,
    build_payload,
    parse_hardware_uuids,
    read_config,
)


class ConfigTest(unittest.TestCase):
    def test_reads_default_config(self):
        config = read_config({})

        self.assertEqual(config.mqtt.host, "mqtt")
        self.assertEqual(config.mqtt.port, 1883)
        self.assertEqual(config.mqtt.topic, "sensorhub/measurements")
        self.assertEqual(config.mqtt.client_id, "sensorhub-mock-sensor")
        self.assertEqual(config.mqtt.qos, 0)
        self.assertEqual(config.hardware_uuids, (DEFAULT_HARDWARE_UUID,))
        self.assertEqual(config.interval_seconds, 5.0)

    def test_reads_mqtt_config(self):
        config = read_config(
            {
                "SENSORHUB_MQTT_HOST": "localhost",
                "SENSORHUB_MQTT_PORT": "1884",
                "SENSORHUB_MQTT_TOPIC": "custom/topic",
                "SENSORHUB_MQTT_CLIENT_ID": "custom-client",
                "SENSORHUB_MQTT_QOS": "1",
            }
        )

        self.assertEqual(config.mqtt.host, "localhost")
        self.assertEqual(config.mqtt.port, 1884)
        self.assertEqual(config.mqtt.topic, "custom/topic")
        self.assertEqual(config.mqtt.client_id, "custom-client")
        self.assertEqual(config.mqtt.qos, 1)

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

    def test_rejects_invalid_qos(self):
        with self.assertRaises(ConfigError):
            read_config({"SENSORHUB_MQTT_QOS": "3"})


class MeasurementGeneratorTest(unittest.TestCase):
    def test_generates_values_inside_ranges(self):
        generator = MeasurementGenerator(
            ValueRange(18.0, 32.0, 0.4),
            ValueRange(35.0, 80.0, 1.5),
            random.Random(1),
        )

        measurement = generator.next_measurement("hardware-1")

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

        previous = generator.next_measurement("hardware-1")
        current = generator.next_measurement("hardware-1")

        self.assertLessEqual(abs(current.temperature - previous.temperature), 0.4)
        self.assertLessEqual(abs(current.humidity - previous.humidity), 1.5)

    def test_rounds_values_to_two_decimal_places(self):
        generator = MeasurementGenerator(
            ValueRange(18.0, 32.0, 0.4),
            ValueRange(35.0, 80.0, 1.5),
            random.Random(3),
        )

        measurement = generator.next_measurement("hardware-1")

        self.assertRegex(f"{measurement.temperature:.2f}", r"^\d+\.\d{2}$")
        self.assertRegex(f"{measurement.humidity:.2f}", r"^\d+\.\d{2}$")


class PayloadTest(unittest.TestCase):
    def test_builds_sensor_payload_without_internal_fields(self):
        payload = build_payload(
            DEFAULT_HARDWARE_UUID,
            Measurement(
                temperature=23.45,
                humidity=67.89,
                measured_at=datetime(2026, 6, 4, 10, 0, tzinfo=timezone.utc),
            ),
        )

        decoded = json.loads(payload)

        self.assertEqual(decoded["hardwareUuid"], DEFAULT_HARDWARE_UUID)
        self.assertEqual(decoded["temperature"], 23.45)
        self.assertEqual(decoded["temperatureUnit"], "CELSIUS")
        self.assertEqual(decoded["humidity"], 67.89)
        self.assertEqual(decoded["humidityUnit"], "RELATIVE_PERCENT")
        self.assertEqual(decoded["measuredAt"], "2026-06-04T10:00:00Z")
        self.assertNotIn("deviceUuid", decoded)
        self.assertNotIn("receivedAt", decoded)


class RunnerTest(unittest.TestCase):
    def test_publishes_measurement_to_configured_topic(self):
        config = read_config(
            {
                "SENSORHUB_HARDWARE_UUIDS": DEFAULT_HARDWARE_UUID,
                "SENSORHUB_MQTT_TOPIC": "sensorhub/test",
                "SENSORHUB_MQTT_QOS": "1",
            }
        )
        publisher = FakePublisher()

        runner = MockSensorRunner(
            config,
            publisher_factory=lambda mqtt: publisher,
            sleep=lambda interval: runner.stop(),
        )
        runner.run_forever()

        self.assertEqual(len(publisher.published), 1)
        topic, payload, qos = publisher.published[0]
        decoded = json.loads(payload)
        self.assertEqual(topic, "sensorhub/test")
        self.assertEqual(qos, 1)
        self.assertEqual(decoded["hardwareUuid"], DEFAULT_HARDWARE_UUID)
        self.assertTrue(publisher.closed)


class FakePublisher:
    def __init__(self):
        self.published = []
        self.closed = False

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        self.closed = True

    def publish(self, topic, payload, qos):
        self.published.append((topic, payload, qos))


if __name__ == "__main__":
    unittest.main()
