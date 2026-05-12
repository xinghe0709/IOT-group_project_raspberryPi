# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Raspberry Pi weather monitoring system using PiicoDev I2C sensors (BME280 temperature/humidity/pressure, VEML6030 ambient light), an SSD1306 OLED display (128x64), and a 5V active buzzer for threshold alerts. The project also contains a separate ML assignment (CITS5508 softmax regression on MNIST) in `assignment1.ipynb`.

## Hardware Connections

- **I2C bus** (`i2c-1`): BME280, VEML6030, and SSD1306 OLED daisy-chained via PiicoDev cables
- **Buzzer**: GPIO 17 (BCM) — buzzer (+) to GPIO 17, (-) to GND
- Use `i2cdetect -y 1` to verify sensor addresses after wiring

## Dependencies

```
pip install PiicoDev RPi.GPIO
```

The PiicoDev package bundles `PiicoDev_BME280`, `PiicoDev_VEML6030`, and `PiicoDev_SSD1306`. On Raspberry Pi OS Bookworm and later, pip installs must happen inside a virtual environment (see `weather_station.py` docstring for setup steps).

## Main Script: `weather_station.py`

Architecture — a single-file application structured into sections:

1. **Configuration** (lines 195–235): Thresholds, timing, buzzer pin, HTTP POST endpoint/headers
2. **Buzzer setup** (lines 241–267): `buzzer_beep()` and `buzzer_alert()` for audible feedback patterns
3. **Sensor init** (lines 275–297): Each sensor initialises independently with try/except — `None` if unavailable, so the station degrades gracefully
4. **Sensor reading** (lines 304–327): `read_sensors()` returns a dict with keys `temp`, `humidity`, `pressure`, `lux` — values are `None` when a sensor is missing or fails
5. **Derived data** (lines 330–400): `get_weather_condition()` and `get_comfort_index()` produce human-readable descriptions
6. **Alert checking** (lines 403–428): `check_alerts()` compares readings against thresholds and triggers buzzer; `check_alerts_silent()` does the same without buzzer (used for JSON payload)
7. **HTTP POST** (lines 442–493): `send_data()` sends JSON to `API_ENDPOINT`, skipped if endpoint is empty
8. **Display screens** (lines 517–698): Seven screen functions (`screen_overview`, `screen_temperature`, `screen_humidity`, `screen_pressure`, `screen_light`, `screen_conditions`, `screen_post_status`) — each follows the pattern: `display.fill(0)`, `draw_header()`, render data, `display.show()`
9. **Main loop** (lines 704–797): Reads sensors at `READ_INTERVAL`, cycles display screens every `SCREEN_TIME`, POSTs data every `POST_INTERVAL`, checks alerts on each reading

Key design points:
- All three sensors and the display are optional — the loop continues if any fail to initialise
- `data` dict values are `None`-safe throughout: `format_value()` renders `--` for missing data
- The main loop uses a single `while True` with timed checkpoints, no threading

## Test Scripts

- `oled_test.py` — Verifies the SSD1306 OLED displays text and increments a counter
- `buzzer_test.py` — Verifies the buzzer beeps every 10 seconds on GPIO 17

Run them directly on the Pi (with venv activated): `python3 oled_test.py` / `python3 buzzer_test.py`

## 3D Printing Files

`files/` contains STEP files for an anemometer (base, cup, hub) — unrelated to the software but part of the weather station hardware build.

## Backend Server: `AIThermalGuardIoT/`

Spring Boot 4.0.1, Java 21, Gradle 9.4.1. PostgreSQL (JPA) + Redis (Redisson 4.0) + S3 兼容存储 (MinIO/RustFS via AWS SDK).

```bash
cd AIThermalGuardIoT
./gradlew build            # 构建
./gradlew bootRun           # 启动（自动加载 .env，监听 8080）
./gradlew test              # 全部测试（自动加载 .env）
./gradlew test --tests "org.xinghe.AIThermalGuardIoT.SomeTest"
```

**前置依赖**：PostgreSQL (localhost:5432) + Redis (localhost:6379)。`test` 和 `bootRun` 都自动解析 `.env` 注入 JVM 环境变量。

### 核心组件速查

