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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherRecordService {

    private final WeatherRecordRepository repository;
    private final SseBroadcastService broadcastService;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public WeatherRecordResponse saveRecord(WeatherRecordRequest request) {
        String alertsJson;
        try {
            alertsJson = request.getAlerts() != null && !request.getAlerts().isEmpty()
                ? objectMapper.writeValueAsString(request.getAlerts())
                : "[]";
        } catch (JsonProcessingException e) {
            alertsJson = "[]";
        }

        WeatherRecord record = WeatherRecord.builder()
            .stationId(request.getStationId())
            .temperature(request.getTemperature())
            .humidity(request.getHumidity())
            .pressure(request.getPressure())
            .lux(request.getLux())
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

    public List<AggregatedRecordDto> getAggregatedRecords(Instant from, Instant to, String aggregation) {
        String bucket = resolveAggregation(from, to, aggregation);
        List<Object[]> rows = "hour".equals(bucket)
            ? repository.findAggregatedByHour(from, to)
            : repository.findAggregatedByDay(from, to);

        return rows.stream()
            .map(row -> new AggregatedRecordDto(
                (java.sql.Timestamp) row[0] != null ? ((java.sql.Timestamp) row[0]).toInstant() : null,
                (Double) row[1],
                (Double) row[2],
                (Double) row[3],
                (Double) row[4],
                (Long) row[5]
            ))
            .toList();
    }

    private String resolveAggregation(Instant from, Instant to, String aggregation) {
        if (!"auto".equals(aggregation)) {
            return aggregation;
        }
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
            .alerts(r.getAlerts())
            .createdAt(r.getCreatedAt())
            .build();
    }
}
