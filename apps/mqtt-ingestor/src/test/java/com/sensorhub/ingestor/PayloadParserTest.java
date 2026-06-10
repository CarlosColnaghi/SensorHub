package com.sensorhub.ingestor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayloadParserTest {
    private final PayloadParser parser = new PayloadParser();

    @Test
    void parsesValidPayload() {
        TelemetryPayload payload = parser.parse("""
                {
                  "hardwareUuid": "b0fee3a6-ae91-4265-9365-36f793f32f06",
                  "temperature": 24.70,
                  "temperatureUnit": "CELSIUS",
                  "humidity": 58.20,
                  "humidityUnit": "RELATIVE_PERCENT",
                  "measuredAt": "2026-06-01T14:30:00Z"
                }
                """);

        assertEquals(UUID.fromString("b0fee3a6-ae91-4265-9365-36f793f32f06"), payload.hardwareUuid());
        assertTrue(new BigDecimal("24.70").compareTo(payload.temperature()) == 0);
        assertEquals("CELSIUS", payload.temperatureUnit());
        assertTrue(new BigDecimal("58.20").compareTo(payload.humidity()) == 0);
        assertEquals("RELATIVE_PERCENT", payload.humidityUnit());
        assertEquals(Instant.parse("2026-06-01T14:30:00Z"), payload.measuredAt());
    }

    @Test
    void rejectsInvalidJson() {
        assertThrows(InvalidPayloadException.class, () -> parser.parse("not-json"));
    }

    @Test
    void rejectsMissingRequiredField() {
        assertThrows(InvalidPayloadException.class, () -> parser.parse("""
                {
                  "temperature": 24.70,
                  "temperatureUnit": "CELSIUS",
                  "humidity": 58.20,
                  "humidityUnit": "RELATIVE_PERCENT",
                  "measuredAt": "2026-06-01T14:30:00Z"
                }
                """));
    }

    @Test
    void rejectsInvalidUuid() {
        assertThrows(InvalidPayloadException.class, () -> parser.parse("""
                {
                  "hardwareUuid": "invalid",
                  "temperature": 24.70,
                  "temperatureUnit": "CELSIUS",
                  "humidity": 58.20,
                  "humidityUnit": "RELATIVE_PERCENT",
                  "measuredAt": "2026-06-01T14:30:00Z"
                }
                """));
    }

    @Test
    void rejectsUnknownUnits() {
        assertThrows(InvalidPayloadException.class, () -> parser.parse("""
                {
                  "hardwareUuid": "b0fee3a6-ae91-4265-9365-36f793f32f06",
                  "temperature": 24.70,
                  "temperatureUnit": "FAHRENHEIT",
                  "humidity": 58.20,
                  "humidityUnit": "RELATIVE_PERCENT",
                  "measuredAt": "2026-06-01T14:30:00Z"
                }
                """));
    }

    @Test
    void rejectsInvalidMeasuredAt() {
        assertThrows(InvalidPayloadException.class, () -> parser.parse("""
                {
                  "hardwareUuid": "b0fee3a6-ae91-4265-9365-36f793f32f06",
                  "temperature": 24.70,
                  "temperatureUnit": "CELSIUS",
                  "humidity": 58.20,
                  "humidityUnit": "RELATIVE_PERCENT",
                  "measuredAt": "invalid"
                }
                """));
    }
}
