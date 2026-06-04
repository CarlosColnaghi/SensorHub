CREATE TABLE users (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    email VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE environments (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_uuid UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_environments_user FOREIGN KEY (user_uuid) REFERENCES users (uuid) ON DELETE CASCADE,
    CONSTRAINT uk_environments_uuid_user UNIQUE (uuid, user_uuid)
);

CREATE TABLE devices (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_uuid UUID NOT NULL,
    hardware_uuid UUID NOT NULL,
    name VARCHAR(120),
    environment_uuid UUID,
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_devices_hardware_uuid UNIQUE (hardware_uuid),
    CONSTRAINT fk_devices_user FOREIGN KEY (user_uuid) REFERENCES users (uuid) ON DELETE CASCADE,
    CONSTRAINT fk_devices_environment FOREIGN KEY (environment_uuid) REFERENCES environments (uuid) ON DELETE SET NULL,
    CONSTRAINT fk_devices_environment_same_user FOREIGN KEY (environment_uuid, user_uuid) REFERENCES environments (uuid, user_uuid) ON DELETE SET NULL (environment_uuid)
);

CREATE TABLE measurements (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_uuid UUID NOT NULL,
    temperature NUMERIC(6, 2) NOT NULL,
    temperature_unit VARCHAR(32) NOT NULL,
    humidity NUMERIC(6, 2) NOT NULL,
    humidity_unit VARCHAR(32) NOT NULL,
    measured_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_measurements_device FOREIGN KEY (device_uuid) REFERENCES devices (uuid) ON DELETE CASCADE
);

CREATE INDEX idx_environments_user_uuid ON environments (user_uuid);
CREATE INDEX idx_devices_user_uuid ON devices (user_uuid);
CREATE INDEX idx_devices_environment_uuid ON devices (environment_uuid);
CREATE INDEX idx_measurements_device_measured_at ON measurements (device_uuid, measured_at DESC);
CREATE INDEX idx_measurements_device_received_at ON measurements (device_uuid, received_at DESC);
