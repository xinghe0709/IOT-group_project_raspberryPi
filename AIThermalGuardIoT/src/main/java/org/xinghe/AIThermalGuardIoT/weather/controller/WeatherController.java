package org.xinghe.AIThermalGuardIoT.weather.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.xinghe.AIThermalGuardIoT.common.exception.BusinessException;
import org.xinghe.AIThermalGuardIoT.common.exception.ErrorCode;
import org.xinghe.AIThermalGuardIoT.common.result.Result;
import org.xinghe.AIThermalGuardIoT.weather.dto.AggregatedRecordDto;
import org.xinghe.AIThermalGuardIoT.weather.dto.WeatherRecordRequest;
import org.xinghe.AIThermalGuardIoT.weather.service.WeatherRecordService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherRecordService service;

    @PostMapping("/record")
    public Result<Void> record(@Valid @RequestBody WeatherRecordRequest request) {
        service.saveRecord(request);
        return Result.success();
    }

    @GetMapping("/records")
    public Result<List<AggregatedRecordDto>> getRecords(
        @RequestParam("from") String fromStr,
        @RequestParam("to") String toStr,
        @RequestParam(defaultValue = "auto") String aggregation) {

        Instant from = parseInstant(fromStr);
        Instant to = parseInstant(toStr);

        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.WEATHER_QUERY_MISSING_DATE);
        }
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.WEATHER_QUERY_DATE_INVALID);
        }
        if (ChronoUnit.DAYS.between(from, to) > 90) {
            throw new BusinessException(ErrorCode.WEATHER_QUERY_RANGE_TOO_LARGE);
        }

        List<AggregatedRecordDto> data = service.getAggregatedRecords(from, to, aggregation);
        return Result.success(data);
    }

    private Instant parseInstant(String s) {
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
