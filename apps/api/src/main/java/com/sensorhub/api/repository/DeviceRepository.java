package com.sensorhub.api.repository;

import com.sensorhub.api.domain.Device;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    boolean existsByHardwareUuid(UUID hardwareUuid);

    Optional<Device> findByHardwareUuid(UUID hardwareUuid);
}
