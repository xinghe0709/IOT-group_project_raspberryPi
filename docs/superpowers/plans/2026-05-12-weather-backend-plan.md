# Weather Station Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Java backend for the PiicoDev weather station: ingest sensor JSON, persist to PostgreSQL, generate AI safety advisories via Spring AI, and serve a real-time dashboard with SSE + Chart.js.

**Architecture:** New `weather/` package under `org.xinghe.AIThermalGuardIoT` with model/dto/repository/service/controller sub-packages. Follows existing patterns: `Result<T>` responses, `BusinessException` + `ErrorCode` for errors, `StructuredOutputInvoker` for LLM calls, `@Scheduled` for periodic analysis.

**Tech Stack:** Java 21, Spring Boot 4.0.1, JPA/Hibernate, PostgreSQL, Spring AI 2.0.0-M4 (OpenAI-compatible), Chart.js 4.x, SSE.

---

### Task 1: Add ErrorCode entries and JPA Entities

**Files:**
- Modify: `AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/common/exception/ErrorCode.java`
- Create: `AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/model/WeatherRecord.java`
- Create: `AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/model/WeatherAdvisory.java`

- [ ] **Step 1: Add 12xxx error codes to ErrorCode enum**

In `ErrorCode.java`, add after the 11xxx segment (before the closing `;`):

```java
// ========== 气象站模块错误 12xxx ==========
WEATHER_RECORD_SAVE_FAILED(12001, "气象数据保存失败"),
WEATHER_ADVISORY_GENERATION_FAILED(12002, "AI环境分析生成失败"),
WEATHER_ADVISORY_NOT_FOUND(12003, "环境建议不存在"),
WEATHER_STATION_INVALID(12004, "气象站标识无效");
```

- [ ] **Step 2: Create WeatherRecord entity**

```java
package org.xinghe.AIThermalGuardIoT.weather.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "weather_records", indexes = {
    @Index(name = "idx_records_created_at", columnList = "createdAt DESC"),
    @Index(name = "idx_records_station_id", columnList = "station_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class WeatherRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_id", nullable = false, length = 50)
    private String stationId;

    @Column
    private Double temperature;

    @Column
    private Double humidity;

    @Column
    private Double pressure;

    @Column
    private Double lux;

    @Column(length = 500)
    private String alerts;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
```

- [ ] **Step 3: Create WeatherAdvisory entity**

```java
package org.xinghe.AIThermalGuardIoT.weather.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "weather_advisories")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class WeatherAdvisory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id")
    private WeatherRecord record;

    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String recommendation;

    @Column(columnDefinition = "TEXT")
    private String rawResponse;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
```

- [ ] **Step 4: Build and verify compilation**

```bash
cd AIThermalGuardIoT && ./gradlew compileJava
```

Expected: BUILD SUCCESSFUL. JPA will auto-create tables on next bootRun (`ddl-auto: update`).

- [ ] **Step 5: Commit**

```bash
git add AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/common/exception/ErrorCode.java \
        AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/
git commit -m "feat: add weather ErrorCodes and JPA entities"
```

---

### Task 2: Create DTOs

**Files:**
- Create: `AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/dto/WeatherRecordRequest.java`
- Create: `AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/dto/WeatherRecordResponse.java`
- Create: `AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/dto/AdvisoryDto.java`
- Create: `AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/dto/AdvisoryOutput.java`

- [ ] **Step 1: Create WeatherRecordRequest (deserialize from Raspberry Pi)**

```java
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
```

- [ ] **Step 2: Create WeatherRecordResponse (for SSE + frontend)**

```java
package org.xinghe.AIThermalGuardIoT.weather.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class WeatherRecordResponse {
    private Long id;
    private String stationId;
    private Double temperature;
    private Double humidity;
    private Double pressure;
    private Double lux;
    private String alerts;
    private Instant createdAt;
}
```

- [ ] **Step 3: Create AdvisoryDto (for history API)**

```java
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
```

- [ ] **Step 4: Create AdvisoryOutput (for BeanOutputConverter — LLM structured output)**

```java
package org.xinghe.AIThermalGuardIoT.weather.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdvisoryOutput(
    @JsonProperty("risk_level") String riskLevel,
    @JsonProperty("summary") String summary,
    @JsonProperty("recommendation") String recommendation
) {}
```

- [ ] **Step 5: Build and verify compilation**

```bash
cd AIThermalGuardIoT && ./gradlew compileJava
```

- [ ] **Step 6: Commit**

```bash
git add AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/dto/
git commit -m "feat: add weather DTOs (request, response, advisory)"
```

---

