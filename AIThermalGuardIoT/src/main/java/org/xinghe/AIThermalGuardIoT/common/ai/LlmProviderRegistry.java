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
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.xinghe.AIThermalGuardIoT.common.config.LlmProviderProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springaicommunity.agent.tools.FileSystemTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallback;

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
    private final ToolCallback visaSkillsToolCallback;
    private final FileSystemTools fileSystemTools;
    private static final Map<String, String> RECOMMENDED_EMBEDDING_MODELS = Map.of(
        "dashscope", "text-embedding-v3",
        "glm", "embedding-3",
        "zhipu", "embedding-3",
        "baidu", "Embedding-V1",
        "minimax", "embo-01"
    );

    /**
     * 创建运行时主构造器，支持从数据库读取 Provider 配置并注入工具调用相关组件。
     *
     * @param properties LLM Provider 全量配置属性
     * @param toolCallingManager 工具调用管理器，可选；缺失时跳过 ToolCallAdvisor
     * @param observationRegistry 可观测性注册器，可选；缺失时使用 NOOP
     * @param visaSkillsToolCallback 技能工具回调，可选；缺失时不挂载默认工具回调
     * @param fileSystemTools 文件系统工具，可选；缺失时不注册文件读写工具
     */
    @Autowired
    public LlmProviderRegistry(
            LlmProviderProperties properties,
            @Autowired(required = false) ToolCallingManager toolCallingManager,
            @Autowired(required = false) ObservationRegistry observationRegistry,
            @Autowired(required = false) @Qualifier("visaSkillsToolCallback") ToolCallback visaSkillsToolCallback,
            @Autowired(required = false) @Qualifier("visaFileSystemToolsCallback") FileSystemTools fileSystemTools) {
        this.properties = properties;
        this.toolCallingManager = toolCallingManager;
        this.observationRegistry = observationRegistry;
        this.visaSkillsToolCallback = visaSkillsToolCallback;
        this.fileSystemTools = fileSystemTools;
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
        if (visaSkillsToolCallback != null) {
            builder.defaultToolCallbacks(visaSkillsToolCallback);
        }
        if (fileSystemTools != null) {
            try {
                List<ToolCallback> fsCallbacks = createFileSystemToolCallbacks(fileSystemTools);
                builder.defaultToolCallbacks(fsCallbacks.toArray(new ToolCallback[0]));
                log.info("[LlmProviderRegistry] Registered {} FileSystemTools callbacks for provider {}",
                    fsCallbacks.size(), providerId);
            } catch (Exception e) {
                log.warn("[LlmProviderRegistry] Failed to register FileSystemTools callbacks: {}", e.getMessage());
            }
        }
        List<Advisor> advisors = buildDefaultAdvisors(providerId);
        if (!advisors.isEmpty()) {
            builder.defaultAdvisors(advisors.toArray(new Advisor[0]));
            log.info("[LlmProviderRegistry] Applied {} advisors for provider {}", advisors.size(), providerId);
        }

        return builder.build();
    }


    /**
     * 将 {@link FileSystemTools} 实例上标注了 {@link Tool} 的方法转换为 {@link ToolCallback} 列表。
     * <p>
     * Agent 可通过这些回调按需读取 skill 下 references/ 目录中的参考文件。
     */
    private static List<ToolCallback> createFileSystemToolCallbacks(FileSystemTools fsTools) {
        return Arrays.stream(FileSystemTools.class.getDeclaredMethods())
            .filter(m -> m.isAnnotationPresent(Tool.class))
            .<ToolCallback>map(m -> MethodToolCallback.builder()
                .toolObject(fsTools)
                .toolMethod(m)
                .build())
            .toList();
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
