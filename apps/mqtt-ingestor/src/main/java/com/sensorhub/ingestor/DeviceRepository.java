package com.sensorhub.ingestor;

import java.sql.SQLException;
import java.util.UUID;

interface DeviceRepository {
    DeviceRecord findByHardwareUuid(UUID hardwareUuid) throws SQLException;
}
