package org.xinghe.AIThermalGuardIoT.weather.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.xinghe.AIThermalGuardIoT.weather.service.SseBroadcastService;
import org.xinghe.AIThermalGuardIoT.weather.service.WeatherRecordService;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class DashboardController {

    private final SseBroadcastService broadcastService;
    private final WeatherRecordService recordService;

    @GetMapping("/stream")
    public SseEmitter stream() {
        SseEmitter emitter = broadcastService.subscribe();
        // Send init history directly to this client
        try {
            emitter.send(SseEmitter.event()
                .name("init")
                .data(recordService.getRecent20()));
        } catch (IOException e) {
            log.debug("Failed to send init to SSE client");
        }
        return emitter;
    }
}
