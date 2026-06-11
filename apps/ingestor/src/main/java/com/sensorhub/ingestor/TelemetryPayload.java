package com.sensorhub.ingestor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record TelemetryPayload(
        UUID hardwareUuid,
        BigDecimal temperature,
        String temperatureUnit,
        BigDecimal humidity,
        String humidityUnit,
        Instant measuredAt
) {
}
