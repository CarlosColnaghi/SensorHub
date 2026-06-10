package com.sensorhub.ingestor;

import java.time.Duration;
import java.util.Map;

record AppConfig(
        MqttConfig mqtt,
        DatabaseConfig database,
        Duration deviceCacheTtl
) {
    static AppConfig fromEnvironment() {
        return from(System.getenv());
    }

    static AppConfig from(Map<String, String> values) {
        int mqttPort = readInt(values, "SENSORHUB_MQTT_PORT", 1883);
        if (mqttPort <= 0) {
            throw new ConfigException("SENSORHUB_MQTT_PORT must be greater than zero");
        }

        int mqttQos = readInt(values, "SENSORHUB_MQTT_QOS", 0);
        if (mqttQos < 0 || mqttQos > 2) {
            throw new ConfigException("SENSORHUB_MQTT_QOS must be 0, 1, or 2");
        }

        int dbPort = readInt(values, "SENSORHUB_DB_PORT", 5432);
        if (dbPort <= 0) {
            throw new ConfigException("SENSORHUB_DB_PORT must be greater than zero");
        }

        long cacheTtlSeconds = readLong(values, "SENSORHUB_DEVICE_CACHE_TTL_SECONDS", 300);
        if (cacheTtlSeconds <= 0) {
            throw new ConfigException("SENSORHUB_DEVICE_CACHE_TTL_SECONDS must be greater than zero");
        }

        MqttConfig mqtt = new MqttConfig(
                values.getOrDefault("SENSORHUB_MQTT_HOST", "mqtt"),
                mqttPort,
                values.getOrDefault("SENSORHUB_MQTT_TOPIC", "sensorhub/measurements"),
                values.getOrDefault("SENSORHUB_MQTT_CLIENT_ID", "sensorhub-mqtt-ingestor"),
                mqttQos
        );
        DatabaseConfig database = new DatabaseConfig(
                values.getOrDefault("SENSORHUB_DB_HOST", "postgres"),
                dbPort,
                values.getOrDefault("SENSORHUB_DB_NAME", "sensorhub"),
                values.getOrDefault("SENSORHUB_DB_USER", "sensorhub"),
                values.getOrDefault("SENSORHUB_DB_PASSWORD", "sensorhub")
        );
        return new AppConfig(mqtt, database, Duration.ofSeconds(cacheTtlSeconds));
    }

    private static int readInt(Map<String, String> values, String key, int defaultValue) {
        String rawValue = values.get(key);
        if (rawValue == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException exception) {
            throw new ConfigException(key + " must be an integer", exception);
        }
    }

    private static long readLong(Map<String, String> values, String key, long defaultValue) {
        String rawValue = values.get(key);
        if (rawValue == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException exception) {
            throw new ConfigException(key + " must be an integer", exception);
        }
    }
}
