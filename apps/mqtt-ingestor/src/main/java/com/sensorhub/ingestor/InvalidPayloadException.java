package com.sensorhub.ingestor;

final class InvalidPayloadException extends RuntimeException {
    InvalidPayloadException(String message) {
        super(message);
    }

    InvalidPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
