package org.xinghe.AIThermalGuardIoT.weather.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.xinghe.AIThermalGuardIoT.common.ai.LlmProviderRegistry;
import org.xinghe.AIThermalGuardIoT.common.ai.StructuredOutputInvoker;
import org.xinghe.AIThermalGuardIoT.common.exception.ErrorCode;
import org.xinghe.AIThermalGuardIoT.weather.dto.AdvisoryOutput;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AdvisoryPromptTemplateTest {

    private static final Logger log = LoggerFactory.getLogger(AdvisoryPromptTemplateTest.class);

    @Autowired
    private LlmProviderRegistry registry;

    @Autowired
    private StructuredOutputInvoker invoker;

    private ChatClient client;

    @BeforeEach
    void setUp() {
        client = registry.getChatClient("deepseek");
    }

    // ---- prompt template (identical to AdvisoryScheduler.buildPrompt) ----

    private String buildPrompt(double temp, double humidity, double pressure, double lux, String alerts) {
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
            temp, humidity, pressure, lux, alerts
        );
    }

    private AdvisoryOutput callLlm(String prompt) {
        BeanOutputConverter<AdvisoryOutput> converter = new BeanOutputConverter<>(AdvisoryOutput.class);
        return invoker.invoke(
            client, prompt, prompt, converter,
            ErrorCode.WEATHER_ADVISORY_GENERATION_FAILED,
            "LLM failed: ",
            "prompt-template-test",
            log
        );
    }

    // ---- test scenarios ----

    @Test
    @DisplayName("Scenario 1: Pleasant day — mild, moderate humidity")
    void pleasantMildDay() {
        String prompt = buildPrompt(22.5, 55, 1017, 8000, "[]");

        AdvisoryOutput output = callLlm(prompt);

        log.info("=== Pleasant Mild Day ===");
        log.info("risk_level:    {}", output.riskLevel());
        log.info("summary:       {}", output.summary());
        log.info("recommendation: {}", output.recommendation());

        assertNotNull(output.riskLevel());
        assertNotNull(output.summary());
        assertNotNull(output.recommendation());
        assertTrue(List.of("LOW", "MODERATE").contains(output.riskLevel()),
            "Pleasant day should be LOW or MODERATE, got: " + output.riskLevel());
        assertTrue(output.summary().length() > 10, "Summary too short");
        assertTrue(output.recommendation().length() > 20, "Recommendation too short");
    }

    @Test
    @DisplayName("Scenario 2: Hot & humid — near threshold, with alert")
    void hotAndHumidWithAlert() {
        String prompt = buildPrompt(38.5, 82, 1005, 60000, "[\"HIGH_TEMP:41.2\"]");

        AdvisoryOutput output = callLlm(prompt);

        log.info("=== Hot & Humid With Alert ===");
        log.info("risk_level:    {}", output.riskLevel());
        log.info("summary:       {}", output.summary());
        log.info("recommendation: {}", output.recommendation());

        assertNotNull(output.riskLevel());
        assertTrue(List.of("MODERATE", "HIGH", "EXTREME").contains(output.riskLevel()),
            "Hot/humid day should be MODERATE/HIGH/EXTREME, got: " + output.riskLevel());
        assertTrue(output.recommendation().length() > 20);
    }

    @Test
    @DisplayName("Scenario 3: Storm warning — low pressure")
    void stormWarningLowPressure() {
        String prompt = buildPrompt(15.0, 70, 992, 3000, "[\"LOW_PRESSURE:992\"]");

        AdvisoryOutput output = callLlm(prompt);

        log.info("=== Storm Warning ===");
        log.info("risk_level:    {}", output.riskLevel());
        log.info("summary:       {}", output.summary());
        log.info("recommendation: {}", output.recommendation());

        assertNotNull(output.riskLevel());
        assertTrue(output.summary().length() > 10, "Summary too short");
        assertTrue(output.recommendation().length() > 20);
    }

    @Test
    @DisplayName("Scenario 4: Cold & dark night")
    void coldDarkNight() {
        String prompt = buildPrompt(3.0, 45, 1022, 5, "[\"LOW_TEMP:3.0\"]");

        AdvisoryOutput output = callLlm(prompt);

        log.info("=== Cold Dark Night ===");
        log.info("risk_level:    {}", output.riskLevel());
        log.info("summary:       {}", output.summary());
        log.info("recommendation: {}", output.recommendation());

        assertNotNull(output.riskLevel());
        assertTrue(output.recommendation().length() > 20);
    }

    @Test
    @DisplayName("Scenario 5: Moderate — comfortable conditions")
    void moderateComfortable() {
        String prompt = buildPrompt(24.0, 48, 1015, 12000, "[]");

        AdvisoryOutput output = callLlm(prompt);

        log.info("=== Moderate Comfortable ===");
        log.info("risk_level:    {}", output.riskLevel());
        log.info("summary:       {}", output.summary());
        log.info("recommendation: {}", output.recommendation());

        assertEquals("LOW", output.riskLevel(), "Comfortable day should be LOW risk");
        assertTrue(output.recommendation().length() > 20);
    }
}
