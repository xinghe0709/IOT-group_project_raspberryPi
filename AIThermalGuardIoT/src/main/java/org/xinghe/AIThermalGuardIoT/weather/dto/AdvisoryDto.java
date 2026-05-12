package org.xinghe.AIThermalGuardIoT.weather.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class AdvisoryDto {
    private Long id;
    private String riskLevel;
    private String summary;
    private String recommendation;
    private WeatherRecordResponse record;
    private Instant createdAt;
}
