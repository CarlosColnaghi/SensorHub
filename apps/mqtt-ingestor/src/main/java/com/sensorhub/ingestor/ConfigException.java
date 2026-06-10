package com.sensorhub.ingestor;

final class ConfigException extends RuntimeException {
    ConfigException(String message) {
        super(message);
    }

    ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
