package org.xinghe.AIThermalGuardIoT.weather.service;

import org.springframework.stereotype.Component;
import org.xinghe.AIThermalGuardIoT.weather.dto.AdvisoryDto;
import org.xinghe.AIThermalGuardIoT.weather.dto.WeatherRecordResponse;
import org.xinghe.AIThermalGuardIoT.weather.model.WeatherAdvisory;
import org.xinghe.AIThermalGuardIoT.weather.model.WeatherRecord;

@Component
public class AdvisoryMappingService {

    public AdvisoryDto toDto(WeatherAdvisory a) {
        return AdvisoryDto.builder()
            .id(a.getId())
            .riskLevel(a.getRiskLevel())
            .summary(a.getSummary())
            .recommendation(a.getRecommendation())
            .record(toRecordResponse(a.getRecord()))
            .createdAt(a.getCreatedAt())
            .build();
    }

    private WeatherRecordResponse toRecordResponse(WeatherRecord r) {
        if (r == null) return null;
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