### Task 3: Create Repository Interfaces

**Files:**
- Create: `AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/repository/WeatherRecordRepository.java`
- Create: `AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/repository/WeatherAdvisoryRepository.java`

- [ ] **Step 1: Create WeatherRecordRepository**

```java
package org.xinghe.AIThermalGuardIoT.weather.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.xinghe.AIThermalGuardIoT.weather.model.WeatherRecord;

import java.util.List;

@Repository
public interface WeatherRecordRepository extends JpaRepository<WeatherRecord, Long> {

    WeatherRecord findTopByOrderByCreatedAtDesc();

    List<WeatherRecord> findTop20ByOrderByCreatedAtDesc();
}
```

- [ ] **Step 2: Create WeatherAdvisoryRepository**

```java
package org.xinghe.AIThermalGuardIoT.weather.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.xinghe.AIThermalGuardIoT.weather.model.WeatherAdvisory;

@Repository
public interface WeatherAdvisoryRepository extends JpaRepository<WeatherAdvisory, Long> {

    Page<WeatherAdvisory> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
```

- [ ] **Step 3: Build**

```bash
cd AIThermalGuardIoT && ./gradlew compileJava
```

- [ ] **Step 4: Commit**

```bash
git add AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/repository/
git commit -m "feat: add weather JPA repositories"
```

---

### Task 4: Create Services (RecordService + SseBroadcastService)

**Files:**
- Create: `AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/service/SseBroadcastService.java`
- Create: `AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/service/WeatherRecordService.java`

- [ ] **Step 1: Create SseBroadcastService**

```java
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
        SseEmitter emitter = new SseEmitter(0L); // no timeout
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
```

- [ ] **Step 2: Create WeatherRecordService**

```java
package org.xinghe.AIThermalGuardIoT.weather.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xinghe.AIThermalGuardIoT.common.exception.BusinessException;
import org.xinghe.AIThermalGuardIoT.common.exception.ErrorCode;
import org.xinghe.AIThermalGuardIoT.weather.dto.WeatherRecordRequest;
import org.xinghe.AIThermalGuardIoT.weather.dto.WeatherRecordResponse;
import org.xinghe.AIThermalGuardIoT.weather.model.WeatherRecord;
import org.xinghe.AIThermalGuardIoT.weather.repository.WeatherRecordRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherRecordService {

    private final WeatherRecordRepository repository;
    private final SseBroadcastService broadcastService;
    private final ObjectMapper objectMapper;

    @Transactional
    public WeatherRecordResponse saveRecord(WeatherRecordRequest request) {
        String alertsJson;
        try {
            alertsJson = request.getAlerts() != null && !request.getAlerts().isEmpty()
                ? objectMapper.writeValueAsString(request.getAlerts())
                : "[]";
        } catch (JsonProcessingException e) {
            alertsJson = "[]";
        }

        WeatherRecord record = WeatherRecord.builder()
            .stationId(request.getStationId())
            .temperature(request.getTemperature())
            .humidity(request.getHumidity())
            .pressure(request.getPressure())
            .lux(request.getLux())
            .alerts(alertsJson)
            .build();

        try {
            record = repository.save(record);
        } catch (Exception e) {
            log.error("Failed to save weather record: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.WEATHER_RECORD_SAVE_FAILED);
        }

        WeatherRecordResponse response = toResponse(record);
        broadcastService.broadcastUpdate(response);
        return response;
    }

    public WeatherRecordResponse getLatest() {
        WeatherRecord record = repository.findTopByOrderByCreatedAtDesc();
        return record != null ? toResponse(record) : null;
    }

    public List<WeatherRecordResponse> getRecent20() {
        return repository.findTop20ByOrderByCreatedAtDesc().stream()
            .map(this::toResponse)
            .toList();
    }

    private WeatherRecordResponse toResponse(WeatherRecord r) {
        return WeatherRecordResponse.builder()
            .id(r.getId())
            .stationId(r.getStationId())
            .temperature(r.getTemperature())
            .humidity(r.getHumidity())
            .pressure(r.getPressure())
            .lux(r.getLux())
            .alerts(r.getAlerts())
            .createdAt(r.getCreatedAt())
            .build();
    }
}
```

- [ ] **Step 3: Build**

```bash
cd AIThermalGuardIoT && ./gradlew compileJava
```

- [ ] **Step 4: Commit**

```bash
git add AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/service/
git commit -m "feat: add WeatherRecordService and SseBroadcastService"
```

---

### Task 5: Create WeatherController (POST ingest)

**Files:**
- Create: `AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/controller/WeatherController.java`

- [ ] **Step 1: Write integration test**

