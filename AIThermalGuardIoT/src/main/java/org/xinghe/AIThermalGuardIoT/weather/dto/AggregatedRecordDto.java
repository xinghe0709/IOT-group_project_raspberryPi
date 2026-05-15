package org.xinghe.AIThermalGuardIoT.weather.dto;

import java.time.Instant;

public record AggregatedRecordDto(
    Instant bucket,
    Double temperature,
    Double humidity,
    Double pressure,
    Double lux,
    Long count
) {}
