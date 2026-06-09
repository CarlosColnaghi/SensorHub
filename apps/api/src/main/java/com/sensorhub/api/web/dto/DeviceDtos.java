package com.sensorhub.api.web.dto;

import com.sensorhub.api.domain.Device;
import com.sensorhub.api.domain.DeviceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class DeviceDtos {

    private DeviceDtos() {
    }

    public record CreateDeviceRequest(
            @NotNull UUID hardwareUuid,
            @NotNull UUID userUuid,
            UUID environmentUuid,
            DeviceStatus status,
            @Size(max = 120) String name
    ) {
    }

    public record UpdateDeviceRequest(
            UUID environmentUuid,
            DeviceStatus status,
            @Size(max = 120) String name
    ) {
    }

    public record DeviceResponse(
            UUID uuid,
            UUID hardwareUuid,
            UUID userUuid,
            UUID environmentUuid,
            DeviceStatus status,
            String name,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static DeviceResponse from(Device device) {
            return new DeviceResponse(
                    device.getUuid(),
                    device.getHardwareUuid(),
                    device.getUserUuid(),
                    device.getEnvironmentUuid(),
                    device.getStatus(),
                    device.getName(),
                    device.getCreatedAt(),
                    device.getUpdatedAt()
            );
        }
    }
}