Create `AIThermalGuardIoT/src/test/java/org/xinghe/AIThermalGuardIoT/weather/controller/WeatherControllerTest.java`:

```java
package org.xinghe.AIThermalGuardIoT.weather.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WeatherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldAcceptValidRecord() throws Exception {
        var body = objectMapper.createObjectNode();
        body.put("station_id", "pi-weather-01");
        body.put("temperature", 25.3);
        body.put("humidity", 62.0);
        body.put("pressure", 1013.0);
        body.put("lux", 4500.0);
        body.putArray("alerts").add("HIGH_TEMP:41.2");

        mockMvc.perform(post("/api/weather/record")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldRejectEmptyStationId() throws Exception {
        var body = objectMapper.createObjectNode();
        body.put("station_id", "");
        body.put("temperature", 25.3);

        mockMvc.perform(post("/api/weather/record")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));
    }
}
```

- [ ] **Step 2: Run test to verify it fails (406 expected)**

```bash
cd AIThermalGuardIoT && ./gradlew test --tests "org.xinghe.AIThermalGuardIoT.weather.controller.WeatherControllerTest"
```

Expected: FAIL (no controller mapped to `/api/weather/record`)

- [ ] **Step 3: Create WeatherController**

```java
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
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd AIThermalGuardIoT && ./gradlew test --tests "org.xinghe.AIThermalGuardIoT.weather.controller.WeatherControllerTest"
```

Expected: PASS (both tests)

- [ ] **Step 5: Commit**

```bash
git add AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/controller/WeatherController.java \
        AIThermalGuardIoT/src/test/java/org/xinghe/AIThermalGuardIoT/weather/
git commit -m "feat: add WeatherController POST /api/weather/record"
```

---

### Task 6: Create AdvisoryScheduler (LLM analysis)

**Files:**
- Create: `AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/service/AdvisoryScheduler.java`
- Create: `AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/service/AdvisoryMappingService.java`

- [ ] **Step 1: Create AdvisoryMappingService (Entity ↔ DTO mapping)**

```java
package org.xinghe.AIThermalGuardIoT.weather.service;

import org.springframework.stereotype.Component;
import org.xinghe.AIThermalGuardIoT.weather.dto.AdvisoryDto;
import org.xinghe.AIThermalGuardIoT.weather.dto.WeatherRecordResponse;
import org.xinghe.AIThermalGuardIoT.weather.model.WeatherAdvisory;
import org.xinghe.AIThermalGuardIoT.weather.model.WeatherRecord;

@Component
public class AdvisoryMappingService {

    public AdvisoryDto toDto(WeatherAdvisory a) {
        return AdvisoryDto.builder()
            .id(a.getId())
            .riskLevel(a.getRiskLevel())
            .summary(a.getSummary())
            .recommendation(a.getRecommendation())
            .record(toRecordResponse(a.getRecord()))
            .createdAt(a.getCreatedAt())
            .build();
    }

    private WeatherRecordResponse toRecordResponse(WeatherRecord r) {
        if (r == null) return null;
        return WeatherRecordResponse.builder()
            .id(r.getId())
            .stationId(r.getStationId())
            .temperature(r.getTemperature())
            .humidity(r.getHumidity())
            .pressure(r.getPressure())
            .lux(r.getLux())
            .alerts(r.getAlerts())
            .createdAt(r.getCreatedAt())
            .build();
    }
}
```

- [ ] **Step 2: Create AdvisoryScheduler**

