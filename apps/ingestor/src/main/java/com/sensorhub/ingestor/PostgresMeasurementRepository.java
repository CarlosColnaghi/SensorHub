package com.sensorhub.ingestor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class PostgresMeasurementRepository implements MeasurementRepository {
    private static final String INSERT_MEASUREMENT = """
            INSERT INTO measurements (
                device_uuid,
                temperature,
                temperature_unit,
                humidity,
                humidity_unit,
                measured_at,
                received_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final DatabaseConnectionFactory connectionFactory;

    PostgresMeasurementRepository(DatabaseConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void insert(DeviceRecord device, TelemetryPayload payload, Instant receivedAt)
            throws SQLException {
        try (
                Connection connection = connectionFactory.open();
                PreparedStatement statement = connection.prepareStatement(INSERT_MEASUREMENT)
        ) {
            statement.setObject(1, device.uuid());
            statement.setBigDecimal(2, payload.temperature());
            statement.setString(3, payload.temperatureUnit());
            statement.setBigDecimal(4, payload.humidity());
            statement.setString(5, payload.humidityUnit());
            statement.setObject(6, OffsetDateTime.ofInstant(payload.measuredAt(), ZoneOffset.UTC));
            statement.setObject(7, OffsetDateTime.ofInstant(receivedAt, ZoneOffset.UTC));
            statement.executeUpdate();
        }
    }
}
