package com.sensorhub.ingestor;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

final class TelemetryProcessor {
    private static final Logger LOGGER = Logger.getLogger(TelemetryProcessor.class.getName());

    private final PayloadParser parser;
    private final DeviceCache deviceCache;
    private final MeasurementRepository measurementRepository;
    private final Clock clock;

    TelemetryProcessor(
            PayloadParser parser,
            DeviceCache deviceCache,
            MeasurementRepository measurementRepository,
            Clock clock
    ) {
        this.parser = parser;
        this.deviceCache = deviceCache;
        this.measurementRepository = measurementRepository;
        this.clock = clock;
    }

    boolean process(String rawPayload) {
        TelemetryPayload payload;
        try {
            payload = parser.parse(rawPayload);
        } catch (InvalidPayloadException exception) {
            LOGGER.warning("discarding invalid telemetry payload: " + exception.getMessage());
            return false;
        }

        try {
            DeviceRecord device = deviceCache.resolve(payload.hardwareUuid());
            if (device == null) {
                LOGGER.warning("discarding telemetry for unknown hardwareUuid=" + payload.hardwareUuid());
                return false;
            }
            if (device.status() != DeviceStatus.ACTIVATED) {
                LOGGER.warning(
                        "discarding telemetry for hardwareUuid="
                                + payload.hardwareUuid()
                                + " because device status is "
                                + device.status()
                );
                return false;
            }

            Instant receivedAt = clock.instant();
            measurementRepository.insert(device, payload, receivedAt);
            LOGGER.info(
                    "persisted measurement hardwareUuid="
                            + payload.hardwareUuid()
                            + " deviceUuid="
                            + device.uuid()
            );
            return true;
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE, "failed to persist telemetry for hardwareUuid=" + payload.hardwareUuid(), exception);
            return false;
        }
    }
}