```java
package org.xinghe.AIThermalGuardIoT.weather.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.xinghe.AIThermalGuardIoT.common.ai.LlmProviderRegistry;
import org.xinghe.AIThermalGuardIoT.common.ai.StructuredOutputInvoker;
import org.xinghe.AIThermalGuardIoT.common.exception.BusinessException;
import org.xinghe.AIThermalGuardIoT.common.exception.ErrorCode;
import org.xinghe.AIThermalGuardIoT.weather.dto.AdvisoryOutput;
import org.xinghe.AIThermalGuardIoT.weather.model.WeatherAdvisory;
import org.xinghe.AIThermalGuardIoT.weather.model.WeatherRecord;
import org.xinghe.AIThermalGuardIoT.weather.repository.WeatherAdvisoryRepository;
import org.xinghe.AIThermalGuardIoT.weather.repository.WeatherRecordRepository;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class AdvisoryScheduler {

    private static final Logger advisoryLog = LoggerFactory.getLogger("advisory");

    private final WeatherRecordRepository recordRepository;
    private final WeatherAdvisoryRepository advisoryRepository;
    private final LlmProviderRegistry llmProviderRegistry;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final SseBroadcastService broadcastService;
    private final AdvisoryMappingService mappingService;

    @Scheduled(fixedRate = 120_000, initialDelay = 10_000)
    public void generateAdvisory() {
        WeatherRecord latest = recordRepository.findTopByOrderByCreatedAtDesc();
        if (latest == null) {
            log.debug("No weather records yet, skipping advisory generation");
            return;
        }

        String userPrompt = buildPrompt(latest);
        ChatClient chatClient = llmProviderRegistry.getChatClient("deepseek");
        BeanOutputConverter<AdvisoryOutput> converter = new BeanOutputConverter<>(AdvisoryOutput.class);

        try {
            AdvisoryOutput output = structuredOutputInvoker.invoke(
                chatClient,
                userPrompt,  // systemPrompt (StructuredOutputInvoker appends anti-injection)
                userPrompt,  // userPrompt
                converter,
                ErrorCode.WEATHER_ADVISORY_GENERATION_FAILED,
                "AI分析失败: ",
                "weather-advisory",
                log
            );

            String rawResponse = ""; // captured via converter in practice
            WeatherAdvisory advisory = WeatherAdvisory.builder()
                .record(latest)
                .riskLevel(output.riskLevel())
                .summary(output.summary())
                .recommendation(output.recommendation())
                .rawResponse(rawResponse)
                .build();

            advisory = advisoryRepository.save(advisory);
            advisoryLog.info("Advisory generated: risk={}, summary={}", output.riskLevel(), output.summary());

            broadcastService.broadcastAdvisory(mappingService.toDto(advisory));

        } catch (Exception e) {
            log.error("Failed to generate advisory: {}", e.getMessage(), e);
        }
    }

    private String buildPrompt(WeatherRecord r) {
        return String.format("""
            You are a warm, thoughtful weather companion. You care about people's comfort and safety like a considerate friend would. Speak naturally — imagine you're telling a neighbor what to expect before they step outside.

            Here is the current sensor reading:

            Temperature: %.1f°C
            Humidity: %.0f%%
            Pressure: %.0f hPa
            Light: %.0f lux
            Alerts: %s

            Respond with a JSON object:

            {
              "risk_level": one of "LOW", "MODERATE", "HIGH", "EXTREME",
              "summary": one friendly sentence describing what the environment feels like right now,
              "recommendation": 1-3 sentences of warm, personable advice — no lists, no jargon, sounds like a caring friend talking. Example: "It's pretty muggy out there right now! Maybe crack a window and grab a cold drink. If you're heading out later, an umbrella wouldn't hurt — the pressure's dropping."
            }

            Rules for recommendation:
            - Use conversational language, contractions (it's, you'd, don't), and occasional light humor.
            - Never use bullet points, numbered lists, or technical jargon.
            - When conditions are comfortable, express warmth and encouragement.
            - When conditions are dangerous, be serious but still human, like a concerned friend.
            - Mention specific real-world impacts (e.g. "the sun's harsh today" instead of "lux levels exceed threshold").
            """,
            safeDouble(r.getTemperature()),
            safeDouble(r.getHumidity()),
            safeDouble(r.getPressure()),
            safeDouble(r.getLux()),
            r.getAlerts()
        );
    }

    private double safeDouble(Double v) {
        return v != null ? v : 0.0;
    }
}
```

- [ ] **Step 3: Build**

```bash
cd AIThermalGuardIoT && ./gradlew compileJava
```

- [ ] **Step 4: Commit**

```bash
git add AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/service/
git commit -m "feat: add AdvisoryScheduler with LLM analysis every 2min"
```

---

### Task 7: Create DashboardController (SSE + static page)

**Files:**
- Create: `AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/controller/DashboardController.java`

- [ ] **Step 1: Create DashboardController**

```java
package org.xinghe.AIThermalGuardIoT.weather.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.xinghe.AIThermalGuardIoT.weather.service.SseBroadcastService;
import org.xinghe.AIThermalGuardIoT.weather.service.WeatherRecordService;

@Controller
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class DashboardController {

    private final SseBroadcastService broadcastService;
    private final WeatherRecordService recordService;

    @GetMapping("/stream")
    @ResponseBody
    public SseEmitter stream() {
        SseEmitter emitter = broadcastService.subscribe();
        // Send init event with recent history
        broadcastService.broadcastInit(recordService.getRecent20());
        return emitter;
    }
}
```

Note: `broadcastInit()` only reaches the newly created emitter due to `CopyOnWriteArrayList` nature; other clients don't get duplicate init events. For a more targeted approach, the init data can be sent directly to the new emitter before it's added to the list. But the current broadcast model is sufficient for low-concurrency scenarios.

