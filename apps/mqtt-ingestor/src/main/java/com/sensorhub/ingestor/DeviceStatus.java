package com.sensorhub.ingestor;

enum DeviceStatus {
    ACTIVATED,
    INACTIVATED;

    static DeviceStatus fromDatabase(String value) {
        try {
            return DeviceStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("unknown device status from database: " + value, exception);
        }
    }
}
