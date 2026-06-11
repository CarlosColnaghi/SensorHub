package com.sensorhub.ingestor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TelemetryProcessorTest {
    private static final String HARDWARE_UUID = "b0fee3a6-ae91-4265-9365-36f793f32f06";
    private static final UUID DEVICE_UUID = UUID.fromString("fe0a2a2e-3222-45ef-91e5-e285ccbe70a2");
    private static final Instant NOW = Instant.parse("2026-06-01T14:31:00Z");

    @Test
    void persistsValidPayloadForActivatedDevice() {
        FakeDeviceRepository deviceRepository = new FakeDeviceRepository(
                new DeviceRecord(DEVICE_UUID, DeviceStatus.ACTIVATED)
        );
        FakeMeasurementRepository measurementRepository = new FakeMeasurementRepository();
        TelemetryProcessor processor = processor(deviceRepository, measurementRepository);

        boolean processed = processor.process(validPayload());

        assertTrue(processed);
        assertEquals(1, measurementRepository.insertCount);
        assertEquals(DEVICE_UUID, measurementRepository.lastDevice.uuid());
        assertEquals(NOW, measurementRepository.lastReceivedAt);
    }

    @Test
    void discardsInvalidPayload() {
        FakeMeasurementRepository measurementRepository = new FakeMeasurementRepository();
        TelemetryProcessor processor = processor(
                new FakeDeviceRepository(new DeviceRecord(DEVICE_UUID, DeviceStatus.ACTIVATED)),
                measurementRepository
        );

        boolean processed = processor.process("not-json");

        assertFalse(processed);
        assertEquals(0, measurementRepository.insertCount);
    }

    @Test
    void discardsUnknownDevice() {
        FakeMeasurementRepository measurementRepository = new FakeMeasurementRepository();
        TelemetryProcessor processor = processor(new FakeDeviceRepository(null), measurementRepository);

        boolean processed = processor.process(validPayload());

        assertFalse(processed);
        assertEquals(0, measurementRepository.insertCount);
    }

    @Test
    void discardsInactivatedDevice() {
        FakeMeasurementRepository measurementRepository = new FakeMeasurementRepository();
        TelemetryProcessor processor = processor(
                new FakeDeviceRepository(new DeviceRecord(DEVICE_UUID, DeviceStatus.INACTIVATED)),
                measurementRepository
        );

        boolean processed = processor.process(validPayload());

        assertFalse(processed);
        assertEquals(0, measurementRepository.insertCount);
    }

    @Test
    void continuesAfterPersistenceFailure() {
        FakeMeasurementRepository measurementRepository = new FakeMeasurementRepository();
        measurementRepository.fail = true;
        TelemetryProcessor processor = processor(
                new FakeDeviceRepository(new DeviceRecord(DEVICE_UUID, DeviceStatus.ACTIVATED)),
                measurementRepository
        );

        boolean processed = processor.process(validPayload());

        assertFalse(processed);
        assertEquals(1, measurementRepository.insertCount);
    }

    private TelemetryProcessor processor(
            DeviceRepository deviceRepository,
            MeasurementRepository measurementRepository
    ) {
        DeviceCache cache = new DeviceCache(
                deviceRepository,
                Duration.ofSeconds(300),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        return new TelemetryProcessor(
                new PayloadParser(),
                cache,
                measurementRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private String validPayload() {
        return """
                {
                  "hardwareUuid": "%s",
                  "temperature": 24.70,
                  "temperatureUnit": "CELSIUS",
                  "humidity": 58.20,
                  "humidityUnit": "RELATIVE_PERCENT",
                  "measuredAt": "2026-06-01T14:30:00Z"
                }
                """.formatted(HARDWARE_UUID);
    }

    private static final class FakeDeviceRepository implements DeviceRepository {
        private final DeviceRecord device;

        private FakeDeviceRepository(DeviceRecord device) {
            this.device = device;
        }

        @Override
        public DeviceRecord findByHardwareUuid(UUID hardwareUuid) {
            return device;
        }
    }

    private static final class FakeMeasurementRepository implements MeasurementRepository {
        private int insertCount;
        private DeviceRecord lastDevice;
        private Instant lastReceivedAt;
        private boolean fail;

        @Override
        public void insert(DeviceRecord device, TelemetryPayload payload, Instant receivedAt)
                throws SQLException {
            insertCount++;
            lastDevice = device;
            lastReceivedAt = receivedAt;
            if (fail) {
                throw new SQLException("insert failed");
            }
        }
    }
}
