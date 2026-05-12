# Weather Station Backend — Design Spec

**Date:** 2026-05-12 | **Status:** Draft

## Overview

为 PiicoDev 气象站搭建 Java 后端服务器，实现数据持久化、AI 环境分析、前端仪表盘三大功能。单树莓派低并发场景，基于 `AIThermalGuardIoT/` 现有 Spring Boot 框架扩展。

## Architecture

```
树莓派 (Python)
    │  POST /api/weather/record  (every 30s)
    ▼
Spring Boot (Java 21, Gradle)
    │
    ├─ WeatherController       接收传感器数据 → 写入 DB
    ├─ AdvisoryScheduler       @Scheduled(2min) → 取最新 record → LLM → 写入 advisories
    ├─ AdvisoryController      GET 分页查询建议列表
    ├─ DashboardController     SSE /api/weather/stream
    ├─ StaticResources         GET /dashboard.html
    │
    ▼
PostgreSQL
    ├─ weather_records    (sensor data, 1 row/30s)
    └─ weather_advisories (AI analysis, 1 row/2min, FK→record nullable)
```

**Data flow:** Record ingest → Advisory generation → SSE push → Frontend render.

## Data Model

### weather_records

| Column       | Type                  | Notes                          |
|-------------|-----------------------|---------------------------------|
| id          | BIGSERIAL PK          |                                 |
| station_id  | VARCHAR(50) NOT NULL  | "pi-weather-01"                |
| temperature | DOUBLE PRECISION      | nullable (sensor may fail)      |
| humidity    | DOUBLE PRECISION      | nullable                        |
| pressure    | DOUBLE PRECISION      | nullable                        |
| lux         | DOUBLE PRECISION      | nullable                        |
| alerts      | VARCHAR(500)          | serialized JSON array string    |
| created_at  | TIMESTAMPTZ NOT NULL  | DEFAULT NOW()                   |

Index: `idx_records_created_at` (DESC), `idx_records_station_id`.

### weather_advisories

| Column         | Type                  | Notes                        |
|---------------|-----------------------|------------------------------|
| id            | BIGSERIAL PK          |                              |
| record_id     | BIGINT FK→records(id) | nullable, only analyzed rows |
| risk_level    | VARCHAR(20) NOT NULL  | LOW/MODERATE/HIGH/EXTREME    |
| summary       | TEXT NOT NULL         | one-sentence environment desc|
| recommendation| TEXT NOT NULL         | 1-3 warm, personable sentences|
| raw_response  | TEXT                  | LLM raw output (debug)       |
| created_at    | TIMESTAMPTZ NOT NULL  | DEFAULT NOW()                 |

JPA: `WeatherRecord` ← `@OneToOne(mappedBy = "record")` → `WeatherAdvisory`.

## API Design

### POST /api/weather/record

Ingest sensor data from Raspberry Pi.

Request:
```json
{
    "station_id": "pi-weather-01",
    "timestamp": "2026-05-12T14:30:00+0800",
    "temperature": 25.3,
    "humidity": 62.0,
    "pressure": 1013.0,
    "lux": 4500.0,
    "alerts": ["HIGH_TEMP:41.2"]
}
```

Response: `Result<Void>` — `{"code": 200, "message": "success", "data": null}`

Validation: `station_id` required non-blank. Numeric fields nullable. `created_at` set server-side via `NOW()`, client `timestamp` ignored.

After insert: broadcast via SseEmitter to all connected dashboard clients as `event:update`.

### GET /api/weather/advisories?page=1&size=20

Paginated advisory history. Returns `Result<Page<AdvisoryDto>>`. Each `AdvisoryDto` includes nested `record` (temperature/humidity/pressure/lux/created_at).

### GET /api/weather/stream

SSE endpoint. Emits three event types:

| Event      | Trigger                 | Data                            |
|------------|-------------------------|---------------------------------|
| `init`     | Client connect          | Last 20 records (JSON array)    |
| `update`   | New record inserted     | Single record (JSON object)     |
| `advisory` | New advisory generated  | Single advisory with nested record |

### GET /dashboard.html

Static HTML served from `src/main/resources/static/dashboard.html`.

## LLM Integration

### Prompt Template

Humanized tone via role anchoring + concrete example + behavioral rules:

```
You are a warm, thoughtful weather companion. You care about people's comfort and safety like a considerate friend would. Speak naturally.

Current reading:
Temperature: %.1f°C / Humidity: %.0f%% / Pressure: %.0f hPa / Light: %.0f lux / Alerts: %s

Respond with JSON:
{
  "risk_level": "LOW"|"MODERATE"|"HIGH"|"EXTREME",
  "summary": friendly one-sentence environment description,
  "recommendation": 1-3 warm sentences, conversational tone, no bullet points, no jargon
}

Rules: contractions (it's, don't), light humor when safe, serious when dangerous, real-world impacts not technical terms.
```

### Invocation Chain

