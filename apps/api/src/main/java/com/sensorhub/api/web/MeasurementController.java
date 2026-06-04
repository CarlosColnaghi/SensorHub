package com.sensorhub.api.web;

import com.sensorhub.api.domain.Measurement;
import com.sensorhub.api.service.MeasurementService;
import com.sensorhub.api.web.dto.MeasurementDtos.MeasurementOverviewResponse;
import com.sensorhub.api.web.dto.MeasurementDtos.MeasurementResponse;
import com.sensorhub.api.web.dto.MeasurementDtos.PageResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MeasurementController {

    private final MeasurementService measurements;

    public MeasurementController(MeasurementService measurements) {
        this.measurements = measurements;
    }

    @GetMapping("/measurements")
    public PageResponse<MeasurementResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Page<MeasurementResponse> response = measurements.list(page, size).map(MeasurementResponse::from);
        return PageResponse.from(response);
    }

    @GetMapping("/measurements/{uuid}")
    public MeasurementResponse get(@PathVariable UUID uuid) {
        return MeasurementResponse.from(measurements.get(uuid));
    }

    @GetMapping("/devices/{deviceUuid}/measurements")
    public PageResponse<MeasurementResponse> listByDevice(
            @PathVariable UUID deviceUuid,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "measuredAt") String timeField,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Page<MeasurementResponse> response = measurements
                .listByDevice(deviceUuid, from, to, timeField, page, size)
                .map(MeasurementResponse::from);
        return PageResponse.from(response);
    }

    @GetMapping("/devices/{deviceUuid}/measurements/latest")
    public ResponseEntity<MeasurementResponse> latestByDevice(@PathVariable UUID deviceUuid) {
        Measurement latest = measurements.latestByDevice(deviceUuid);
        if (latest == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(MeasurementResponse.from(latest));
    }

    @GetMapping("/devices/{deviceUuid}/measurements/overview")
    public MeasurementOverviewResponse overviewByDevice(
            @PathVariable UUID deviceUuid,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "raw") String bucket
    ) {
        return measurements.overviewByDevice(deviceUuid, from, to, bucket);
    }
}
