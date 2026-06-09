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

    private static final Duration OFFLINE_AFTER = Duration.ofMinutes(1);

    private final Clock clock;

    public FreshnessStatusService(Clock clock) {
        this.clock = clock;
    }

    public FreshnessStatus status(Device device, Measurement latestCommunication) {
        if (device.getStatus() == DeviceStatus.INACTIVATED) {
            return FreshnessStatus.INACTIVATED;
        }
        if (latestCommunication == null) {
            return FreshnessStatus.NO_DATA;
        }

        Instant reference = latestCommunication.getReceivedAt();
        if (reference.plus(OFFLINE_AFTER).isBefore(Instant.now(clock))) {
            return FreshnessStatus.OFFLINE;
        }
        return FreshnessStatus.ONLINE;
    }
}
