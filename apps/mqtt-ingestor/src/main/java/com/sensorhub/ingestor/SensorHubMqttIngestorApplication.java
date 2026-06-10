package com.sensorhub.ingestor;

import java.time.Clock;
import java.sql.Connection;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SensorHubMqttIngestorApplication {
    private static final Logger LOGGER = Logger.getLogger(SensorHubMqttIngestorApplication.class.getName());

    private SensorHubMqttIngestorApplication() {
    }

    public static void main(String[] args) {
        try {
            AppConfig config = AppConfig.fromEnvironment();
            DatabaseConnectionFactory connectionFactory = new DatabaseConnectionFactory(config.database());
            try (Connection ignored = connectionFactory.open()) {
                LOGGER.info("connected to PostgreSQL at " + config.database().jdbcUrl());
            }
            DeviceRepository deviceRepository = new PostgresDeviceRepository(connectionFactory);
            MeasurementRepository measurementRepository = new PostgresMeasurementRepository(connectionFactory);
            DeviceCache deviceCache = new DeviceCache(
                    deviceRepository,
                    config.deviceCacheTtl(),
                    Clock.systemUTC()
            );
            TelemetryProcessor processor = new TelemetryProcessor(
                    new PayloadParser(),
                    deviceCache,
                    measurementRepository,
                    Clock.systemUTC()
            );

            try (MqttTelemetryConsumer consumer = new MqttTelemetryConsumer(config.mqtt(), processor)) {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        consumer.close();
                    } catch (Exception exception) {
                        LOGGER.log(Level.WARNING, "failed to close MQTT consumer", exception);
                    }
                }));
                consumer.start();
                consumer.await();
            }
        } catch (ConfigException exception) {
            LOGGER.severe(exception.getMessage());
            System.exit(1);
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "MQTT ingestor failed", exception);
            System.exit(1);
        }
    }
}
