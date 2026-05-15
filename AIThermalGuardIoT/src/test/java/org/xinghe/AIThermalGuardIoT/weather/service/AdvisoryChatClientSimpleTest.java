package org.xinghe.AIThermalGuardIoT.weather.service;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.xinghe.AIThermalGuardIoT.common.ai.LlmProviderRegistry;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AdvisoryChatClientSimpleTest {

    private static final Logger log = LoggerFactory.getLogger(AdvisoryChatClientSimpleTest.class);

    @Autowired
    private LlmProviderRegistry registry;

    @Test
    void shouldCallChatClientAndGetResponse() {
        ChatClient client = registry.getChatClient("deepseek");

        String content = client.prompt()
            .user("Say exactly this and nothing else: HELLO_OK")
            .call()
            .content();

        log.info("LLM response: {}", content);
        assertNotNull(content, "Response content should not be null");
        assertTrue(content.contains("HELLO_OK"), "Response should contain HELLO_OK, got: " + content);
    }
}