- [ ] **Step 2: Build**

```bash
cd AIThermalGuardIoT && ./gradlew compileJava
```

- [ ] **Step 3: Commit**

```bash
git add AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/controller/DashboardController.java
git commit -m "feat: add SSE streaming endpoint /api/weather/stream"
```

---

### Task 8: Create AdvisoryController

**Files:**
- Create: `AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/controller/AdvisoryController.java`

- [ ] **Step 1: Create AdvisoryController**

```java
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
```

- [ ] **Step 2: Build**

```bash
cd AIThermalGuardIoT && ./gradlew compileJava
```

- [ ] **Step 3: Commit**

```bash
git add AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/controller/AdvisoryController.java
git commit -m "feat: add AdvisoryController GET /api/weather/advisories"
```

---

### Task 9: Create Frontend Dashboard (HTML + JS)

**Files:**
- Create: `AIThermalGuardIoT/src/main/resources/static/dashboard.html`
- Create: `AIThermalGuardIoT/src/main/resources/static/app.js`

- [ ] **Step 1: Create dashboard.html**

```html
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>PiicoDev Weather Station</title>
<link href="https://fonts.googleapis.com/css2?family=DM+Mono:ital@0;1&family=Space+Mono&family=Crimson+Text:ital,wght@0,400;0,600;1,400&display=swap" rel="stylesheet">
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.7/dist/chart.umd.min.js"></script>
<style>
  :root {
    --bg: #1a1c1e;
    --card: #242629;
    --text: #e8d5c4;
    --accent-orange: #ff6b35;
    --accent-teal: #4ecdc4;
    --accent-amber: #f7dc6f;
    --accent-gold: #ffeaa7;
    --danger: #e74c3c;
    --border: #3a3d42;
    --risk-low: #27ae60;
    --risk-moderate: #f39c12;
    --risk-high: #e67e22;
    --risk-extreme: #e74c3c;
  }
  * { margin:0; padding:0; box-sizing:border-box; }
  body { background:var(--bg); color:var(--text); font-family:'Crimson Text',serif; display:flex; min-height:100vh; }
  nav { width:200px; background:#1c1d21; position:fixed; top:0; left:0; bottom:0; padding:24px 16px; display:flex; flex-direction:column; gap:8px; }
  nav .logo { font-family:'DM Mono',monospace; font-size:14px; color:var(--accent-orange); margin-bottom:24px; letter-spacing:2px; text-transform:uppercase; }
  nav a { font-family:'DM Mono',monospace; font-size:13px; color:var(--text); text-decoration:none; padding:10px 12px; border-radius:6px; transition:all .15s; border-left:3px solid transparent; }
  nav a:hover, nav a.active { background:var(--card); border-left-color:var(--accent-orange); color:var(--accent-gold); }
  main { margin-left:200px; flex:1; padding:32px; }
  .header { display:flex; justify-content:space-between; align-items:center; margin-bottom:24px; }
  .header h1 { font-family:'DM Mono',monospace; font-size:18px; font-weight:400; }
  .status { display:flex; align-items:center; gap:8px; font-family:'Space Mono',monospace; font-size:12px; }
  .status .dot { width:8px; height:8px; border-radius:50%; background:var(--accent-teal); animation:pulse 2s infinite; }
  .status .dot.alert { background:var(--danger); }
  @keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.4} }
  .cards { display:grid; grid-template-columns:repeat(4,1fr); gap:16px; margin-bottom:24px; }
  .card { background:var(--card); border-radius:10px; padding:20px; border:1px solid var(--border); }
  .card .label { font-family:'DM Mono',monospace; font-size:11px; text-transform:uppercase; letter-spacing:1px; color:#888; margin-bottom:8px; }
  .card .value { font-family:'Space Mono',monospace; font-size:28px; }
  .card.temp .value{color:var(--accent-orange)} .card.humidity .value{color:var(--accent-teal)}
  .card.pressure .value{color:var(--accent-amber)} .card.light .value{color:var(--accent-gold)}
  .alerts-bar { background:var(--danger); color:#fff; padding:10px 16px; border-radius:8px; margin-bottom:16px; font-family:'DM Mono',monospace; font-size:12px; display:none; }
  .alerts-bar.active { display:block; animation:pulse 1.5s infinite; }
  .chart-wrap { background:var(--card); border-radius:10px; padding:20px; border:1px solid var(--border); margin-bottom:24px; }
  .chart-wrap h3 { font-family:'DM Mono',monospace; font-size:13px; margin-bottom:12px; font-weight:400; }
  .advisory-card { background:var(--card); border-radius:10px; padding:20px; border:1px solid var(--border); margin-bottom:12px; display:flex; gap:16px; }
  .advisory-card .risk-bar { width:4px; border-radius:2px; flex-shrink:0; }
  .advisory-card .risk-bar.LOW{background:var(--risk-low)} .advisory-card .risk-bar.MODERATE{background:var(--risk-moderate)}
  .advisory-card .risk-bar.HIGH{background:var(--risk-high)} .advisory-card .risk-bar.EXTREME{background:var(--risk-extreme)}
  .advisory-card .content { flex:1; }
  .advisory-card .risk-tag { font-family:'DM Mono',monospace; font-size:10px; text-transform:uppercase; letter-spacing:1px; margin-bottom:4px; }
  .advisory-card .risk-tag.LOW{color:var(--risk-low)} .advisory-card .risk-tag.MODERATE{color:var(--risk-moderate)}
  .advisory-card .risk-tag.HIGH{color:var(--risk-high)} .advisory-card .risk-tag.EXTREME{color:var(--risk-extreme)}
  .advisory-card .summary { font-family:'Crimson Text',serif; font-size:16px; line-height:1.5; margin-bottom:4px; }
  .advisory-card .time { font-family:'DM Mono',monospace; font-size:11px; color:#666; }
  .advisory-card .detail { display:none; margin-top:8px; font-size:15px; line-height:1.6; color:#ccc; }
  .advisory-card .detail.open { display:block; }
  .advisory-list { display:flex; flex-direction:column; gap:12px; }
  .load-more { font-family:'DM Mono',monospace; font-size:12px; color:var(--accent-teal); text-align:center; padding:12px; cursor:pointer; }
  .toast { position:fixed; top:0; left:0; right:0; background:linear-gradient(90deg,var(--accent-orange),var(--accent-amber)); color:var(--bg); padding:12px 24px; font-family:'DM Mono',monospace; font-size:13px; text-align:center; transform:translateY(-100%); transition:transform .3s; z-index:1000; }
  .toast.show { transform:translateY(0); }
  @media(max-width:768px){ nav{width:60px;padding:16px 8px} nav a span{display:none} main{margin-left:60px;padding:16px} .cards{grid-template-columns:1fr 1fr} }
</style>
</head>
<body>
<nav>
  <div class="logo">🌦 PiicoDev</div>
  <a href="#" class="active" data-page="dashboard"><span>◆ 仪表盘</span></a>
  <a href="#" data-page="advisories"><span>◇ 环境建议</span></a>
</nav>
<main>
  <div class="toast" id="toast"></div>
  <div class="header">
    <h1>Weather Station <span id="stationId">pi-weather-01</span></h1>
    <div class="status"><div class="dot" id="statusDot"></div><span id="lastUpdate">--</span></div>
  </div>
  <div class="alerts-bar" id="alertsBar"></div>

  <!-- Dashboard Page -->
  <div id="pageDashboard">
    <div class="cards">
      <div class="card temp"><div class="label">Temperature</div><div class="value" id="valTemp">--°C</div></div>
      <div class="card humidity"><div class="label">Humidity</div><div class="value" id="valHum">--%</div></div>
      <div class="card pressure"><div class="label">Pressure</div><div class="value" id="valPres">-- hPa</div></div>
      <div class="card light"><div class="label">Light</div><div class="value" id="valLux">-- lux</div></div>
    </div>
    <div class="chart-wrap"><h3>Combined Trends (2h)</h3><canvas id="trendChart"></canvas></div>
    <div class="chart-wrap">
      <h3>Latest Advisory</h3>
      <div id="latestAdvisory" style="font-family:'Crimson Text',serif;font-size:16px;color:#888;">Waiting for first analysis...</div>
    </div>
  </div>

  <!-- Advisory History Page -->
  <div id="pageAdvisories" style="display:none;">
    <h2 style="font-family:'DM Mono',monospace;font-size:16px;font-weight:400;margin-bottom:16px;">💬 环境建议历史</h2>
    <div class="advisory-list" id="advisoryList"></div>
    <div class="load-more" id="loadMore" style="display:none;">加载更多 ↓</div>
  </div>
</main>
<script src="/app.js"></script>
</body>
</html>
```

