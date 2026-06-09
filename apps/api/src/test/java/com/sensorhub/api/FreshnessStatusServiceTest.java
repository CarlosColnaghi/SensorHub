package com.sensorhub.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.sensorhub.api.domain.Device;
import com.sensorhub.api.domain.DeviceStatus;
import com.sensorhub.api.domain.Measurement;
import com.sensorhub.api.service.FreshnessStatus;
import com.sensorhub.api.service.FreshnessStatusService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FreshnessStatusServiceTest {

    private final Instant now = Instant.parse("2026-06-09T12:00:00Z");
    private final FreshnessStatusService service = new FreshnessStatusService(
            Clock.fixed(now, ZoneOffset.UTC)
    );

    @Test
    void returnsOfflineWhenLatestReceivedMeasurementIsOlderThanOneMinute() {
        Device device = activeDevice();

        FreshnessStatus status = service.status(device, measurementReceivedAt(now.minusSeconds(61)));

        assertThat(status).isEqualTo(FreshnessStatus.OFFLINE);
    }

    @Test
    void returnsOnlineWhenLatestReceivedMeasurementIsInsideOneMinuteWindow() {
        Device device = activeDevice();

        FreshnessStatus status = service.status(device, measurementReceivedAt(now.minusSeconds(60)));

        assertThat(status).isEqualTo(FreshnessStatus.ONLINE);
    }

    @Test
    void inactivatedDeviceOverridesFreshnessWindow() {
        Device device = activeDevice();
        device.setStatus(DeviceStatus.INACTIVATED);

        FreshnessStatus status = service.status(device, measurementReceivedAt(now.minusSeconds(61)));

        assertThat(status).isEqualTo(FreshnessStatus.INACTIVATED);
    }

    @Test
    void activeDeviceWithoutMeasurementReturnsNoData() {
        FreshnessStatus status = service.status(activeDevice(), null);

        assertThat(status).isEqualTo(FreshnessStatus.NO_DATA);
    }

    private Device activeDevice() {
        Device device = new Device();
        device.setStatus(DeviceStatus.ACTIVATED);
        return device;
    }

    private Measurement measurementReceivedAt(Instant receivedAt) {
        Measurement measurement = new Measurement();
        measurement.setMeasuredAt(receivedAt.minusSeconds(10));
        measurement.setReceivedAt(receivedAt);
        return measurement;
    }
}
