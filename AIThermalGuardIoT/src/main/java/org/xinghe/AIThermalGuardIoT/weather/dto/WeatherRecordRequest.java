package org.xinghe.AIThermalGuardIoT.weather.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class WeatherRecordRequest {
    @NotBlank(message = "station_id is required")
    @JsonProperty("station_id")
    private String stationId;
    private Double temperature;
    private Double humidity;
    private Double pressure;
    private Double lux;
    private List<String> alerts;
}
