package com.sensorhub.api.repository;

import com.sensorhub.api.domain.Measurement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeasurementRepository extends JpaRepository<Measurement, UUID> {

    Page<Measurement> findByDeviceUuid(UUID deviceUuid, Pageable pageable);

    Page<Measurement> findByDeviceUuidAndMeasuredAtGreaterThanEqual(UUID deviceUuid, Instant from, Pageable pageable);

    Page<Measurement> findByDeviceUuidAndMeasuredAtLessThanEqual(UUID deviceUuid, Instant to, Pageable pageable);

    Page<Measurement> findByDeviceUuidAndMeasuredAtBetween(UUID deviceUuid, Instant from, Instant to, Pageable pageable);

    Page<Measurement> findByDeviceUuidAndReceivedAtGreaterThanEqual(UUID deviceUuid, Instant from, Pageable pageable);

    Page<Measurement> findByDeviceUuidAndReceivedAtLessThanEqual(UUID deviceUuid, Instant to, Pageable pageable);

    Page<Measurement> findByDeviceUuidAndReceivedAtBetween(UUID deviceUuid, Instant from, Instant to, Pageable pageable);

    Optional<Measurement> findFirstByDeviceUuidOrderByMeasuredAtDesc(UUID deviceUuid);
}
