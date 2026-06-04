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

    public record LatestMeasurementResponse(
            BigDecimal temperature,
            String temperatureUnit,
            BigDecimal humidity,
            String humidityUnit,
            Instant measuredAt
    ) {
        public static LatestMeasurementResponse from(Measurement measurement) {
            if (measurement == null) {
                return null;
            }
            return new LatestMeasurementResponse(
                    measurement.getTemperature(),
                    measurement.getTemperatureUnit(),
                    measurement.getHumidity(),
                    measurement.getHumidityUnit(),
                    measurement.getMeasuredAt()
            );
        }
    }

    public record MeasurementOverviewResponse(
            UUID deviceUuid,
            String freshnessStatus,
            Instant lastSeenAt,
            PeriodResponse period,
            LatestMeasurementResponse latestMeasurement,
            List<SeriesPointResponse> series,
            OverviewStatsResponse overview
    ) {
    }

    public record PeriodResponse(
            Instant from,
            Instant to,
            String bucket
    ) {
    }

    public record SeriesPointResponse(
            Instant timestamp,
            BigDecimal temperature,
            BigDecimal humidity
    ) {
    }

    public record OverviewStatsResponse(
            BigDecimal temperatureMax,
            Instant temperatureMaxAt,
            BigDecimal temperatureMin,
            Instant temperatureMinAt,
            BigDecimal temperatureAverage,
            BigDecimal humidityMax,
            Instant humidityMaxAt,
            BigDecimal humidityMin,
            Instant humidityMinAt,
            BigDecimal humidityAverage,
            long measurementCount
    ) {
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
