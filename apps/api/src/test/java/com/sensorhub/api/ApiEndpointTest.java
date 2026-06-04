package com.sensorhub.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.sensorhub.api.domain.Measurement;
import com.sensorhub.api.repository.DeviceRepository;
import com.sensorhub.api.repository.MeasurementRepository;
import com.sensorhub.api.service.MeasurementService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ApiEndpointTest extends AbstractPostgresIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    MeasurementRepository measurements;

    @Autowired
    MeasurementService measurementService;

    @Autowired
    DeviceRepository devices;

    @Test
    void userEnvironmentAndDeviceCrudWorks() {
        Map<?, ?> user = createUser("Linus Torvalds", "linus@example.com");
        String userUuid = (String) user.get("uuid");

        ResponseEntity<Map> environmentResponse = rest.postForEntity("/api/v1/environments", Map.of(
                "userUuid", userUuid,
                "name", "Office"
        ), Map.class);
        assertThat(environmentResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> environment = environmentResponse.getBody();
        assertThat(environment).isNotNull();

        UUID hardwareUuid = UUID.randomUUID();
        ResponseEntity<Map> deviceResponse = rest.postForEntity("/api/v1/devices", Map.of(
                "hardwareUuid", hardwareUuid.toString(),
                "userUuid", userUuid,
                "environmentUuid", environment.get("uuid"),
                "name", "Office sensor"
        ), Map.class);
        assertThat(deviceResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> device = deviceResponse.getBody();
        assertThat(device).isNotNull();
        assertThat(device.get("uuid")).isNotNull();
        assertThat(device.get("hardwareUuid")).isEqualTo(hardwareUuid.toString());
        assertThat(device.get("status")).isEqualTo("ACTIVATED");

        ResponseEntity<Map> getResponse = rest.getForEntity("/api/v1/devices/" + device.get("uuid"), Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).containsEntry("uuid", device.get("uuid"));

        ResponseEntity<List> listResponse = rest.getForEntity("/api/v1/devices", List.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotEmpty();

        ResponseEntity<Map> updateResponse = rest.exchange(
                "/api/v1/devices/" + device.get("uuid"),
                HttpMethod.PUT,
                new HttpEntity<>(Map.of(
                        "environmentUuid", environment.get("uuid"),
                        "status", "INACTIVATED",
                        "name", "Updated sensor"
                )),
                Map.class
        );
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody()).containsEntry("name", "Updated sensor");
        assertThat(updateResponse.getBody()).containsEntry("status", "INACTIVATED");

        ResponseEntity<Void> deleteResponse = rest.exchange(
                "/api/v1/devices/" + device.get("uuid"),
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class
        );
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> deletedGetResponse = rest.getForEntity("/api/v1/devices/" + device.get("uuid"), Map.class);
        assertThat(deletedGetResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void userCrudWorks() {
        Map<?, ?> user = createUser("User CRUD", uniqueEmail("user-crud"));

        ResponseEntity<Map> getResponse = rest.getForEntity("/api/v1/users/" + user.get("uuid"), Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).containsEntry("email", user.get("email"));

        ResponseEntity<List> listResponse = rest.getForEntity("/api/v1/users", List.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotEmpty();

        ResponseEntity<Map> updateResponse = rest.exchange(
                "/api/v1/users/" + user.get("uuid"),
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Updated User", "email", uniqueEmail("updated-user"))),
                Map.class
        );
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody()).containsEntry("name", "Updated User");

        ResponseEntity<Void> deleteResponse = rest.exchange(
                "/api/v1/users/" + user.get("uuid"),
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class
        );
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> deletedGetResponse = rest.getForEntity("/api/v1/users/" + user.get("uuid"), Map.class);
        assertThat(deletedGetResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void environmentCrudWorks() {
        Map<?, ?> user = createUser("Environment CRUD", uniqueEmail("environment-crud"));

        ResponseEntity<Map> createResponse = rest.postForEntity("/api/v1/environments", Map.of(
                "userUuid", user.get("uuid"),
                "name", "Lab"
        ), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> environment = createResponse.getBody();
        assertThat(environment).isNotNull();

        ResponseEntity<Map> getResponse = rest.getForEntity("/api/v1/environments/" + environment.get("uuid"), Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).containsEntry("name", "Lab");

        ResponseEntity<List> listResponse = rest.getForEntity("/api/v1/environments", List.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotEmpty();

        ResponseEntity<Map> updateResponse = rest.exchange(
                "/api/v1/environments/" + environment.get("uuid"),
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Updated Lab")),
                Map.class
        );
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody()).containsEntry("name", "Updated Lab");

        ResponseEntity<Void> deleteResponse = rest.exchange(
                "/api/v1/environments/" + environment.get("uuid"),
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class
        );
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> deletedGetResponse = rest.getForEntity(
                "/api/v1/environments/" + environment.get("uuid"),
                Map.class
        );
        assertThat(deletedGetResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void measurementsAreReadOnlyAndSupportTimeQueriesAndLatest() {
        Map<?, ?> user = createUser("Marie Curie", "marie@example.com");
        Map<?, ?> device = createDevice((String) user.get("uuid"), null);
        UUID deviceUuid = UUID.fromString((String) device.get("uuid"));

        Measurement olderByMeasuredAt = createMeasurement(
                deviceUuid,
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-03T10:00:00Z"),
                "21.50"
        );
        Measurement latestByMeasuredAt = createMeasurement(
                deviceUuid,
                Instant.parse("2026-06-02T10:00:00Z"),
                Instant.parse("2026-06-02T10:00:00Z"),
                "22.75"
        );

        ResponseEntity<String> postResponse = rest.postForEntity("/api/v1/measurements", Map.of(), String.class);
        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);

        ResponseEntity<String> putResponse = rest.exchange(
                "/api/v1/measurements/" + olderByMeasuredAt.getUuid(),
                HttpMethod.PUT,
                new HttpEntity<>(Map.of()),
                String.class
        );
        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);

        ResponseEntity<String> deleteResponse = rest.exchange(
                "/api/v1/measurements/" + olderByMeasuredAt.getUuid(),
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                String.class
        );
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);

        ResponseEntity<Map> latestResponse = rest.getForEntity(
                "/api/v1/devices/" + deviceUuid + "/measurements/latest",
                Map.class
        );
        assertThat(latestResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(latestResponse.getBody()).containsEntry("uuid", latestByMeasuredAt.getUuid().toString());

        ResponseEntity<Map> allMeasurementsPage = rest.getForEntity("/api/v1/measurements?page=0&size=50", Map.class);
        assertThat(allMeasurementsPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) allMeasurementsPage.getBody().get("totalItems")).longValue()).isGreaterThanOrEqualTo(2);

        ResponseEntity<Map> getMeasurementResponse = rest.getForEntity(
                "/api/v1/measurements/" + olderByMeasuredAt.getUuid(),
                Map.class
        );
        assertThat(getMeasurementResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getMeasurementResponse.getBody()).containsEntry("uuid", olderByMeasuredAt.getUuid().toString());

        ResponseEntity<Map> measuredAtPage = rest.getForEntity(
                "/api/v1/devices/" + deviceUuid + "/measurements?from=2026-06-02T00:00:00Z&to=2026-06-02T23:59:59Z&timeField=measuredAt&page=0&size=50",
                Map.class
        );
        assertThat(measuredAtPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(measuredAtPage.getBody()).containsEntry("totalItems", 1);

        ResponseEntity<Map> receivedAtPage = rest.getForEntity(
                "/api/v1/devices/" + deviceUuid + "/measurements?from=2026-06-03T00:00:00Z&to=2026-06-03T23:59:59Z&timeField=receivedAt&page=0&size=50",
                Map.class
        );
        assertThat(receivedAtPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(receivedAtPage.getBody()).containsEntry("totalItems", 1);
        assertThat(olderByMeasuredAt.getUuid()).isNotNull();
    }

    @Test
    void deviceValidationErrorsAreReturned() {
        Map<?, ?> firstUser = createUser("Device Validation One", uniqueEmail("device-validation-one"));
        Map<?, ?> secondUser = createUser("Device Validation Two", uniqueEmail("device-validation-two"));
        ResponseEntity<Map> secondUserEnvironment = rest.postForEntity("/api/v1/environments", Map.of(
                "userUuid", secondUser.get("uuid"),
                "name", "Other lab"
        ), Map.class);
        assertThat(secondUserEnvironment.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        UUID hardwareUuid = UUID.randomUUID();
        Map<?, ?> device = createDevice((String) firstUser.get("uuid"), null, hardwareUuid);

        ResponseEntity<Map> duplicateHardware = rest.postForEntity("/api/v1/devices", Map.of(
                "hardwareUuid", hardwareUuid.toString(),
                "userUuid", firstUser.get("uuid"),
                "name", "Duplicate sensor"
        ), Map.class);
        assertThat(duplicateHardware.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Map> missingEnvironment = rest.postForEntity("/api/v1/devices", Map.of(
                "hardwareUuid", UUID.randomUUID().toString(),
                "userUuid", firstUser.get("uuid"),
                "environmentUuid", UUID.randomUUID().toString(),
                "name", "Missing environment sensor"
        ), Map.class);
        assertThat(missingEnvironment.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map> otherUserEnvironment = rest.exchange(
                "/api/v1/devices/" + device.get("uuid"),
                HttpMethod.PUT,
                new HttpEntity<>(Map.of(
                        "environmentUuid", secondUserEnvironment.getBody().get("uuid"),
                        "name", "Cross-user sensor"
                )),
                Map.class
        );
        assertThat(otherUserEnvironment.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void latestMeasurementReturnsNoContentWhenDeviceHasNoMeasurements() {
        Map<?, ?> user = createUser("Empty User", "empty@example.com");
        Map<?, ?> device = createDevice((String) user.get("uuid"), null);

        ResponseEntity<Void> response = rest.getForEntity(
                "/api/v1/devices/" + device.get("uuid") + "/measurements/latest",
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void mobileDashboardAndMeasurementOverviewContractsWork() {
        Map<?, ?> user = createUser("Mobile User", uniqueEmail("mobile-user"));
        ResponseEntity<Map> environmentResponse = rest.postForEntity("/api/v1/environments", Map.of(
                "userUuid", user.get("uuid"),
                "name", "Greenhouse"
        ), Map.class);
        assertThat(environmentResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<?, ?> device = createDevice((String) user.get("uuid"), (String) environmentResponse.getBody().get("uuid"));
        Map<?, ?> emptyDevice = createDevice((String) user.get("uuid"), null);
        UUID deviceUuid = UUID.fromString((String) device.get("uuid"));
        UUID emptyDeviceUuid = UUID.fromString((String) emptyDevice.get("uuid"));
        Instant now = Instant.now();
        Instant from = now.minus(Duration.ofMinutes(20));
        Instant to = now.plus(Duration.ofMinutes(1));

        createMeasurement(deviceUuid, now.minus(Duration.ofMinutes(15)), now.minus(Duration.ofMinutes(15)), "20.00", "50.00");
        createMeasurement(deviceUuid, now.minus(Duration.ofMinutes(10)), now.minus(Duration.ofMinutes(10)), "25.00", "60.00");
        createMeasurement(deviceUuid, now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(5)), "22.00", "40.00");
        devices.findById(deviceUuid).ifPresent(savedDevice -> {
            savedDevice.setLastSeenAt(now);
            devices.saveAndFlush(savedDevice);
        });

        ResponseEntity<List> dashboardDevicesResponse = rest.getForEntity(
                "/api/v1/users/" + user.get("uuid") + "/dashboard/devices",
                List.class
        );
        assertThat(dashboardDevicesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dashboardDevicesResponse.getBody()).hasSize(2);
        Map<?, ?> dashboardDevice = findByDeviceUuid(dashboardDevicesResponse.getBody(), deviceUuid);
        assertThat(dashboardDevice.get("environmentName")).isEqualTo("Greenhouse");
        assertThat(dashboardDevice.get("deviceStatus")).isEqualTo("ACTIVATED");
        assertThat(dashboardDevice.containsKey("latestMeasurement")).isFalse();
        assertThat(dashboardDevice.containsKey("freshnessStatus")).isFalse();

        ResponseEntity<List> latestDashboardResponse = rest.getForEntity(
                "/api/v1/users/" + user.get("uuid") + "/dashboard/measurements/latest",
                List.class
        );
        assertThat(latestDashboardResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> latestDevice = findByDeviceUuid(latestDashboardResponse.getBody(), deviceUuid);
        assertThat(latestDevice.get("freshnessStatus")).isEqualTo("ONLINE");
        assertThat(latestDevice.get("latestMeasurement")).isInstanceOf(Map.class);
        Map<?, ?> latestEmptyDevice = findByDeviceUuid(latestDashboardResponse.getBody(), emptyDeviceUuid);
        assertThat(latestEmptyDevice.get("freshnessStatus")).isEqualTo("NO_DATA");
        assertThat(latestEmptyDevice.get("latestMeasurement")).isNull();

        ResponseEntity<Map> overviewResponse = rest.getForEntity(
                "/api/v1/devices/" + deviceUuid
                        + "/measurements/overview?from=" + from
                        + "&to=" + to
                        + "&bucket=5m",
                Map.class
        );
        assertThat(overviewResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> overview = overviewResponse.getBody();
        assertThat(overview).isNotNull();
        assertThat(overview.get("deviceUuid")).isEqualTo(deviceUuid.toString());
        assertThat((List<?>) overview.get("series")).hasSize(3);
        Map<?, ?> overviewStats = (Map<?, ?>) overview.get("overview");
        assertThat(((Number) overviewStats.get("measurementCount")).longValue()).isEqualTo(3);
        assertThat(((Number) overviewStats.get("temperatureMax")).doubleValue()).isEqualTo(25.0);
        assertThat(((Number) overviewStats.get("temperatureMin")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) overviewStats.get("temperatureAverage")).doubleValue()).isEqualTo(22.33);
        assertThat(((Number) overviewStats.get("humidityMax")).doubleValue()).isEqualTo(60.0);
        assertThat(((Number) overviewStats.get("humidityMin")).doubleValue()).isEqualTo(40.0);
        assertThat(((Number) overviewStats.get("humidityAverage")).doubleValue()).isEqualTo(50.0);
    }

    @Test
    void internalMeasurementRecordingSetsReceivedAtAndDeviceLastSeenAt() {
        Map<?, ?> user = createUser("Ingest User", "ingest@example.com");
        Map<?, ?> device = createDevice((String) user.get("uuid"), null);
        UUID deviceUuid = UUID.fromString((String) device.get("uuid"));

        Measurement measurement = new Measurement();
        measurement.setDeviceUuid(deviceUuid);
        measurement.setTemperature(new BigDecimal("23.10"));
        measurement.setTemperatureUnit("CELSIUS");
        measurement.setHumidity(new BigDecimal("48.00"));
        measurement.setHumidityUnit("RELATIVE_PERCENT");
        measurement.setMeasuredAt(Instant.parse("2026-06-01T10:00:00Z"));

        Measurement saved = measurementService.record(measurement);

        assertThat(saved.getReceivedAt()).isNotNull();
        assertThat(devices.findById(deviceUuid)).hasValueSatisfying(savedDevice ->
                assertThat(Duration.between(savedDevice.getLastSeenAt(), saved.getReceivedAt()).abs())
                        .isLessThanOrEqualTo(Duration.ofMillis(1))
        );
    }

    @Test
    void validationErrorsAreReturned() {
        Map<?, ?> user = createUser("Validation User", "validation@example.com");
        Map<?, ?> device = createDevice((String) user.get("uuid"), null);

        ResponseEntity<Map> invalidUuid = rest.getForEntity("/api/v1/users/not-a-uuid", Map.class);
        assertThat(invalidUuid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> invalidInterval = rest.getForEntity(
                "/api/v1/devices/" + device.get("uuid") + "/measurements?from=2026-06-03T00:00:00Z&to=2026-06-02T00:00:00Z",
                Map.class
        );
        assertThat(invalidInterval.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> invalidTimeField = rest.getForEntity(
                "/api/v1/devices/" + device.get("uuid") + "/measurements?timeField=createdAt",
                Map.class
        );
        assertThat(invalidTimeField.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> invalidDeviceStatus = rest.exchange(
                "/api/v1/devices/" + device.get("uuid"),
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("status", "BROKEN", "name", "Invalid status")),
                Map.class
        );
        assertThat(invalidDeviceStatus.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> missingRequiredField = rest.postForEntity("/api/v1/users", Map.of(
                "name", "Missing Email"
        ), Map.class);
        assertThat(missingRequiredField.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> missingMeasurement = rest.getForEntity(
                "/api/v1/measurements/" + UUID.randomUUID(),
                Map.class
        );
        assertThat(missingMeasurement.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map> missingDeviceMeasurements = rest.getForEntity(
                "/api/v1/devices/" + UUID.randomUUID() + "/measurements",
                Map.class
        );
        assertThat(missingDeviceMeasurements.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map> invalidBucket = rest.getForEntity(
                "/api/v1/devices/" + device.get("uuid")
                        + "/measurements/overview?from=2026-06-01T00:00:00Z&to=2026-06-02T00:00:00Z&bucket=2m",
                Map.class
        );
        assertThat(invalidBucket.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private Map<?, ?> createUser(String name, String email) {
        ResponseEntity<Map> response = rest.postForEntity("/api/v1/users", Map.of(
                "name", name,
                "email", email
        ), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private Map<?, ?> createDevice(String userUuid, String environmentUuid) {
        return createDevice(userUuid, environmentUuid, UUID.randomUUID());
    }

    private Map<?, ?> createDevice(String userUuid, String environmentUuid, UUID hardwareUuid) {
        Map<String, Object> request = new HashMap<>();
        request.put("hardwareUuid", hardwareUuid.toString());
        request.put("userUuid", userUuid);
        request.put("environmentUuid", environmentUuid);
        request.put("name", "Sensor");

        ResponseEntity<Map> response = rest.postForEntity("/api/v1/devices", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private Measurement createMeasurement(UUID deviceUuid, Instant measuredAt, Instant receivedAt, String temperature) {
        return createMeasurement(deviceUuid, measuredAt, receivedAt, temperature, "55.00");
    }

    private Measurement createMeasurement(
            UUID deviceUuid,
            Instant measuredAt,
            Instant receivedAt,
            String temperature,
            String humidity
    ) {
        Measurement measurement = new Measurement();
        measurement.setDeviceUuid(deviceUuid);
        measurement.setTemperature(new BigDecimal(temperature));
        measurement.setTemperatureUnit("CELSIUS");
        measurement.setHumidity(new BigDecimal(humidity));
        measurement.setHumidityUnit("RELATIVE_PERCENT");
        measurement.setMeasuredAt(measuredAt);
        measurement.setReceivedAt(receivedAt);
        return measurements.saveAndFlush(measurement);
    }

    private Map<?, ?> findByDeviceUuid(List<?> items, UUID deviceUuid) {
        return items.stream()
                .map(item -> (Map<?, ?>) item)
                .filter(item -> deviceUuid.toString().equals(item.get("deviceUuid")))
                .findFirst()
                .orElseThrow();
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