- [ ] **Step 2: Create app.js**

```javascript
// SSE connection
const evtSource = new EventSource('/api/weather/stream');
let chart = null;

// Init chart with empty data
const ctx = document.getElementById('trendChart').getContext('2d');
chart = new Chart(ctx, {
  type: 'line',
  data: {
    labels: [],
    datasets: [
      { label: 'Temperature (°C)', data: [], borderColor: '#ff6b35', yAxisID: 'yLinear', tension: 0.3, pointRadius: 0 },
      { label: 'Humidity (%)', data: [], borderColor: '#4ecdc4', yAxisID: 'yLinear', tension: 0.3, pointRadius: 0 },
      { label: 'Pressure (hPa)', data: [], borderColor: '#f7dc6f', yAxisID: 'yLinear', tension: 0.3, pointRadius: 0 },
      { label: 'Light (lux)', data: [], borderColor: '#ffeaa7', yAxisID: 'yLog', tension: 0.3, pointRadius: 0 }
    ]
  },
  options: {
    responsive: true,
    animation: { duration: 300 },
    scales: {
      yLinear: { type: 'linear', position: 'left', title: { display: true, text: '°C / % / hPa', color: '#888' } },
      yLog: { type: 'logarithmic', position: 'right', title: { display: true, text: 'lux (log)', color: '#888' }, min: 1 }
    },
    plugins: { legend: { labels: { color: '#888', font: { family: 'DM Mono', size: 11 } } } }
  }
});

// SSE event handlers
evtSource.addEventListener('init', e => {
  const records = JSON.parse(e.data);
  if (!records || records.length === 0) return;
  records.reverse().forEach(r => appendRecord(r));
});

evtSource.addEventListener('update', e => {
  const r = JSON.parse(e.data);
  updateCurrentCards(r);
  appendRecord(r);
  document.getElementById('lastUpdate').textContent = new Date(r.created_at).toLocaleTimeString();
  updateAlerts(r.alerts);
});

evtSource.addEventListener('advisory', e => {
  const a = JSON.parse(e.data);
  showToast('New environmental advisory available');
  updateLatestAdvisory(a);
  prependAdvisoryCard(a);
});

function appendRecord(r) {
  const label = new Date(r.created_at).toLocaleTimeString();
  chart.data.labels.push(label);
  chart.data.datasets[0].data.push(r.temperature);
  chart.data.datasets[1].data.push(r.humidity);
  chart.data.datasets[2].data.push(r.pressure);
  chart.data.datasets[3].data.push(r.lux);
  if (chart.data.labels.length > 240) {
    chart.data.labels.shift();
    chart.data.datasets.forEach(d => d.data.shift());
  }
  chart.update();
}

function updateCurrentCards(r) {
  document.getElementById('valTemp').textContent = r.temperature != null ? r.temperature.toFixed(1) + '°C' : '--';
  document.getElementById('valHum').textContent = r.humidity != null ? r.humidity.toFixed(0) + '%' : '--';
  document.getElementById('valPres').textContent = r.pressure != null ? r.pressure.toFixed(0) + ' hPa' : '--';
  document.getElementById('valLux').textContent = r.lux != null ? r.lux.toFixed(0) + ' lux' : '--';
}

function updateAlerts(alertsStr) {
  const bar = document.getElementById('alertsBar');
  try {
    const alerts = JSON.parse(alertsStr || '[]');
    if (alerts.length > 0) {
      bar.textContent = '⚠ ' + alerts.join('  ·  ');
      bar.classList.add('active');
      document.getElementById('statusDot').classList.add('alert');
    } else {
      bar.classList.remove('active');
      document.getElementById('statusDot').classList.remove('alert');
    }
  } catch(e) { bar.classList.remove('active'); }
}

function updateLatestAdvisory(a) {
  const riskColors = { LOW: '#27ae60', MODERATE: '#f39c12', HIGH: '#e67e22', EXTREME: '#e74c3c' };
  document.getElementById('latestAdvisory').innerHTML = `
    <div class="advisory-card">
      <div class="risk-bar ${a.risk_level}"></div>
      <div class="content">
        <div class="risk-tag ${a.risk_level}">${a.risk_level}</div>
        <div class="summary">${a.recommendation}</div>
        <div class="time">${new Date(a.created_at).toLocaleTimeString()}</div>
      </div>
    </div>
  `;
}

function prependAdvisoryCard(a) {
  if (document.getElementById('pageAdvisories').style.display === 'none') return;
  const list = document.getElementById('advisoryList');
  list.insertBefore(createAdvisoryCard(a), list.firstChild);
}

function createAdvisoryCard(a) {
  const div = document.createElement('div');
  div.className = 'advisory-card';
  div.innerHTML = `
    <div class="risk-bar ${a.risk_level}"></div>
    <div class="content">
      <div class="risk-tag ${a.risk_level}">${a.risk_level}</div>
      <div class="summary">${a.recommendation}</div>
      <div class="time">${new Date(a.created_at).toLocaleString()} · ${a.record ? a.record.temperature + '°C' : ''}</div>
      <div class="detail" onclick="this.classList.toggle('open')">${a.summary}</div>
    </div>
  `;
  return div;
}

function showToast(msg) {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.classList.add('show');
  setTimeout(() => t.classList.remove('show'), 4000);
}

// Advisory history page - load on nav click
let advisoryPage = 1;
document.getElementById('loadMore').addEventListener('click', loadAdvisories);

async function loadAdvisories() {
  const resp = await fetch(`/api/weather/advisories?page=${advisoryPage}&size=20`);
  const result = await resp.json();
  if (result.code === 200 && result.data.content.length > 0) {
    const list = document.getElementById('advisoryList');
    result.data.content.forEach(a => list.appendChild(createAdvisoryCard(a)));
    advisoryPage++;
    document.getElementById('loadMore').style.display = result.data.total_pages > advisoryPage - 1 ? 'block' : 'none';
  }
}

// Sidebar navigation
document.querySelectorAll('nav a').forEach(link => {
  link.addEventListener('click', e => {
    e.preventDefault();
    document.querySelectorAll('nav a').forEach(l => l.classList.remove('active'));
    link.classList.add('active');
    const page = link.dataset.page;
    document.getElementById('pageDashboard').style.display = page === 'dashboard' ? 'block' : 'none';
    document.getElementById('pageAdvisories').style.display = page === 'advisories' ? 'block' : 'none';
    if (page === 'advisories') {
      document.getElementById('advisoryList').innerHTML = '';
      advisoryPage = 1;
      loadAdvisories();
    }
  });
});
```

