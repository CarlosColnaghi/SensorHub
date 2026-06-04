package com.sensorhub.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sensorhub.api.domain.AppUser;
import com.sensorhub.api.domain.Device;
import com.sensorhub.api.domain.DeviceStatus;
import com.sensorhub.api.domain.Measurement;
import com.sensorhub.api.domain.SensorEnvironment;
import com.sensorhub.api.repository.DeviceRepository;
import com.sensorhub.api.repository.EnvironmentRepository;
import com.sensorhub.api.repository.MeasurementRepository;
import com.sensorhub.api.repository.UserRepository;
import com.sensorhub.api.service.MeasurementService;
import com.sensorhub.api.service.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RepositoryAndBusinessRuleTest extends AbstractPostgresIntegrationTest {

    @Autowired
    UserRepository users;

    @Autowired
    EnvironmentRepository environments;

    @Autowired
    DeviceRepository devices;

    @Autowired
    MeasurementRepository measurements;

    @Autowired
    MeasurementService measurementService;

    @Test
    void repositoriesPersistAndFindMainDomains() {
        AppUser user = createUser("Repository User", uniqueEmail("repository"));
        SensorEnvironment environment = createEnvironment(user.getUuid(), "Office");
        Device deviceWithoutEnvironment = createDevice(user.getUuid(), UUID.randomUUID(), null);
        Device deviceWithEnvironment = createDevice(user.getUuid(), UUID.randomUUID(), environment.getUuid());
        Measurement measurement = createMeasurement(deviceWithEnvironment.getUuid(), "24.10");

        assertThat(users.findById(user.getUuid())).hasValueSatisfying(found ->
                assertThat(found.getEmail()).isEqualTo(user.getEmail()));
        assertThat(environments.findById(environment.getUuid())).hasValueSatisfying(found ->
                assertThat(found.getUserUuid()).isEqualTo(user.getUuid()));
        assertThat(devices.findById(deviceWithoutEnvironment.getUuid())).hasValueSatisfying(found ->
                assertThat(found.getEnvironmentUuid()).isNull());
        assertThat(devices.findById(deviceWithEnvironment.getUuid())).hasValueSatisfying(found ->
                assertThat(found.getEnvironmentUuid()).isEqualTo(environment.getUuid()));
        assertThat(deviceWithEnvironment.getStatus()).isEqualTo(DeviceStatus.ACTIVATED);
        assertThat(measurements.findById(measurement.getUuid())).hasValueSatisfying(found ->
                assertThat(found.getDeviceUuid()).isEqualTo(deviceWithEnvironment.getUuid()));
    }

    @Test
    void recordingMeasurementRejectsMissingDevice() {
        Measurement measurement = newMeasurement(UUID.randomUUID(), "20.00");

        assertThatThrownBy(() -> measurementService.record(measurement))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("device not found");
    }

    @Test
    void recordingMeasurementOverridesReceivedAtWithSystemTime() {
        AppUser user = createUser("Measurement User", uniqueEmail("measurement"));
        Device device = createDevice(user.getUuid(), UUID.randomUUID(), null);
        Measurement measurement = newMeasurement(device.getUuid(), "22.00");
        measurement.setReceivedAt(Instant.parse("2026-01-01T00:00:00Z"));

        Instant beforeRecord = Instant.now();
        Measurement saved = measurementService.record(measurement);
        Instant afterRecord = Instant.now();

        assertThat(saved.getReceivedAt()).isBetween(beforeRecord, afterRecord);
        assertThat(saved.getReceivedAt()).isNotEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(devices.findById(device.getUuid())).hasValueSatisfying(found ->
                assertThat(found.getLastSeenAt()).isNotNull());
    }

    private AppUser createUser(String name, String email) {
        AppUser user = new AppUser();
        user.setName(name);
        user.setEmail(email);
        return users.saveAndFlush(user);
    }

    private SensorEnvironment createEnvironment(UUID userUuid, String name) {
        SensorEnvironment environment = new SensorEnvironment();
        environment.setUserUuid(userUuid);
        environment.setName(name);
        return environments.saveAndFlush(environment);
    }

    private Device createDevice(UUID userUuid, UUID hardwareUuid, UUID environmentUuid) {
        Device device = new Device();
        device.setUserUuid(userUuid);
        device.setHardwareUuid(hardwareUuid);
        device.setEnvironmentUuid(environmentUuid);
        device.setName("Sensor");
        return devices.saveAndFlush(device);
    }

    private Measurement createMeasurement(UUID deviceUuid, String temperature) {
        Measurement measurement = newMeasurement(deviceUuid, temperature);
        return measurements.saveAndFlush(measurement);
    }

    private Measurement newMeasurement(UUID deviceUuid, String temperature) {
        Measurement measurement = new Measurement();
        measurement.setDeviceUuid(deviceUuid);
        measurement.setTemperature(new BigDecimal(temperature));
        measurement.setTemperatureUnit("CELSIUS");
        measurement.setHumidity(new BigDecimal("48.00"));
        measurement.setHumidityUnit("RELATIVE_PERCENT");
        measurement.setMeasuredAt(Instant.parse("2026-06-01T10:00:00Z"));
        return measurement;
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
