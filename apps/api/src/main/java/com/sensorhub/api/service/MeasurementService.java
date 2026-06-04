package com.sensorhub.api.service;

import com.sensorhub.api.domain.Measurement;
import com.sensorhub.api.domain.Device;
import com.sensorhub.api.repository.DeviceRepository;
import com.sensorhub.api.repository.MeasurementRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeasurementService {

    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 500;

    private final MeasurementRepository measurements;
    private final DeviceRepository devices;

    public MeasurementService(MeasurementRepository measurements, DeviceRepository devices) {
        this.measurements = measurements;
        this.devices = devices;
    }

    @Transactional(readOnly = true)
    public Page<Measurement> list(int page, int size) {
        return measurements.findAll(pageable(page, size, "measuredAt"));
    }

    @Transactional(readOnly = true)
    public Measurement get(UUID uuid) {
        return measurements.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("measurement not found"));
    }

    @Transactional(readOnly = true)
    public Page<Measurement> listByDevice(UUID deviceUuid, Instant from, Instant to, String timeField, int page, int size) {
        if (!devices.existsById(deviceUuid)) {
            throw new ResourceNotFoundException("device not found");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("from must be before or equal to to");
        }

        TimeField parsedTimeField = TimeField.parse(timeField);
        Pageable pageable = pageable(page, size, parsedTimeField == TimeField.MEASURED_AT ? "measuredAt" : "receivedAt");

        if (parsedTimeField == TimeField.RECEIVED_AT) {
            return byReceivedAt(deviceUuid, from, to, pageable);
        }
        return byMeasuredAt(deviceUuid, from, to, pageable);
    }

    @Transactional(readOnly = true)
    public Measurement latestByDevice(UUID deviceUuid) {
        if (!devices.existsById(deviceUuid)) {
            throw new ResourceNotFoundException("device not found");
        }
        return measurements.findFirstByDeviceUuidOrderByMeasuredAtDesc(deviceUuid).orElse(null);
    }

    @Transactional
    public Measurement record(Measurement measurement) {
        Device device = devices.findById(measurement.getDeviceUuid())
                .orElseThrow(() -> new ResourceNotFoundException("device not found"));
        measurement.setReceivedAt(Instant.now());
        Measurement saved = measurements.save(measurement);
        device.setLastSeenAt(saved.getReceivedAt());
        return saved;
    }

    private Page<Measurement> byMeasuredAt(UUID deviceUuid, Instant from, Instant to, Pageable pageable) {
        if (from != null && to != null) {
            return measurements.findByDeviceUuidAndMeasuredAtBetween(deviceUuid, from, to, pageable);
        }
        if (from != null) {
            return measurements.findByDeviceUuidAndMeasuredAtGreaterThanEqual(deviceUuid, from, pageable);
        }
        if (to != null) {
            return measurements.findByDeviceUuidAndMeasuredAtLessThanEqual(deviceUuid, to, pageable);
        }
        return measurements.findByDeviceUuid(deviceUuid, pageable);
    }

    private Page<Measurement> byReceivedAt(UUID deviceUuid, Instant from, Instant to, Pageable pageable) {
        if (from != null && to != null) {
            return measurements.findByDeviceUuidAndReceivedAtBetween(deviceUuid, from, to, pageable);
        }
        if (from != null) {
            return measurements.findByDeviceUuidAndReceivedAtGreaterThanEqual(deviceUuid, from, pageable);
        }
        if (to != null) {
            return measurements.findByDeviceUuidAndReceivedAtLessThanEqual(deviceUuid, to, pageable);
        }
        return measurements.findByDeviceUuid(deviceUuid, pageable);
    }

    private Pageable pageable(int page, int size, String sortField) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, sortField));
    }
}
