package org.xinghe.AIThermalGuardIoT.common.ai;


import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xinghe.AIThermalGuardIoT.common.config.LlmProviderProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing and caching LLM providers.
 * Supports dynamic creation of ChatClient based on provider configurations.
 */
@Component
@Slf4j
public class LlmProviderRegistry {

    private final LlmProviderProperties properties;
    private final Map<String, ChatClient> clientCache = new ConcurrentHashMap<>();
    private final Map<String, EmbeddingModel> embeddingModelCache = new ConcurrentHashMap<>();
    private final ToolCallingManager toolCallingManager;
    private final ObservationRegistry observationRegistry;
    private static final Map<String, String> RECOMMENDED_EMBEDDING_MODELS = Map.of(
        "dashscope", "text-embedding-v3",
        "glm", "embedding-3",
        "zhipu", "embedding-3",
        "baidu", "Embedding-V1",
        "minimax", "embo-01"
    );

    @Autowired
    public LlmProviderRegistry(
            LlmProviderProperties properties,
            @Autowired(required = false) ToolCallingManager toolCallingManager,
            @Autowired(required = false) ObservationRegistry observationRegistry) {
        this.properties = properties;
        this.toolCallingManager = toolCallingManager;
        this.observationRegistry = observationRegistry;
    }


    /**
     * 获取指定 Provider 的 {@link ChatClient}。
     * <p>
     * 该方法会优先命中内部缓存；若缓存不存在，则基于 Provider 配置即时构建并写入缓存。
     *
     * @param providerId Provider 唯一标识（例如 {@code dashscope}、{@code lmstudio}）
     * @return 可复用的 {@link ChatClient} 实例
     * @throws IllegalArgumentException 当 Provider 不存在或不可用时抛出
     */
    public ChatClient getChatClient(String providerId) {
        return clientCache.computeIfAbsent(providerId, id -> {
            log.info("[LlmProviderRegistry] Creating new client for provider: {}", id);
            return createChatClient(id);
        });
    }


    /**
     * 为指定 Provider 构建标准聊天客户端（带默认工具与 Advisor 组合）。
     *
     * @param providerId Provider 唯一标识
     * @return 构建完成的 {@link ChatClient}
     */
    private ChatClient createChatClient(String providerId) {
        OpenAiChatModel chatModel = buildChatModel(providerId);

        ChatClient.Builder builder = ChatClient.builder(chatModel);
        List<Advisor> advisors = buildDefaultAdvisors(providerId);
        if (!advisors.isEmpty()) {
            builder.defaultAdvisors(advisors.toArray(new Advisor[0]));
            log.info("[LlmProviderRegistry] Applied {} advisors for provider {}", advisors.size(), providerId);
        }

        return builder.build();
    }


    /**
     * 基于LlmProviderProperties构建底层 {@link OpenAiChatModel}。
     *
     * @param providerId Provider 唯一标识
     * @return 可用于 {@link ChatClient} 的聊天模型实例
     * @throws IllegalArgumentException 当 Provider 不存在时抛出
     */
    private OpenAiChatModel buildChatModel(String providerId) {
        LlmProviderProperties.ProviderConfig providerConfig = properties.getProviders().get(providerId);

        String baseUrl = providerConfig.getBaseUrl();
        String apiKey = providerConfig.getApiKey();
        String model = providerConfig.getModel();
        Double temperature = providerConfig.getTemperature();
        log.info("[LlmProviderRegistry] Building ChatModel - Provider: {}, BaseUrl: {}, Model: {}",
                providerId, baseUrl, model);

        OpenAiApi openAiApi = ApiPathResolver.buildOpenAiApi(baseUrl, apiKey);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature!= null ? temperature: 0.2)
                .build();

        return new OpenAiChatModel(
                openAiApi,
                options,
                toolCallingManager,
                RetryUtils.DEFAULT_RETRY_TEMPLATE,
                observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP
        );
    }




    /**
     * 根据全局 Advisor 配置生成默认 Advisor 列表。
     *
     * @param providerId Provider 唯一标识，仅用于日志上下文
     * @return 已按配置筛选的 Advisor 列表；若功能未启用则返回空列表
     */
    private List<Advisor> buildDefaultAdvisors(String providerId) {
        LlmProviderProperties.AdvisorConfig config = properties.getAdvisors();
        if (config == null || !config.isEnabled()) {
            return List.of();
        }

        List<Advisor> advisors = new ArrayList<>();

        if (config.isToolCallEnabled()) {
            if (toolCallingManager != null) {
                advisors.add(buildToolCallAdvisor(
                    config.isToolCallConversationHistoryEnabled(),
                    config.isStreamToolCallResponses()));
            } else {
                log.warn("[LlmProviderRegistry] ToolCallAdvisor skipped: ToolCallingManager unavailable, provider={}", providerId);
            }
        }

        if (config.isMessageChatMemoryEnabled()) {
            int maxMessages = Math.max(20, config.getMessageChatMemoryMaxMessages());
            MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(
                MessageWindowChatMemory.builder()
                    .maxMessages(maxMessages)
                    .build()
            ).build();
            advisors.add(memoryAdvisor);
        }

        if (config.isSimpleLoggerEnabled()) {
            advisors.add(new SimpleLoggerAdvisor());
        }

        buildSafeGuardAdvisor().ifPresent(advisors::add);

        return advisors;
    }

    /**
     * 构建 ToolCallAdvisor。
     *
     * @param conversationHistoryEnabled 是否启用工具调用会话历史
     * @param streamToolCallResponses 是否启用工具调用流式响应
     * @return ToolCallAdvisor 实例
     */
    private ToolCallAdvisor buildToolCallAdvisor(boolean conversationHistoryEnabled,
                                                  boolean streamToolCallResponses) {
        return ToolCallAdvisor.builder()
            .toolCallingManager(toolCallingManager)
            .conversationHistoryEnabled(conversationHistoryEnabled)
            .streamToolCallResponses(streamToolCallResponses)
            .build();
    }

    /**
     * 按配置构建敏感词防护 Advisor。
     *
     * @return 启用时返回 {@link SafeGuardAdvisor}，未启用时返回空
     */
    private Optional<SafeGuardAdvisor> buildSafeGuardAdvisor() {
        LlmProviderProperties.AdvisorConfig config = properties.getAdvisors();
        if (config == null || !config.isSafeguardEnabled()) {
            return Optional.empty();
        }
        SafeGuardAdvisor advisor = SafeGuardAdvisor.builder()
            .sensitiveWords(config.getSafeguardWords())
            .failureResponse("抱歉，我只能协助面试相关的任务。")
            .order(100)
            .build();
        return Optional.of(advisor);
    }


    /**
     * 判断字符串是否为 {@code null} 或空白。
     *
     * @param value 待判断字符串
     * @return 为空白返回 {@code true}，否则返回 {@code false}
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }






}
