package org.xinghe.AIThermalGuardIoT.weather.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.xinghe.AIThermalGuardIoT.common.result.Result;
import org.xinghe.AIThermalGuardIoT.weather.dto.WeatherRecordRequest;
import org.xinghe.AIThermalGuardIoT.weather.service.WeatherRecordService;

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
}
