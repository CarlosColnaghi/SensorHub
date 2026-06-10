package com.sensorhub.ingestor;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class DeviceCache {
    private final DeviceRepository repository;
    private final Duration ttl;
    private final Clock clock;
    private final Map<UUID, CacheEntry> entries = new HashMap<>();

    DeviceCache(DeviceRepository repository, Duration ttl, Clock clock) {
        this.repository = repository;
        this.ttl = ttl;
        this.clock = clock;
    }

    DeviceRecord resolve(UUID hardwareUuid) throws SQLException {
        Instant now = clock.instant();
        CacheEntry existing = entries.get(hardwareUuid);
        if (existing != null && existing.expiresAt().isAfter(now)) {
            return existing.device();
        }

        DeviceRecord device = repository.findByHardwareUuid(hardwareUuid);
        if (device != null) {
            entries.put(hardwareUuid, new CacheEntry(device, now.plus(ttl)));
        } else {
            entries.remove(hardwareUuid);
        }
        return device;
    }

    private record CacheEntry(DeviceRecord device, Instant expiresAt) {
    }
}
