# AIThermalGuardIoT

Spring Boot 4.0 backend server for the PiicoDev Weather Station — receives sensor data from Raspberry Pi, persists to PostgreSQL, generates AI-powered safety advisories via Spring AI (DeepSeek), and serves a real-time dashboard with SSE + Chart.js.

## Requirements

- **Java 21**
- **PostgreSQL** (localhost:5432)
- **Redis** (localhost:6379)
- **DeepSeek API Key** (free, sign up at [platform.deepseek.com](https://platform.deepseek.com))

S3-compatible storage (MinIO / RustFS) is optional — the server starts without it.

## Quick Start

```bash
# 1. Create the .env file (see below)
cp .env.example .env
vim .env   # fill in your API keys

# 2. Build and run
./gradlew bootRun
```

The server starts on **http://localhost:8080**. Open **http://localhost:8080/dashboard.html** to see the real-time weather dashboard.

## .env Configuration

Create `AIThermalGuardIoT/.env` with the following variables. The `bootRun` and `test` Gradle tasks load this file automatically — no external plugin needed.

### Required

```bash
# DeepSeek API Key — get one free at https://platform.deepseek.com/
PROVIDER_DEEPSEEK_API_KEY=sk-your-key-here
PROVIDER_DEEPSEEK_MODEL=deepseek-v4-flash
```

### Database (defaults work if you use the docker-compose values below)

```bash
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=interview_guide
POSTGRES_USER=postgres
POSTGRES_PASSWORD=123456

REDIS_HOST=localhost
REDIS_PORT=6379
```

### Optional — Additional LLM Providers

```bash
# Alibaba Bailian / DashScope
AI_BAILIAN_API_KEY=sk-your-key

# Kimi / Moonshot
PROVIDER_KIMI_API_KEY=sk-your-key

# Zhipu / GLM
PROVIDER_GLM_API_KEY=your-key
```

### Optional — S3 Storage (MinIO / RustFS)

```bash
APP_STORAGE_ENDPOINT=http://localhost:9000
APP_STORAGE_ACCESS_KEY=minioadmin
APP_STORAGE_SECRET_KEY=minioadmin
APP_STORAGE_BUCKET=interview-guide
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/weather/record` | Receive sensor data from Raspberry Pi |
| `GET` | `/api/weather/advisories?page=1&size=20` | Paginated AI advisory history |
| `GET` | `/api/weather/stream` | SSE real-time stream (init / update / advisory events) |
| `GET` | `/dashboard.html` | Real-time dashboard with Chart.js |

### POST /api/weather/record

```json
{
  "station_id": "pi-weather-01",
  "temperature": 22.5,
  "humidity": 55.0,
  "pressure": 1017.0,
  "lux": 8000.0,
  "alerts": []
}
```

### Weather Simulator

A shell script in the project root simulates a Raspberry Pi sending data through 5 weather scenarios:

```bash
./simulate_weather.sh                        # default: localhost:8080
./simulate_weather.sh http://host:8080/api/weather/record  # custom endpoint
```

## Project Structure

```
AIThermalGuardIoT/
├── build.gradle                  # Gradle config, .env loading
├── .env                          # Secrets (gitignored)
├── simulate_weather.sh           # Pi simulator script (project root)
└── src/
    ├── main/java/org/xinghe/AIThermalGuardIoT/
    │   ├── common/               # AI, async, exception, result wrappers
    │   ├── infrastructure/       # Redis, S3, file services
    │   └── weather/              # Weather module
    │       ├── controller/       # REST + SSE endpoints
    │       ├── dto/              # Request/response records
    │       ├── model/            # JPA entities
    │       ├── repository/       # Spring Data repos
    │       └── service/          # Business logic + LLM scheduler
    └── main/resources/
        ├── application.yaml      # Spring config
        └── static/               # Dashboard frontend (HTML/CSS/JS)
```

## Running Tests

```bash
./gradlew test                                    # all tests
./gradlew test --tests "org.xinghe.AIThermalGuardIoT.weather.service.AdvisoryPromptTemplateTest"
```

Tests that call the LLM need a valid `PROVIDER_DEEPSEEK_API_KEY` in `.env`.

## Architecture

- **Data flow**: Raspberry Pi → `POST /api/weather/record` → PostgreSQL → `AdvisoryScheduler` (every 2 min) → DeepSeek LLM → `weather_advisories` table → SSE broadcast to dashboard
- **SSE events**: `init` (recent 20 records), `update` (each new record), `advisory` (each AI analysis)
- **AI**: Prompt-template based structured output via `BeanOutputConverter<AdvisoryOutput>`, with automatic retry and JSON repair
