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
    @JsonProperty("heat_index")
    private Double heatIndex;
    @JsonProperty("heat_stress_category")
    private String heatStressCategory;
    private List<String> alerts;
}
