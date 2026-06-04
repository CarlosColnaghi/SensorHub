package com.sensorhub.api.service;

import com.sensorhub.api.domain.Device;
import com.sensorhub.api.domain.DeviceStatus;
import com.sensorhub.api.domain.SensorEnvironment;
import com.sensorhub.api.repository.DeviceRepository;
import com.sensorhub.api.repository.EnvironmentRepository;
import com.sensorhub.api.repository.UserRepository;
import com.sensorhub.api.web.dto.DeviceDtos.CreateDeviceRequest;
import com.sensorhub.api.web.dto.DeviceDtos.UpdateDeviceRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

    private final DeviceRepository devices;
    private final UserRepository users;
    private final EnvironmentRepository environments;

    public DeviceService(DeviceRepository devices, UserRepository users, EnvironmentRepository environments) {
        this.devices = devices;
        this.users = users;
        this.environments = environments;
    }

    @Transactional
    public Device create(CreateDeviceRequest request) {
        if (!users.existsById(request.userUuid())) {
            throw new ResourceNotFoundException("user not found");
        }
        if (devices.existsByHardwareUuid(request.hardwareUuid())) {
            throw new ConflictException("hardwareUuid already exists");
        }
        validateEnvironmentOwnership(request.environmentUuid(), request.userUuid());

        Device device = new Device();
        device.setHardwareUuid(request.hardwareUuid());
        device.setUserUuid(request.userUuid());
        device.setEnvironmentUuid(request.environmentUuid());
        device.setStatus(request.status() == null ? DeviceStatus.ACTIVATED : request.status());
        device.setName(request.name());
        return devices.save(device);
    }

    @Transactional(readOnly = true)
    public List<Device> list() {
        return devices.findAll();
    }

    @Transactional(readOnly = true)
    public Device get(UUID uuid) {
        return devices.findById(uuid).orElseThrow(() -> new ResourceNotFoundException("device not found"));
    }

    @Transactional
    public Device update(UUID uuid, UpdateDeviceRequest request) {
        Device device = get(uuid);
        validateEnvironmentOwnership(request.environmentUuid(), device.getUserUuid());
        device.setEnvironmentUuid(request.environmentUuid());
        if (request.status() != null) {
            device.setStatus(request.status());
        }
        device.setName(request.name());
        return device;
    }

    @Transactional
    public void delete(UUID uuid) {
        if (!devices.existsById(uuid)) {
            throw new ResourceNotFoundException("device not found");
        }
        devices.deleteById(uuid);
    }

    private void validateEnvironmentOwnership(UUID environmentUuid, UUID userUuid) {
        if (environmentUuid == null) {
            return;
        }
        SensorEnvironment environment = environments.findById(environmentUuid)
                .orElseThrow(() -> new ResourceNotFoundException("environment not found"));
        if (!environment.getUserUuid().equals(userUuid)) {
            throw new InvalidRequestException("device and environment must belong to the same user");
        }
    }
}
