package com.sensorhub.api.web;

import com.sensorhub.api.domain.DeviceStatus;
import com.sensorhub.api.service.DashboardService;
import com.sensorhub.api.web.dto.DashboardDtos.DashboardDeviceResponse;
import com.sensorhub.api.web.dto.DashboardDtos.DashboardLatestMeasurementResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/{userUuid}/dashboard")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/devices")
    public List<DashboardDeviceResponse> devices(
            @PathVariable UUID userUuid,
            @RequestParam(required = false) UUID environmentUuid,
            @RequestParam(required = false) DeviceStatus status
    ) {
        return dashboard.dashboardDevices(userUuid, environmentUuid, status);
    }

    @GetMapping("/measurements/latest")
    public List<DashboardLatestMeasurementResponse> latestMeasurements(
            @PathVariable UUID userUuid,
            @RequestParam(required = false) UUID environmentUuid,
            @RequestParam(required = false) DeviceStatus deviceStatus
    ) {
        return dashboard.latestMeasurements(userUuid, environmentUuid, deviceStatus);
    }
}
