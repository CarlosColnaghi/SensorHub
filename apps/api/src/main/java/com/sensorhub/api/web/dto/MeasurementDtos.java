package com.sensorhub.api.web.dto;

import com.sensorhub.api.domain.Measurement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public final class MeasurementDtos {

    private MeasurementDtos() {
    }

    public record MeasurementResponse(
            UUID uuid,
            UUID deviceUuid,
            BigDecimal temperature,
            String temperatureUnit,
            BigDecimal humidity,
            String humidityUnit,
            Instant measuredAt,
            Instant receivedAt
    ) {
        public static MeasurementResponse from(Measurement measurement) {
            return new MeasurementResponse(
                    measurement.getUuid(),
                    measurement.getDeviceUuid(),
                    measurement.getTemperature(),
                    measurement.getTemperatureUnit(),
                    measurement.getHumidity(),
                    measurement.getHumidityUnit(),
                    measurement.getMeasuredAt(),
                    measurement.getReceivedAt()
            );
        }
    }

    public record PageResponse<T>(
            List<T> items,
            int page,
            int size,
            long totalItems,
            int totalPages
    ) {
        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(
                    page.getContent(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages()
            );
        }
    }
}
