package org.xinghe.AIThermalGuardIoT.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class LlmProviderProperties {
    private String defaultProvider = "deepseek";
    private String defaultEmbeddingProvider;
    private Integer embeddingDimensions = 1024;
    private Map<String, ProviderConfig> providers;
    private AdvisorConfig advisors = new AdvisorConfig();
    private String configYamlPath;
    private String configEnvPath;
    private SecurityConfig security = new SecurityConfig();

    @Data
    public static class ProviderConfig {
        private String baseUrl;
        private String apiKey;
        private String model;
        private String embeddingModel;
        private Integer embeddingDimensions;
        private Boolean supportsEmbedding;
        private Double temperature;
    }

    @Data
    public static class SecurityConfig {
        private String apiKeyEncryptionKey;
        private boolean requireEncryptionKey = false;
    }

    /**
     * Spring AI ChatClient 默认 Advisor 及独立 Prompt 清洗组件的开关与参数，
     *
     */
    @Data
    public static class AdvisorConfig {

        /** 总开关：为 false 时不向 ChatClient 注册任何 Advisor（不含 PromptSanitizer，其为单独 Bean）。 */
        private boolean enabled = true;

        /** 启用 ToolCallAdvisor：在对话链路中编排多轮工具/函数调用；无 ToolCallingManager 时该项不生效。 */
        private boolean toolCallEnabled = true;
        /** 工具调用上下文是否并入会话历史（影响 token 与延迟，按需开启）。 */
        private boolean toolCallConversationHistoryEnabled = false;
        /** 工具执行结果是否以流式形式回传；false 时为缓冲后再输出。 */
        private boolean streamToolCallResponses = false;

        /** 启用 MessageChatMemoryAdvisor，按会话维度滑动窗口保留近期消息（默认关闭，避免会话间上下文串扰）。 */
        private boolean messageChatMemoryEnabled = false;
        /** 滑动窗口最多保留的对话消息条数；注册 Advisor 时下限为 20。 */
        private int messageChatMemoryMaxMessages = 120;

        /** 启用 SimpleLoggerAdvisor：将 ChatClient 的请求/响应打到日志（默认关，调试或排障时再开）。 */
        private boolean simpleLoggerEnabled = false;

        /** 启用 SafeGuardAdvisor：用户 Prompt 命中敏感短语时短路并返回固定拒答文案，缓冲越狱类诱导。 */
        private boolean safeguardEnabled = true;
        /** SafeGuardAdvisor 匹配的敏感短语（常见「忽略前文 / 扮演新角色」等起手式，可按业务调整）。 */
        private List<String> safeguardWords = List.of(
            "I'll now act as",
            "Sure, I'll ignore",
            "我已经忽略",
            "新的角色是",
            "忽略之前的指令",
            "forget all previous instructions"
        );

        /**
         * 启用 PromptSanitizer：对拼进 Prompt 的用户文本做注入特征清洗（与 ChatClient Advisor 链无关），
         *
         */
        private boolean promptSanitizerEnabled = true;
    }
}
