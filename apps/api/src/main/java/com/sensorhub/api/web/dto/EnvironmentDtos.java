package com.sensorhub.api.web.dto;

import com.sensorhub.api.domain.SensorEnvironment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class EnvironmentDtos {

    private EnvironmentDtos() {
    }

    public record CreateEnvironmentRequest(
            @NotNull UUID userUuid,
            @NotBlank String name
    ) {
    }

    public record UpdateEnvironmentRequest(
            @NotBlank String name
    ) {
    }

    public record EnvironmentResponse(
            UUID uuid,
            UUID userUuid,
            String name,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static EnvironmentResponse from(SensorEnvironment environment) {
            return new EnvironmentResponse(
                    environment.getUuid(),
                    environment.getUserUuid(),
                    environment.getName(),
                    environment.getCreatedAt(),
                    environment.getUpdatedAt()
            );
        }
    }
}
