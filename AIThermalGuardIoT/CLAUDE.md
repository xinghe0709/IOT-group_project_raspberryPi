# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build/Run

```bash
# Build
./gradlew build

# Run (loads .env automatically via build.gradle bootRun)
./gradlew bootRun

# Run a single test
./gradlew test --tests "org.xinghe.AIThermalGuardIoT.SomeTest"

# Run all tests
./gradlew test
```

The project uses Gradle 9.4.1 with Java 21, Spring Boot 4.0.1, and Spring AI 2.0.0-M4.

## Architecture

Package layout:

```
org.xinghe.AIThermalGuardIoT
├── common.ai        — LLM provider registry, structured output, prompt security, SkillsTool
├── common.async     — Redis Stream producer/consumer templates for async tasks
├── common.config    — Spring @ConfigurationProperties + @Configuration beans
├── common.constant  — AsyncTaskStreamConstants, CommonConstants
├── common.exception — BusinessException, ErrorCode enum, GlobalExceptionHandler
├── common.result    — Result<T> unified response wrapper
└── infrastructure   — RedisService, FileStorageService, document parsing, file validation
```

## Key Patterns

### LLM Provider System

All LLM calls go through `LlmProviderRegistry`, which creates `OpenAiApi`-backed `ChatClient` instances from `app.ai.providers.*` config entries. Each provider is cached by ID. The default provider is controlled by `app.ai.default-provider`.

To use a specific provider: `llmProviderRegistry.getChatClient("deepseek")`.

`ApiPathResolver.buildOpenAiApi()` detects versioned base URLs (e.g., ending in `/v1`) and switches to short paths to avoid prefix duplication.

`StructuredOutputInvoker` wraps `BeanOutputConverter` with retry logic, JSON repair for unescaped quotes, structured metrics, and anti-prompt-injection guard appended to system prompts.

### Async Task Queue (Redis Streams)

`AbstractStreamProducer<T>` and `AbstractStreamConsumer<T>` form a Redis Stream-based task queue pattern. Subclass and implement the abstract methods to create typed producer/consumer pairs. The consumer template handles: group creation, polling loop, retry (up to 3 times), ACK, and final failure marking.

### Configuration Properties Binding

Multiple `@ConfigurationProperties` classes bind under the `app.*` prefix:
- `app.ai` → `LlmProviderProperties` (providers, advisors, RAG, structured output)
- `app.ai.agent-utils` → `AgentUtilsProperties` (skills root)
- `app.ai` → `StructuredOutputProperties` (retry/max-attempts)
- `app.storage` → `StorageConfigProperties` (S3-compatible endpoint)
- `app.cors` → `CorsProperties` (allowed origins)

Note: `LlmProviderProperties` and `StructuredOutputProperties` share the `app.ai` prefix — this is intentional, they bind different property subsets.

### .env Loading

`bootRun` task in `build.gradle` parses `.env` in the project root and exports each line as a JVM environment variable. Supports quoted values and `#` comments. No external plugin needed.

## Runtime Dependencies

| Service | Default | Config Key |
|---------|---------|------------|
| PostgreSQL | localhost:5432 | `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_PASSWORD` |
| Redis | localhost:6379 | `REDIS_HOST`, `REDIS_PORT` |
| S3 (RustFS) | localhost:9000 | `APP_STORAGE_ENDPOINT`, `APP_STORAGE_ACCESS_KEY`, `APP_STORAGE_SECRET_KEY` |

## Key Conventions

- All exceptions extend `BusinessException` with an `ErrorCode` enum value
- `GlobalExceptionHandler` returns HTTP 200 for all exceptions, using the `Result.error()` body to signal errors — the client inspects `code` rather than HTTP status
- Exceptions thrown by `@ExceptionHandler` methods that match a specific type must use `@ExceptionHandler(SpecificType.class)`, not the generic `Exception.class`
- Springdoc `packages-to-scan` must match `org.xinghe.AIThermalGuardIoT` for Swagger to discover controllers
- `@Bean("visaSkillsToolCallback")` in `AgentUtilsConfiguration` is the canonical SkillsTool bean name — `@Qualifier` references must match
