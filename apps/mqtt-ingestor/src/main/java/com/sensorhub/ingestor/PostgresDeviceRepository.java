package com.sensorhub.ingestor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

final class PostgresDeviceRepository implements DeviceRepository {
    private static final String SELECT_DEVICE = """
            SELECT uuid, status
            FROM devices
            WHERE hardware_uuid = ?
            """;

    private final DatabaseConnectionFactory connectionFactory;

    PostgresDeviceRepository(DatabaseConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public DeviceRecord findByHardwareUuid(UUID hardwareUuid) throws SQLException {
        try (
                Connection connection = connectionFactory.open();
                PreparedStatement statement = connection.prepareStatement(SELECT_DEVICE)
        ) {
            statement.setObject(1, hardwareUuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new DeviceRecord(
                        resultSet.getObject("uuid", UUID.class),
                        DeviceStatus.fromDatabase(resultSet.getString("status"))
                );
            }
        }
    }
}
