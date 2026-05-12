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
                userPrompt,
                userPrompt,
                converter,
                ErrorCode.WEATHER_ADVISORY_GENERATION_FAILED,
                "AI分析失败: ",
                "weather-advisory",
                log
            );

            WeatherAdvisory advisory = WeatherAdvisory.builder()
                .record(latest)
                .riskLevel(output.riskLevel())
                .summary(output.summary())
                .recommendation(output.recommendation())
                .rawResponse("")
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
