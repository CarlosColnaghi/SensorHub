package com.sensorhub.api.web.dto;

import com.sensorhub.api.domain.AppUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record CreateUserRequest(
            @NotBlank String name,
            @NotBlank @Email String email
    ) {
    }

    public record UpdateUserRequest(
            @NotBlank String name,
            @NotBlank @Email String email
    ) {
    }

    public record UserResponse(
            UUID uuid,
            String name,
            String email,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static UserResponse from(AppUser user) {
            return new UserResponse(
                    user.getUuid(),
                    user.getName(),
                    user.getEmail(),
                    user.getCreatedAt(),
                    user.getUpdatedAt()
            );
        }
    }
}
