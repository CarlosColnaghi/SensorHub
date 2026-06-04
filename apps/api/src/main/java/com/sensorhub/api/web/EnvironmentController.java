package com.sensorhub.api.web;

import com.sensorhub.api.service.EnvironmentService;
import com.sensorhub.api.web.dto.EnvironmentDtos.CreateEnvironmentRequest;
import com.sensorhub.api.web.dto.EnvironmentDtos.EnvironmentResponse;
import com.sensorhub.api.web.dto.EnvironmentDtos.UpdateEnvironmentRequest;
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
@RequestMapping("/api/v1/environments")
public class EnvironmentController {

    private final EnvironmentService environments;

    public EnvironmentController(EnvironmentService environments) {
        this.environments = environments;
    }

    @PostMapping
    public ResponseEntity<EnvironmentResponse> create(@Valid @RequestBody CreateEnvironmentRequest request) {
        EnvironmentResponse response = EnvironmentResponse.from(environments.create(request));
        return ResponseEntity.created(URI.create("/api/v1/environments/" + response.uuid())).body(response);
    }

    @GetMapping
    public List<EnvironmentResponse> list() {
        return environments.list().stream().map(EnvironmentResponse::from).toList();
    }

    @GetMapping("/{uuid}")
    public EnvironmentResponse get(@PathVariable UUID uuid) {
        return EnvironmentResponse.from(environments.get(uuid));
    }

    @PutMapping("/{uuid}")
    public EnvironmentResponse update(@PathVariable UUID uuid, @Valid @RequestBody UpdateEnvironmentRequest request) {
        return EnvironmentResponse.from(environments.update(uuid, request));
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
        environments.delete(uuid);
        return ResponseEntity.noContent().build();
    }
}
