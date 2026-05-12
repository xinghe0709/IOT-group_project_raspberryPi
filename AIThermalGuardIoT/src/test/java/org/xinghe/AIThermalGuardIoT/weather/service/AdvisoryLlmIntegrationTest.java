package org.xinghe.AIThermalGuardIoT.weather.service;

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

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 集成测试：验证真实的 LLM 调用链 (LlmProviderRegistry → StructuredOutputInvoker → BeanOutputConverter)
 * 需要 PostgreSQL + Redis 运行
 */
@SpringBootTest
class AdvisoryLlmIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AdvisoryLlmIntegrationTest.class);

    @Autowired
    private LlmProviderRegistry registry;

    @Autowired
    private StructuredOutputInvoker invoker;

    @Test
    void shouldCallDeepseekAndParseJson() {
        assertNotNull(registry, "LlmProviderRegistry should be autowired");
        assertNotNull(invoker, "StructuredOutputInvoker should be autowired");

        ChatClient client = registry.getChatClient("deepseek");
        assertNotNull(client, "ChatClient for deepseek should exist");

        String prompt = """
            You are a helpful weather assistant. Respond with a JSON object:
            {"risk_level":"LOW","summary":"Nice day","recommendation":"Go outside and enjoy"}
            Return ONLY the JSON, no markdown, no explanation.
            """;

        BeanOutputConverter<AdvisoryOutput> converter = new BeanOutputConverter<>(AdvisoryOutput.class);

        AdvisoryOutput output = invoker.invoke(
            client, prompt, prompt, converter,
            ErrorCode.WEATHER_ADVISORY_GENERATION_FAILED,
            "LLM call failed: ",
            "integration-test",
            log
        );

        assertNotNull(output, "AdvisoryOutput should not be null");
        log.info("SUCCESS: riskLevel={}, summary={}, recommendation={}",
            output.riskLevel(), output.summary(), output.recommendation());
    }
}
