package org.xinghe.AIThermalGuardIoT.weather.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.xinghe.AIThermalGuardIoT.weather.dto.AdvisoryDto;
import org.xinghe.AIThermalGuardIoT.weather.dto.WeatherRecordResponse;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class SseBroadcastService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    public void broadcastInit(List<WeatherRecordResponse> records) {
        broadcast("init", records);
    }

    public void broadcastUpdate(WeatherRecordResponse record) {
        broadcast("update", record);
    }

    public void broadcastAdvisory(AdvisoryDto advisory) {
        broadcast("advisory", advisory);
    }

    private void broadcast(String eventName, Object data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
            } catch (IOException e) {
                emitters.remove(emitter);
                log.debug("SSE client disconnected");
            }
        }
    }
}
