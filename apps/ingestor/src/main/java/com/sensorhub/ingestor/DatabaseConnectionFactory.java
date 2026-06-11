package com.sensorhub.ingestor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

final class DatabaseConnectionFactory {
    private final DatabaseConfig config;

    DatabaseConnectionFactory(DatabaseConfig config) {
        this.config = config;
    }

    Connection open() throws SQLException {
        return DriverManager.getConnection(config.jdbcUrl(), config.user(), config.password());
    }
}
