package com.sensorhub.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sensorhub.api.domain.AppUser;
import com.sensorhub.api.domain.Device;
import com.sensorhub.api.domain.SensorEnvironment;
import com.sensorhub.api.repository.DeviceRepository;
import com.sensorhub.api.repository.EnvironmentRepository;
import com.sensorhub.api.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class SchemaMigrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserRepository users;

    @Autowired
    EnvironmentRepository environments;

    @Autowired
    DeviceRepository devices;

    @Test
    void flywayCreatesInitialTables() {
        Integer tableCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('users', 'environments', 'devices', 'measurements')
                """, Integer.class);

        assertThat(tableCount).isEqualTo(4);
    }

    @Test
    void databaseGeneratesUuidPrimaryKeys() {
        AppUser user = new AppUser();
        user.setName("Ada Lovelace");
        user.setEmail("ada@example.com");

        AppUser saved = users.saveAndFlush(user);

        assertThat(saved.getUuid()).isNotNull();
    }

    @Test
    void primaryKeysUseUuidColumns() {
        Integer uuidPrimaryKeyCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns c
                JOIN information_schema.table_constraints tc
                  ON tc.table_schema = c.table_schema
                 AND tc.table_name = c.table_name
                JOIN information_schema.key_column_usage kcu
                  ON kcu.constraint_schema = tc.constraint_schema
                 AND kcu.constraint_name = tc.constraint_name
                 AND kcu.table_name = tc.table_name
                 AND kcu.column_name = c.column_name
                WHERE c.table_schema = 'public'
                  AND c.table_name IN ('users', 'environments', 'devices', 'measurements')
                  AND c.column_name = 'uuid'
                  AND c.udt_name = 'uuid'
                  AND tc.constraint_type = 'PRIMARY KEY'
                """, Integer.class);

        assertThat(uuidPrimaryKeyCount).isEqualTo(4);
    }

    @Test
    void foreignKeysPreventOrphanRecords() {
        UUID missingUserUuid = UUID.randomUUID();
        UUID missingDeviceUuid = UUID.randomUUID();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO environments (user_uuid, name) VALUES (?, ?)",
                missingUserUuid,
                "Orphan environment"
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO devices (user_uuid, hardware_uuid, name) VALUES (?, ?, ?)",
                missingUserUuid,
                UUID.randomUUID(),
                "Orphan device"
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO measurements (
                    device_uuid,
                    temperature,
                    temperature_unit,
                    humidity,
                    humidity_unit,
                    measured_at
                ) VALUES (?, 21.20, 'CELSIUS', 50.00, 'RELATIVE_PERCENT', now())
                """, missingDeviceUuid)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void flywaySeedsAdminUserAndInitialDevice() {
        UUID adminUuid = jdbc.queryForObject("""
                SELECT uuid
                FROM users
                WHERE email = 'admin@sensorhub.com'
                  AND name = 'admin'
                """, UUID.class);

        UUID deviceUserUuid = jdbc.queryForObject("""
                SELECT user_uuid
                FROM devices
                WHERE hardware_uuid = 'b0fee3a6-ae91-4265-9365-36f793f32f06'::uuid
                  AND name = 'Admin seed sensor'
                  AND environment_uuid IS NULL
                """, UUID.class);

        assertThat(adminUuid).isNotNull();
        assertThat(deviceUserUuid).isEqualTo(adminUuid);
    }

    @Test
    void userEmailAndDeviceHardwareUuidAreUnique() {
        AppUser user = createUser("Grace Hopper", "grace@example.com");
        UUID hardwareUuid = UUID.randomUUID();
        createDevice(user.getUuid(), hardwareUuid, null);

        AppUser duplicateUser = new AppUser();
        duplicateUser.setName("Grace Duplicate");
        duplicateUser.setEmail("grace@example.com");

        assertThatThrownBy(() -> users.saveAndFlush(duplicateUser))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> createDevice(user.getUuid(), hardwareUuid, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deviceEnvironmentMustBelongToSameUser() {
        AppUser firstUser = createUser("First User", "first@example.com");
        AppUser secondUser = createUser("Second User", "second@example.com");
        SensorEnvironment secondUserEnvironment = createEnvironment(secondUser.getUuid(), "Lab");

        assertThatThrownBy(() -> createDevice(firstUser.getUuid(), UUID.randomUUID(), secondUserEnvironment.getUuid()))
                .isInstanceOf(DataIntegrityViolationException.class);
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
}
