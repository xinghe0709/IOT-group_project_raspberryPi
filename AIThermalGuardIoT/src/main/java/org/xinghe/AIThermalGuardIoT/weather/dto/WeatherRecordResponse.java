package org.xinghe.AIThermalGuardIoT.weather.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class WeatherRecordResponse {
    private Long id;
    private String stationId;
    private Double temperature;
    private Double humidity;
    private Double pressure;
    private Double lux;
    private String alerts;
    private Instant createdAt;
}
