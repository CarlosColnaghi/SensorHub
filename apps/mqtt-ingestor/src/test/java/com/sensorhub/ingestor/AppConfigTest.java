package com.sensorhub.ingestor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppConfigTest {
    @Test
    void readsDefaultConfig() {
        AppConfig config = AppConfig.from(Map.of());

        assertEquals("mqtt", config.mqtt().host());
        assertEquals(1883, config.mqtt().port());
        assertEquals("sensorhub/measurements", config.mqtt().topic());
        assertEquals("sensorhub-mqtt-ingestor", config.mqtt().clientId());
        assertEquals(0, config.mqtt().qos());
        assertEquals("postgres", config.database().host());
        assertEquals(5432, config.database().port());
        assertEquals("sensorhub", config.database().name());
        assertEquals("sensorhub", config.database().user());
        assertEquals("sensorhub", config.database().password());
        assertEquals(Duration.ofSeconds(300), config.deviceCacheTtl());
    }

    @Test
    void readsProvidedConfig() {
        Map<String, String> values = new HashMap<>();
        values.put("SENSORHUB_MQTT_HOST", "localhost");
        values.put("SENSORHUB_MQTT_PORT", "1884");
        values.put("SENSORHUB_MQTT_TOPIC", "custom/topic");
        values.put("SENSORHUB_MQTT_CLIENT_ID", "custom-client");
        values.put("SENSORHUB_MQTT_QOS", "1");
        values.put("SENSORHUB_DB_HOST", "db");
        values.put("SENSORHUB_DB_PORT", "5433");
        values.put("SENSORHUB_DB_NAME", "custom");
        values.put("SENSORHUB_DB_USER", "custom_user");
        values.put("SENSORHUB_DB_PASSWORD", "custom_password");
        values.put("SENSORHUB_DEVICE_CACHE_TTL_SECONDS", "60");

        AppConfig config = AppConfig.from(values);

        assertEquals("localhost", config.mqtt().host());
        assertEquals(1884, config.mqtt().port());
        assertEquals("custom/topic", config.mqtt().topic());
        assertEquals("custom-client", config.mqtt().clientId());
        assertEquals(1, config.mqtt().qos());
        assertEquals("db", config.database().host());
        assertEquals(5433, config.database().port());
        assertEquals("custom", config.database().name());
        assertEquals("custom_user", config.database().user());
        assertEquals("custom_password", config.database().password());
        assertEquals(Duration.ofSeconds(60), config.deviceCacheTtl());
    }

    @Test
    void rejectsInvalidQos() {
        assertThrows(ConfigException.class, () -> AppConfig.from(Map.of("SENSORHUB_MQTT_QOS", "3")));
    }

    @Test
    void rejectsInvalidCacheTtl() {
        assertThrows(
                ConfigException.class,
                () -> AppConfig.from(Map.of("SENSORHUB_DEVICE_CACHE_TTL_SECONDS", "0"))
        );
    }
}
