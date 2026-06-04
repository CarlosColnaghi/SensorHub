package com.sensorhub.api.web;

import com.sensorhub.api.service.DeviceService;
import com.sensorhub.api.web.dto.DeviceDtos.CreateDeviceRequest;
import com.sensorhub.api.web.dto.DeviceDtos.DeviceResponse;
import com.sensorhub.api.web.dto.DeviceDtos.UpdateDeviceRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService devices;

    public DeviceController(DeviceService devices) {
        this.devices = devices;
    }

    @PostMapping
    public ResponseEntity<DeviceResponse> create(@Valid @RequestBody CreateDeviceRequest request) {
        DeviceResponse response = DeviceResponse.from(devices.create(request));
        return ResponseEntity.created(URI.create("/api/v1/devices/" + response.uuid())).body(response);
    }

    @GetMapping
    public List<DeviceResponse> list() {
        return devices.list().stream().map(DeviceResponse::from).toList();
    }

    @GetMapping("/{uuid}")
    public DeviceResponse get(@PathVariable UUID uuid) {
        return DeviceResponse.from(devices.get(uuid));
    }

    @PutMapping("/{uuid}")
    public DeviceResponse update(@PathVariable UUID uuid, @Valid @RequestBody UpdateDeviceRequest request) {
        return DeviceResponse.from(devices.update(uuid, request));
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
        devices.delete(uuid);
        return ResponseEntity.noContent().build();
    }
}
