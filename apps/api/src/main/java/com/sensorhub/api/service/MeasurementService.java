package com.sensorhub.api.service;

import com.sensorhub.api.domain.Device;
import com.sensorhub.api.domain.DeviceStatus;
import com.sensorhub.api.domain.Measurement;
import com.sensorhub.api.repository.DeviceRepository;
import com.sensorhub.api.repository.MeasurementRepository;
import com.sensorhub.api.web.dto.MeasurementDtos.LatestMeasurementResponse;
import com.sensorhub.api.web.dto.MeasurementDtos.MeasurementOverviewResponse;
import com.sensorhub.api.web.dto.MeasurementDtos.OverviewStatsResponse;
import com.sensorhub.api.web.dto.MeasurementDtos.PeriodResponse;
import com.sensorhub.api.web.dto.MeasurementDtos.SeriesPointResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final FreshnessStatusService freshness;

    public MeasurementService(
            MeasurementRepository measurements,
            DeviceRepository devices,
            FreshnessStatusService freshness
    ) {
        this.measurements = measurements;
        this.devices = devices;
        this.freshness = freshness;
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

    @Transactional(readOnly = true)
    public MeasurementOverviewResponse overviewByDevice(UUID deviceUuid, Instant from, Instant to, String bucket) {
        Device device = devices.findById(deviceUuid)
                .orElseThrow(() -> new ResourceNotFoundException("device not found"));
        if (from.isAfter(to)) {
            throw new InvalidRequestException("from must be before or equal to to");
        }

        Bucket parsedBucket = Bucket.parse(bucket);
        Measurement latest = measurements.findFirstByDeviceUuidOrderByMeasuredAtDesc(deviceUuid).orElse(null);
        Measurement latestCommunication = measurements.findFirstByDeviceUuidOrderByReceivedAtDesc(deviceUuid).orElse(null);
        List<Measurement> periodMeasurements = measurements
                .findByDeviceUuidAndMeasuredAtBetweenOrderByMeasuredAtAsc(deviceUuid, from, to);

        return new MeasurementOverviewResponse(
                deviceUuid,
                freshness.status(device, latestCommunication).name(),
                lastSeenAt(latestCommunication),
                new PeriodResponse(from, to, parsedBucket.value),
                LatestMeasurementResponse.from(latest),
                series(periodMeasurements, parsedBucket),
                overview(periodMeasurements)
        );
    }

    @Transactional
    public Measurement record(Measurement measurement) {
        Device device = devices.findById(measurement.getDeviceUuid())
                .orElseThrow(() -> new ResourceNotFoundException("device not found"));
        if (device.getStatus() == DeviceStatus.INACTIVATED) {
            throw new InvalidRequestException("device is inactivated");
        }
        measurement.setReceivedAt(Instant.now());
        return measurements.save(measurement);
    }

    private Instant lastSeenAt(Measurement latestCommunication) {
        return latestCommunication == null ? null : latestCommunication.getReceivedAt();
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

    private List<SeriesPointResponse> series(List<Measurement> periodMeasurements, Bucket bucket) {
        if (bucket.duration == null) {
            return periodMeasurements.stream()
                    .map(measurement -> new SeriesPointResponse(
                            measurement.getMeasuredAt(),
                            measurement.getTemperature(),
                            measurement.getHumidity()
                    ))
                    .toList();
        }

        Map<Instant, BucketAccumulator> buckets = new LinkedHashMap<>();
        for (Measurement measurement : periodMeasurements) {
            Instant timestamp = bucketStart(measurement.getMeasuredAt(), bucket.duration);
            buckets.computeIfAbsent(timestamp, ignored -> new BucketAccumulator())
                    .add(measurement);
        }

        return buckets.entrySet().stream()
                .map(entry -> new SeriesPointResponse(
                        entry.getKey(),
                        entry.getValue().averageTemperature(),
                        entry.getValue().averageHumidity()
                ))
                .toList();
    }

    private OverviewStatsResponse overview(List<Measurement> periodMeasurements) {
        if (periodMeasurements.isEmpty()) {
            return null;
        }

        Measurement temperatureMax = periodMeasurements.get(0);
        Measurement temperatureMin = periodMeasurements.get(0);
        Measurement humidityMax = periodMeasurements.get(0);
        Measurement humidityMin = periodMeasurements.get(0);
        BigDecimal temperatureSum = BigDecimal.ZERO;
        BigDecimal humiditySum = BigDecimal.ZERO;

        for (Measurement measurement : periodMeasurements) {
            if (measurement.getTemperature().compareTo(temperatureMax.getTemperature()) > 0) {
                temperatureMax = measurement;
            }
            if (measurement.getTemperature().compareTo(temperatureMin.getTemperature()) < 0) {
                temperatureMin = measurement;
            }
            if (measurement.getHumidity().compareTo(humidityMax.getHumidity()) > 0) {
                humidityMax = measurement;
            }
            if (measurement.getHumidity().compareTo(humidityMin.getHumidity()) < 0) {
                humidityMin = measurement;
            }
            temperatureSum = temperatureSum.add(measurement.getTemperature());
            humiditySum = humiditySum.add(measurement.getHumidity());
        }

        BigDecimal count = BigDecimal.valueOf(periodMeasurements.size());
        return new OverviewStatsResponse(
                temperatureMax.getTemperature(),
                temperatureMax.getMeasuredAt(),
                temperatureMin.getTemperature(),
                temperatureMin.getMeasuredAt(),
                temperatureSum.divide(count, 2, RoundingMode.HALF_UP),
                humidityMax.getHumidity(),
                humidityMax.getMeasuredAt(),
                humidityMin.getHumidity(),
                humidityMin.getMeasuredAt(),
                humiditySum.divide(count, 2, RoundingMode.HALF_UP),
                periodMeasurements.size()
        );
    }

    private Instant bucketStart(Instant instant, Duration bucketDuration) {
        long bucketMillis = bucketDuration.toMillis();
        long epochMillis = instant.toEpochMilli();
        return Instant.ofEpochMilli(Math.floorDiv(epochMillis, bucketMillis) * bucketMillis);
    }

    private enum Bucket {
        RAW("raw", null),
        ONE_MINUTE("1m", Duration.ofMinutes(1)),
        FIVE_MINUTES("5m", Duration.ofMinutes(5)),
        ONE_HOUR("1h", Duration.ofHours(1)),
        ONE_DAY("1d", Duration.ofDays(1));

        private final String value;
        private final Duration duration;

        Bucket(String value, Duration duration) {
            this.value = value;
            this.duration = duration;
        }

        static Bucket parse(String value) {
            for (Bucket bucket : values()) {
                if (bucket.value.equals(value)) {
                    return bucket;
                }
            }
            throw new InvalidRequestException("invalid bucket");
        }
    }

    private static final class BucketAccumulator {
        private BigDecimal temperatureSum = BigDecimal.ZERO;
        private BigDecimal humiditySum = BigDecimal.ZERO;
        private int count;

        void add(Measurement measurement) {
            temperatureSum = temperatureSum.add(measurement.getTemperature());
            humiditySum = humiditySum.add(measurement.getHumidity());
            count++;
        }

        BigDecimal averageTemperature() {
            return temperatureSum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        }

        BigDecimal averageHumidity() {
            return humiditySum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        }
    }
}