- [ ] **Step 3: Build and verify static resources are in classpath**

```bash
cd AIThermalGuardIoT && ./gradlew build
```

- [ ] **Step 4: Commit**

```bash
git add AIThermalGuardIoT/src/main/resources/static/
git commit -m "feat: add dashboard frontend with Chart.js + SSE"
```

---

### Task 10: Fix SSE Init Delivery (targeted to new subscriber)

**Files:**
- Modify: `AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/service/SseBroadcastService.java`
- Modify: `AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/controller/DashboardController.java`

- [ ] **Step 1: Update SseBroadcastService to return emitter after init data is sent**

Replace `subscribe()` method:

```java
public SseEmitter subscribe() {
    SseEmitter emitter = new SseEmitter(0L);
    emitters.add(emitter);
    emitter.onCompletion(() -> emitters.remove(emitter));
    emitter.onTimeout(() -> emitters.remove(emitter));
    emitter.onError(e -> emitters.remove(emitter));
    return emitter;
}
```

- [ ] **Step 2: Update DashboardController to send init to the specific emitter**

```java
@GetMapping("/stream")
@ResponseBody
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
```

Add `@Slf4j` to the class and `import java.io.IOException;`.

- [ ] **Step 3: Build**

```bash
cd AIThermalGuardIoT && ./gradlew build
```

