package com.sensorhub.api.service;

import com.sensorhub.api.domain.Device;
import com.sensorhub.api.domain.DeviceStatus;
import com.sensorhub.api.domain.Measurement;
import com.sensorhub.api.domain.SensorEnvironment;
import com.sensorhub.api.repository.DeviceRepository;
import com.sensorhub.api.repository.EnvironmentRepository;
import com.sensorhub.api.repository.MeasurementRepository;
import com.sensorhub.api.repository.UserRepository;
import com.sensorhub.api.web.dto.DashboardDtos.DashboardDeviceResponse;
import com.sensorhub.api.web.dto.DashboardDtos.DashboardLatestMeasurementResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final UserRepository users;
    private final DeviceRepository devices;
    private final EnvironmentRepository environments;
    private final MeasurementRepository measurements;
    private final FreshnessStatusService freshness;

    public DashboardService(
            UserRepository users,
            DeviceRepository devices,
            EnvironmentRepository environments,
            MeasurementRepository measurements,
            FreshnessStatusService freshness
    ) {
        this.users = users;
        this.devices = devices;
        this.environments = environments;
        this.measurements = measurements;
        this.freshness = freshness;
    }

    @Transactional(readOnly = true)
    public List<DashboardDeviceResponse> dashboardDevices(UUID userUuid, UUID environmentUuid, DeviceStatus status) {
        List<Device> filteredDevices = filteredDevices(userUuid, environmentUuid, status);
        Map<UUID, SensorEnvironment> environmentsByUuid = environments.findAllById(
                        filteredDevices.stream()
                                .map(Device::getEnvironmentUuid)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet())
                )
                .stream()
                .collect(Collectors.toMap(SensorEnvironment::getUuid, Function.identity()));

        return filteredDevices.stream()
                .map(device -> DashboardDeviceResponse.from(
                        device,
                        environmentName(device.getEnvironmentUuid(), environmentsByUuid)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DashboardLatestMeasurementResponse> latestMeasurements(
            UUID userUuid,
            UUID environmentUuid,
            DeviceStatus deviceStatus
    ) {
        return filteredDevices(userUuid, environmentUuid, deviceStatus).stream()
                .map(device -> {
                    Measurement latest = measurements.findFirstByDeviceUuidOrderByMeasuredAtDesc(device.getUuid())
                            .orElse(null);
                    Measurement latestCommunication = measurements
                            .findFirstByDeviceUuidOrderByReceivedAtDesc(device.getUuid())
                            .orElse(null);
                    return DashboardLatestMeasurementResponse.from(
                            device,
                            freshness.status(device, latestCommunication).name(),
                            lastSeenAt(latestCommunication),
                            latest
                    );
                })
                .toList();
    }

    private List<Device> filteredDevices(UUID userUuid, UUID environmentUuid, DeviceStatus status) {
        if (!users.existsById(userUuid)) {
            throw new ResourceNotFoundException("user not found");
        }

        return devices.findByUserUuid(userUuid).stream()
                .filter(device -> environmentUuid == null || environmentUuid.equals(device.getEnvironmentUuid()))
                .filter(device -> status == null || status == device.getStatus())
                .toList();
    }

    private String environmentName(UUID environmentUuid, Map<UUID, SensorEnvironment> environmentsByUuid) {
        if (environmentUuid == null) {
            return null;
        }
        SensorEnvironment environment = environmentsByUuid.get(environmentUuid);
        return environment == null ? null : environment.getName();
    }

    private Instant lastSeenAt(Measurement latestCommunication) {
        return latestCommunication == null ? null : latestCommunication.getReceivedAt();
    }
}
