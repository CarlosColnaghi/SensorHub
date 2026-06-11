package com.sensorhub.ingestor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceCacheTest {
    @Test
    void cachesDeviceUntilTtlExpires() throws SQLException {
        UUID hardwareUuid = UUID.fromString("b0fee3a6-ae91-4265-9365-36f793f32f06");
        UUID deviceUuid = UUID.fromString("fe0a2a2e-3222-45ef-91e5-e285ccbe70a2");
        FakeDeviceRepository repository = new FakeDeviceRepository(
                new DeviceRecord(deviceUuid, DeviceStatus.ACTIVATED)
        );
        MutableClock clock = new MutableClock(Instant.parse("2026-06-01T00:00:00Z"));
        DeviceCache cache = new DeviceCache(repository, Duration.ofSeconds(60), clock);

        DeviceRecord first = cache.resolve(hardwareUuid);
        DeviceRecord second = cache.resolve(hardwareUuid);
        clock.instant = Instant.parse("2026-06-01T00:01:01Z");
        DeviceRecord third = cache.resolve(hardwareUuid);

        assertEquals(deviceUuid, first.uuid());
        assertEquals(deviceUuid, second.uuid());
        assertEquals(deviceUuid, third.uuid());
        assertEquals(2, repository.calls);
    }

    @Test
    void doesNotCacheMissingDevice() throws SQLException {
        UUID hardwareUuid = UUID.fromString("b0fee3a6-ae91-4265-9365-36f793f32f06");
        FakeDeviceRepository repository = new FakeDeviceRepository(null);
        DeviceCache cache = new DeviceCache(
                repository,
                Duration.ofSeconds(60),
                Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC)
        );

        assertNull(cache.resolve(hardwareUuid));
        assertNull(cache.resolve(hardwareUuid));
        assertEquals(2, repository.calls);
    }

    private static final class FakeDeviceRepository implements DeviceRepository {
        private final DeviceRecord device;
        private int calls;

        private FakeDeviceRepository(DeviceRecord device) {
            this.device = device;
        }

        @Override
        public DeviceRecord findByHardwareUuid(UUID hardwareUuid) {
            calls++;
            return device;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
