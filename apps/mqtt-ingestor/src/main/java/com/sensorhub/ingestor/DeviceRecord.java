package com.sensorhub.ingestor;

import java.util.UUID;

record DeviceRecord(UUID uuid, DeviceStatus status) {
}
