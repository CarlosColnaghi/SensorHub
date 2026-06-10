package com.sensorhub.ingestor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

final class PayloadParser {
    private static final String TEMPERATURE_UNIT = "CELSIUS";
    private static final String HUMIDITY_UNIT = "RELATIVE_PERCENT";

    private final ObjectMapper objectMapper = new ObjectMapper();

    TelemetryPayload parse(String rawPayload) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawPayload);
        } catch (IOException exception) {
            throw new InvalidPayloadException("payload must be valid JSON", exception);
        }
        if (!root.isObject()) {
            throw new InvalidPayloadException("payload must be a JSON object");
        }

        UUID hardwareUuid = readUuid(root, "hardwareUuid");
        BigDecimal temperature = readNumber(root, "temperature");
        String temperatureUnit = readText(root, "temperatureUnit");
        BigDecimal humidity = readNumber(root, "humidity");
        String humidityUnit = readText(root, "humidityUnit");
        Instant measuredAt = readInstant(root, "measuredAt");

        if (!TEMPERATURE_UNIT.equals(temperatureUnit)) {
            throw new InvalidPayloadException("temperatureUnit must be " + TEMPERATURE_UNIT);
        }
        if (!HUMIDITY_UNIT.equals(humidityUnit)) {
            throw new InvalidPayloadException("humidityUnit must be " + HUMIDITY_UNIT);
        }

        return new TelemetryPayload(
                hardwareUuid,
                temperature,
                temperatureUnit,
                humidity,
                humidityUnit,
                measuredAt
        );
    }

    private UUID readUuid(JsonNode root, String fieldName) {
        String value = readText(root, fieldName);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPayloadException(fieldName + " must be a UUID", exception);
        }
    }

    private BigDecimal readNumber(JsonNode root, String fieldName) {
        JsonNode node = required(root, fieldName);
        if (!node.isNumber()) {
            throw new InvalidPayloadException(fieldName + " must be numeric");
        }
        return node.decimalValue();
    }

    private String readText(JsonNode root, String fieldName) {
        JsonNode node = required(root, fieldName);
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw new InvalidPayloadException(fieldName + " must be text");
        }
        return node.textValue();
    }

    private Instant readInstant(JsonNode root, String fieldName) {
        String value = readText(root, fieldName);
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new InvalidPayloadException(fieldName + " must be an ISO 8601 instant", exception);
        }
    }

    private JsonNode required(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        if (node == null || node.isNull()) {
            throw new InvalidPayloadException(fieldName + " is required");
        }
        return node;
    }
}
