package org.xinghe.AIThermalGuardIoT.weather.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xinghe.AIThermalGuardIoT.common.exception.BusinessException;
import org.xinghe.AIThermalGuardIoT.common.exception.ErrorCode;
import org.xinghe.AIThermalGuardIoT.weather.dto.AggregatedRecordDto;
import org.xinghe.AIThermalGuardIoT.weather.dto.WeatherRecordRequest;
import org.xinghe.AIThermalGuardIoT.weather.dto.WeatherRecordResponse;
import org.xinghe.AIThermalGuardIoT.weather.model.WeatherRecord;
import org.xinghe.AIThermalGuardIoT.weather.repository.WeatherRecordRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherRecordService {

    private final WeatherRecordRepository repository;
    private final SseBroadcastService broadcastService;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Alert thresholds — evaluated server-side, ignoring Pi-computed alerts
    private static final double HEAT_INDEX_HIGH = 41.0;
    private static final double TEMP_LOW        = 5.0;
    private static final double HUMIDITY_HIGH   = 65.0;
    private static final double PRESSURE_LOW    = 1000.0;
    private static final double LUX_HIGH        = 50000.0;

    @Transactional
    public WeatherRecordResponse saveRecord(WeatherRecordRequest request) {
        // Server-side alert evaluation — ignores Pi-computed alerts entirely
        List<String> alerts = evaluateAlerts(request);
        String alertsJson;
        try {
            alertsJson = objectMapper.writeValueAsString(alerts);
        } catch (JsonProcessingException e) {
            alertsJson = "[]";
        }

        WeatherRecord record = WeatherRecord.builder()
            .stationId(request.getStationId())
            .temperature(request.getTemperature())
            .humidity(request.getHumidity())
            .pressure(request.getPressure())
            .lux(request.getLux())
            .heatIndex(request.getHeatIndex())
            .heatStressCategory(request.getHeatStressCategory())
            .alerts(alertsJson)
            .build();

        try {
            record = repository.save(record);
        } catch (Exception e) {
            log.error("Failed to save weather record: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.WEATHER_RECORD_SAVE_FAILED);
        }

        WeatherRecordResponse response = toResponse(record);
        broadcastService.broadcastUpdate(response);
        return response;
    }

    public WeatherRecordResponse getLatest() {
        WeatherRecord record = repository.findTopByOrderByCreatedAtDesc();
        return record != null ? toResponse(record) : null;
    }

    public List<WeatherRecordResponse> getRecent20() {
        return repository.findTop20ByOrderByCreatedAtDesc().stream()
            .map(this::toResponse)
            .toList();
    }

    private List<String> evaluateAlerts(WeatherRecordRequest request) {
        List<String> alerts = new ArrayList<>();
        if (request.getHeatIndex() != null && request.getHeatIndex() >= HEAT_INDEX_HIGH)
            alerts.add("HIGH_HEAT_INDEX:" + request.getHeatIndex());
        if (request.getTemperature() != null && request.getTemperature() <= TEMP_LOW)
            alerts.add("LOW_TEMP:" + request.getTemperature());
        if (request.getHumidity() != null && request.getHumidity() >= HUMIDITY_HIGH)
            alerts.add("HIGH_HUMIDITY:" + request.getHumidity());
        if (request.getPressure() != null && request.getPressure() <= PRESSURE_LOW)
            alerts.add("LOW_PRESSURE:" + request.getPressure());
        if (request.getLux() != null && request.getLux() >= LUX_HIGH)
            alerts.add("HIGH_LUX:" + request.getLux());
        return alerts;
    }

    public List<AggregatedRecordDto> getAggregatedRecords(Instant from, Instant to, String aggregation) {
        String bucket = resolveAggregation(from, to, aggregation);

        if ("none".equals(bucket)) {
            return repository.findTop300ByCreatedAtBetweenOrderByCreatedAtAsc(from, to)
                .stream()
                .map(r -> new AggregatedRecordDto(
                    r.getCreatedAt(),
                    r.getTemperature(),
                    r.getHumidity(),
                    r.getPressure(),
                    r.getLux(),
                    r.getHeatIndex(),
                    1L
                ))
                .toList();
        }

        List<Object[]> rows = "hour".equals(bucket)
            ? repository.findAggregatedByHour(from, to)
            : repository.findAggregatedByDay(from, to);

        return rows.stream()
            .map(row -> new AggregatedRecordDto(
                toInstant(row[0]),
                toDouble(row[1]),
                toDouble(row[2]),
                toDouble(row[3]),
                toDouble(row[4]),
                toDouble(row[5]),
                toLong(row[6])
            ))
            .toList();
    }

    private Instant toInstant(Object val) {
        if (val == null) return null;
        if (val instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (val instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        if (val instanceof Instant inst) return inst;
        return null;
    }

    private Double toDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        return null;
    }

    private Long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        return null;
    }

    private String resolveAggregation(Instant from, Instant to, String aggregation) {
        if (!"auto".equals(aggregation)) {
            return aggregation;
        }
        long hours = ChronoUnit.HOURS.between(from, to);
        if (hours <= 2) return "none";
        long days = ChronoUnit.DAYS.between(from, to);
        return days <= 2 ? "hour" : "day";
    }

    private WeatherRecordResponse toResponse(WeatherRecord r) {
        return WeatherRecordResponse.builder()
            .id(r.getId())
            .stationId(r.getStationId())
            .temperature(r.getTemperature())
            .humidity(r.getHumidity())
            .pressure(r.getPressure())
            .lux(r.getLux())
            .heatIndex(r.getHeatIndex())
            .alerts(r.getAlerts())
            .createdAt(r.getCreatedAt())
            .build();
    }
}
