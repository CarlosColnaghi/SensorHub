package com.sensorhub.api.service;

import com.sensorhub.api.domain.Device;
import com.sensorhub.api.domain.DeviceStatus;
import com.sensorhub.api.domain.Measurement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class FreshnessStatusService {

    private static final Duration STALE_AFTER = Duration.ofMinutes(5);

    private final Clock clock;

    public FreshnessStatusService(Clock clock) {
        this.clock = clock;
    }

    public FreshnessStatus status(Device device, Measurement latestMeasurement) {
        if (device.getStatus() == DeviceStatus.INACTIVATED) {
            return FreshnessStatus.INACTIVATED;
        }
        if (latestMeasurement == null) {
            return FreshnessStatus.NO_DATA;
        }

        Instant reference = device.getLastSeenAt() != null ? device.getLastSeenAt() : latestMeasurement.getMeasuredAt();
        if (reference.plus(STALE_AFTER).isBefore(Instant.now(clock))) {
            return FreshnessStatus.STALE;
        }
        return FreshnessStatus.ONLINE;
    }
}
