package com.sensorhub.api.web.dto;

import com.sensorhub.api.domain.Device;
import com.sensorhub.api.domain.DeviceStatus;
import com.sensorhub.api.domain.Measurement;
import com.sensorhub.api.web.dto.MeasurementDtos.LatestMeasurementResponse;
import java.time.Instant;
import java.util.UUID;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record DashboardDeviceResponse(
            UUID deviceUuid,
            UUID hardwareUuid,
            String name,
            UUID environmentUuid,
            String environmentName,
            DeviceStatus deviceStatus
    ) {
        public static DashboardDeviceResponse from(Device device, String environmentName) {
            return new DashboardDeviceResponse(
                    device.getUuid(),
                    device.getHardwareUuid(),
                    device.getName(),
                    device.getEnvironmentUuid(),
                    environmentName,
                    device.getStatus()
            );
        }
    }

    public record DashboardLatestMeasurementResponse(
            UUID deviceUuid,
            String freshnessStatus,
            Instant lastSeenAt,
            LatestMeasurementResponse latestMeasurement
    ) {
        public static DashboardLatestMeasurementResponse from(Device device, String freshnessStatus, Measurement latest) {
            return new DashboardLatestMeasurementResponse(
                    device.getUuid(),
                    freshnessStatus,
                    device.getLastSeenAt(),
                    LatestMeasurementResponse.from(latest)
            );
        }
    }
}
