package com.sensorhub.ingestor;

import java.sql.SQLException;
import java.time.Instant;

interface MeasurementRepository {
    void insert(DeviceRecord device, TelemetryPayload payload, Instant receivedAt) throws SQLException;
}