| 组件 | 路径 | 要点 |
|------|------|------|
| `Result<T>` | `common/result/` | 统一响应 `{code, message, data}`；成功 `Result.success(data)`，失败 `Result.error(ErrorCode.XXX)` |
| `BusinessException` | `common/exception/` | 业务异常，携带 `ErrorCode` 枚举。`GlobalExceptionHandler` 对所有异常返回 HTTP 200，客户端检查 `code` 而非 HTTP 状态 |
| `ErrorCode` | `common/exception/` | 按模块分段枚举错误码（1xxx 通用, 2xxx 简历, 3xxx 面试, 4xxx 存储, 7xxx AI, 12xxx 气象站...） |
| `RedisService` | `infrastructure/redis/` | KV/Hash/分布式锁/Stream 消息队列/原子计数器。Stream 消息体为 `Map<String, String>` |
| `AbstractStreamConsumer<T>` | `common/async/` | Redis Stream 消费模板，自动处理组创建、轮询、ACK、重试（最多3次）、失败落库。`@PostConstruct` 启动守护线程 |
| `AbstractStreamProducer<T>` | `common/async/` | Redis Stream 生产模板，实现 `streamKey()`/`buildMessage()` 即可入队 |
| `FileStorageService` | `infrastructure/file/` | S3 兼容上传/下载/删除，中文文件名自动转拼音，按日期分目录。`StorageInitializer` 启动时 S3 不可用会降级 warn |
| `CorsConfig` | `common/config/` | 仅对 `/api/**` 生效，来源通过 `app.cors.allowed-origins` 配置 |

`@ConfigurationProperties` 统一 `app.*` 前缀：`app.ai`（LLM provider）、`app.storage`（S3）、`app.cors`（跨域）。

### Spring AI (`common/ai/`)

Spring AI 2.0.0-M4，OpenAI 兼容接口。`LlmProviderRegistry` 从 `app.ai.providers.*` 构建并缓存 `ChatClient`（默认 deepseek，预置 dashscope/lmstudio/kimi/glm）。每个 ChatClient 挂载：

- **SkillsTool** — 从 `classpath:skills/` 加载技能 MD 文件，当前只有占位 skill
- **FileSystemTools** — 给模型提供文件读写能力的 `@Tool` 方法回调（需显式 `ToolDefinition`）
- **Advisor 链**：`ToolCallAdvisor` → `MessageChatMemoryAdvisor`（滑动窗口 120 条）→ `SafeGuardAdvisor`（敏感词过滤，最后一道闸门）
- **Advisor 开关** (`app.ai.advisors.*`)：tool-call/message-memory/safeguard 默认启用，simple-logger 默认关闭

`StructuredOutputInvoker.invoke(chatClient, systemPrompt, userPrompt, converter, errorCode, errorPrefix, logContext, log)` 封装 `BeanOutputConverter`，含自动重试（默认 2 次）、JSON 修复、防注入、Metrics 打点。`ApiPathResolver` 处理 baseUrl 带版本号时的路径前缀。

### 气象站后端模块

Spec: `docs/superpowers/specs/2026-05-12-weather-backend-design.md`
Plan: `docs/superpowers/plans/2026-05-12-weather-backend-plan.md`

**数据模型**（JPA Entity, 双表）：

| 表 | 关键字段 | 说明 |
|----|---------|------|
| `weather_records` | id, station_id, temperature/humidity/pressure/lux, alerts(TEXT), created_at | 每 30s 一条 |
| `weather_advisories` | id, record_id(FK nullable), risk_level, summary, recommendation, raw_response, created_at | 每 2min 一条，仅被分析的 record 有关联 |

**API**：`POST /api/weather/record`（接收），`GET /api/weather/advisories?page=1&size=20`（历史建议分页），`GET /api/weather/stream`（SSE：`init`/`update`/`advisory`），`GET /dashboard.html`（静态面板）。

**LLM 分析**：`AdvisoryScheduler` `@Scheduled(2min)` 查最新 record → Prompt Template（拟人化角色 + 口语约定）→ `StructuredOutputInvoker.invoke()` → `BeanOutputConverter<AdvisoryOutput>` → 写 `weather_advisories` → SSE 推送 `advisory`。模型 `deepseek-v4-flash`（非 reasoning model，直接输出 JSON）。

**前端**：Chart.js 四合一线图（双 Y 轴：左线性 温度/湿度/气压，右对数 光照）+ 侧栏导航（Dashboard / Advisories）+ SSE 实时更新 + Toast 通知。字体 DM Mono / Space Mono / Crimson Text，暖色系配色。

**测试**：

| 测试 | 类型 | 说明 |
|------|------|------|
| `WeatherControllerTest` | 集成 | POST 接口参数校验（2 tests） |
| `AdvisorySchedulerTest` | 单元 (mock) | 调度器逻辑：正常/跳过/失败/Prompt 模板（4 tests） |
| `AdvisoryLlmIntegrationTest` | 集成 | 真实 LLM 调用链验证 |
| `AdvisoryPromptTemplateTest` | 集成 | 5 种天气场景验证 Prompt 输出质量 |
| `AdvisoryChatClientSimpleTest` | 集成 | ChatClient 连通性冒烟测试 |
| `AdvisoryLlmConnectivityTest` | 诊断 (main) | API key + 网络诊断，不依赖 Spring 上下文 |


