package org.xinghe.AIThermalGuardIoT.weather.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import org.xinghe.AIThermalGuardIoT.common.result.Result;
import org.xinghe.AIThermalGuardIoT.weather.dto.AdvisoryDto;
import org.xinghe.AIThermalGuardIoT.weather.repository.WeatherAdvisoryRepository;
import org.xinghe.AIThermalGuardIoT.weather.service.AdvisoryMappingService;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class AdvisoryController {

    private final WeatherAdvisoryRepository advisoryRepository;
    private final AdvisoryMappingService mappingService;

    @GetMapping("/advisories")
    public Result<Page<AdvisoryDto>> getAdvisories(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page - 1, size);
        Page<AdvisoryDto> result = advisoryRepository.findAllByOrderByCreatedAtDesc(pageRequest)
            .map(mappingService::toDto);
        return Result.success(result);
    }
}
