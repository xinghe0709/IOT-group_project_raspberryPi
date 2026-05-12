package org.xinghe.AIThermalGuardIoT.weather.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdvisoryOutput(
    @JsonProperty("risk_level") String riskLevel,
    @JsonProperty("summary") String summary,
    @JsonProperty("recommendation") String recommendation
) {}
