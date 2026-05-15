# AIThermalGuardIoT

Spring Boot 4.0 backend server for the PiicoDev Weather Station — receives sensor data from Raspberry Pi, persists to PostgreSQL, generates AI-powered safety advisories via Spring AI (DeepSeek), and serves a real-time dashboard with SSE + Chart.js.

## Requirements

- **Java 21**
- **PostgreSQL** (localhost:5432)
- **DeepSeek API Key** (free, sign up at [platform.deepseek.com](https://platform.deepseek.com))

## Quick Start

```bash
# 1. Create the .env file (see below)
cd AIThermalGuardIoT
cp .env.example .env   # if a template exists, or create from scratch
vim .env                # fill in your API keys

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

### Database

```bash
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=weather_station
POSTGRES_USER=postgres
POSTGRES_PASSWORD=123456
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

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/weather/record` | Receive sensor data from Raspberry Pi |
| `GET` | `/api/weather/records?from=&to=&aggregation=` | Aggregated historical data (hourly/daily) |
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
├── README.md                     # This file
├── simulate_weather.sh           # Pi simulator script
├── weather_station.py            # Raspberry Pi sensor code
└── AIThermalGuardIoT/            # Java backend server
    ├── build.gradle              # Gradle config, .env loading
    ├── .env                      # Secrets (gitignored)
    └── src/
        ├── main/java/org/xinghe/AIThermalGuardIoT/
        │   ├── common/           # AI, config, exception, result wrappers
        │   └── weather/          # Weather module
        │       ├── controller/   # REST + SSE endpoints
        │       ├── dto/          # Request/response records
        │       ├── model/        # JPA entities
        │       ├── repository/   # Spring Data repos
        │       └── service/      # Business logic + LLM scheduler
        └── main/resources/
            ├── application.yaml  # Spring config
            └── static/           # Dashboard frontend (HTML/CSS/JS)
```

## Running Tests

```bash
cd AIThermalGuardIoT
./gradlew test                                    # all tests
./gradlew test --tests "org.xinghe.AIThermalGuardIoT.weather.service.AdvisoryPromptTemplateTest"
```

Tests that call the LLM need a valid `PROVIDER_DEEPSEEK_API_KEY` in `.env`.

## Architecture

- **Data flow**: Raspberry Pi → `POST /api/weather/record` → PostgreSQL → `AdvisoryScheduler` (every 2 min) → DeepSeek LLM → `weather_advisories` table → SSE broadcast to dashboard
- **SSE events**: `init` (recent 20 records), `update` (each new record), `advisory` (each AI analysis)
- **AI**: Prompt-template based structured output via `BeanOutputConverter<AdvisoryOutput>`, with automatic retry and JSON repair
- **Runtime dependency**: PostgreSQL only — Redis, S3, and other infrastructure have been removed as unnecessary for weather station operation
