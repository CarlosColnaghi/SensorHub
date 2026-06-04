package com.sensorhub.api.service;

public enum TimeField {
    MEASURED_AT,
    RECEIVED_AT;

    public static TimeField parse(String value) {
        if (value == null || value.isBlank() || "measuredAt".equals(value)) {
            return MEASURED_AT;
        }
        if ("receivedAt".equals(value)) {
            return RECEIVED_AT;
        }
        throw new InvalidRequestException("timeField must be measuredAt or receivedAt");
    }
}
