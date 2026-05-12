package org.xinghe.AIThermalGuardIoT.weather.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.xinghe.AIThermalGuardIoT.common.ai.LlmProviderRegistry;
import org.xinghe.AIThermalGuardIoT.common.ai.StructuredOutputInvoker;
import org.xinghe.AIThermalGuardIoT.common.exception.ErrorCode;
import org.xinghe.AIThermalGuardIoT.weather.dto.AdvisoryOutput;

/**
 * 诊断测试：验证真实 LLM 连接（需要 .env 提供 API Key + 网络可达）
 * 排除 @SpringBootTest 以减少启动时间，手动构造关键 Bean。
 */
public class AdvisoryLlmConnectivityTest {

    public static void main(String[] args) {
        System.out.println("=== LLM Connectivity Diagnostic ===");
        System.out.println("PROVIDER_DEEPSEEK_API_KEY = " + mask(System.getenv("PROVIDER_DEEPSEEK_API_KEY")));
        System.out.println("PROVIDER_DEEPSEEK_MODEL    = " + System.getenv("PROVIDER_DEEPSEEK_MODEL"));

        // 1. Simulate what bootRun would do
        String apiKey = System.getenv("PROVIDER_DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("FATAL: PROVIDER_DEEPSEEK_API_KEY not set. Check .env loading in build.gradle");
            System.exit(1);
        }

        System.out.println("API key loaded ✓");

        // 2. Try direct HTTP call via Java
        try {
            var url = new java.net.URI("https://api.deepseek.com/v1/chat/completions").toURL();
            var conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);

            String body = """
                {"model":"deepseek-v4-flash","messages":[{"role":"user","content":"Say hello"}],"max_tokens":10}
                """;
            conn.getOutputStream().write(body.getBytes());

            int code = conn.getResponseCode();
            String response = new String(
                code >= 400 ? conn.getErrorStream().readAllBytes() : conn.getInputStream().readAllBytes()
            );
            System.out.println("HTTP " + code + ": " + response.substring(0, Math.min(200, response.length())));
        } catch (Exception e) {
            System.err.println("Direct HTTP call failed: " + e);
            e.printStackTrace();
        }
    }

    private static String mask(String s) {
        if (s == null) return "<null>";
        if (s.length() <= 8) return "***";
        return s.substring(0, 4) + "****" + s.substring(s.length() - 4);
    }
}
