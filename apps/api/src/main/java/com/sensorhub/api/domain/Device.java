package com.sensorhub.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;

@Entity
@Table(name = "devices")
public class Device {

    @Id
    @Generated
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @Column(name = "user_uuid", nullable = false)
    private UUID userUuid;

    @Column(name = "hardware_uuid", nullable = false, unique = true)
    private UUID hardwareUuid;

    @Column(name = "name", length = 120)
    private String name;

    @Column(name = "environment_uuid")
    private UUID environmentUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DeviceStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getUuid() {
        return uuid;
    }

    public UUID getUserUuid() {
        return userUuid;
    }

    public void setUserUuid(UUID userUuid) {
        this.userUuid = userUuid;
    }

    public UUID getHardwareUuid() {
        return hardwareUuid;
    }

    public void setHardwareUuid(UUID hardwareUuid) {
        this.hardwareUuid = hardwareUuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getEnvironmentUuid() {
        return environmentUuid;
    }

    public void setEnvironmentUuid(UUID environmentUuid) {
        this.environmentUuid = environmentUuid;
    }

    public DeviceStatus getStatus() {
        return status;
    }

    public void setStatus(DeviceStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (status == null) {
            status = DeviceStatus.ACTIVATED;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