```
AdvisoryScheduler (@Scheduled every 2min)
  → repository.findTopByOrderByCreatedAtDesc()
  → chatClient = llmProviderRegistry.getChatClient("deepseek")
  → BeanOutputConverter<AdvisoryOutput>
  → structuredOutputInvoker.invoke(chatClient, prompt, prompt, converter, ERROR_CODE, "AI分析失败: ", "weather-advisory", log)
  → AdvisoryOutput entity
  → new WeatherAdvisory(...) → save to DB
  → broadcast via SseEmitter (event:advisory)
```

Failure handling: exception caught by `@Scheduled` → log error → retry next cycle. SSE does not receive advisory event, frontend unaffected.

### Structured Output

`AdvisoryOutput` record:
```java
public record AdvisoryOutput(
    @JsonProperty("risk_level") String riskLevel,
    @JsonProperty("summary") String summary,
    @JsonProperty("recommendation") String recommendation
) {}
```

`StructuredOutputInvoker` handles: JSON repair (unescaped quotes), retry with error context (max 2 attempts), anti-injection guard, Micrometer metrics. All retries exhausted → `BusinessException`.

### Provider

Uses configured default provider (deepseek, `app.ai.default-provider`). API key from `.env`. Changeable via `application.yaml` without code change.

## Frontend Design

### Aesthetic: "Warm Instrumentation"

Not a cold monitoring dashboard — a human-facing climate awareness window. Precise like an instrument, readable like a newspaper.

**Color palette:**
- Background: `#1a1c1e` (warm dark)
- Card: `#242629`
- Text: `#e8d5c4` (cream)
- Temperature line: `#ff6b35` (warm orange)
- Humidity line: `#4ecdc4` (teal)
- Pressure line: `#f7dc6f` (amber)
- Light line: `#ffeaa7` (pale gold)
- HIGH alert: `#e74c3c`

**Typography:**
- Nav/title: `DM Mono` (monospace instrument feel)
- Data values: `Space Mono`
- Advisory text: `Crimson Text` (serif, warm, human — mirrors the LLM persona)

### Layout: Sidebar + Content

```
Sidebar (200px fixed)         Content Area
┌──────────┬──────────────────────────────┐
│  Logo    │                              │
│          │  Current readings (4 cards)   │
│  ◆ Dash  │  Combined trend chart (4-in-1)│
│          │  Latest advisory card         │
│  ◇ Advi- │                              │
│    sory  │                              │
└──────────┴──────────────────────────────┘
```

**Dashboard Page:**
- 4 current-reading cards (temp/humidity/pressure/lux)
- Active alerts banner (red pulse if alerts exist)
- Combined trend chart (4 lines, dual Y-axis: left linear, right log for lux)
- Latest advisory card with risk_level color bar

**Advisory History Page:**
- Chronological card list (newest first)
- Each card: colored left border (LOW=green, MODERATE=yellow, HIGH=orange, EXTREME=red), risk_level tag, timestamp, summary text
- Click to expand → full recommendation + associated sensor readings
- Infinite scroll or "load more" button

### Real-time Updates (SSE)

- `event:init` → populate chart with last 20 records
- `event:update` → append data point to all 4 lines, update current-reading cards
- `event:advisory` → toast notification slides in from top (warm gradient bar), advisory card prepends to latest-advisory section

### Tech Stack

| Item | Choice |
|------|--------|
| Chart library | Chart.js 4.x (CDN, ~70KB gzipped) |
| SSE client | Native `EventSource` API |
| Sidebar | Pure CSS + JS toggle |
| Toast | Custom CSS animation |
| Fonts | Google Fonts CDN (DM Mono, Space Mono, Crimson Text) |
| Deployment | `static/dashboard.html` + `static/app.js`, served by Spring Boot |

## Error Codes (12xxx segment)

| Code  | Enum                               | Message               |
|-------|------------------------------------|-----------------------|
| 12001 | WEATHER_RECORD_SAVE_FAILED         | 气象数据保存失败        |
| 12002 | WEATHER_ADVISORY_GENERATION_FAILED | AI环境分析生成失败      |
| 12003 | WEATHER_ADVISORY_NOT_FOUND         | 环境建议不存在          |
| 12004 | WEATHER_STATION_INVALID            | 气象站标识无效          |

## Data Retention

No automatic cleanup for now. Manual cleanup if storage grows. Both tables indexed on `created_at DESC` for query performance.

## Testing Strategy

- **Unit**: `WeatherRecordService.saveRecord()`, `AdvisoryScheduler` with mocked `LlmProviderRegistry`, DTO mapping
- **Integration**: `WeatherController` POST → verify DB insert + SSE broadcast; `AdvisoryController` pagination
- **LLM**: Mock `StructuredOutputInvoker` for deterministic tests; manual integration test with real API for prompt quality
- **Frontend**: Manual browser test with mock SSE events

## Open Questions

- None remaining from design discussion.