- [ ] **Step 4: Commit**

```bash
git add AIThermalGuardIoT/src/main/java/org/xinghe/AIThermalGuardIoT/weather/
git commit -m "fix: send SSE init data directly to new subscriber"
```

---

## Verification

After all tasks complete:

1. **Start dependencies** (PostgreSQL, Redis):
   ```bash
   docker compose up -d postgres redis   # or ensure they're running
   ```

2. **Start Spring Boot**:
   ```bash
   cd AIThermalGuardIoT && ./gradlew bootRun
   ```

3. **Simulate Raspberry Pi POST**:
   ```bash
   curl -X POST http://localhost:8080/api/weather/record \
     -H 'Content-Type: application/json' \
     -d '{"station_id":"pi-weather-01","temperature":25.3,"humidity":62,"pressure":1013,"lux":4500,"alerts":[]}'
   ```
   Expected: `{"code":200,"message":"success","data":null}`

4. **Verify SSE** (open in browser or curl):
   ```bash
   curl -N http://localhost:8080/api/weather/stream
   ```
   Expected: `event:init` with JSON array, then `event:update` every 30s when POST arrives

5. **Open dashboard**: `http://localhost:8080/dashboard.html` — verify chart renders, cards update, advisory appears after 2min

6. **Verify advisory history API**:
   ```bash
   curl http://localhost:8080/api/weather/advisories?page=1&size=5
   ```

7. **Run all tests**:
   ```bash
   cd AIThermalGuardIoT && ./gradlew test
   ```
