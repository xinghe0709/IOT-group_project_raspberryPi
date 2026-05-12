package org.xinghe.AIThermalGuardIoT.common.ai;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xinghe.AIThermalGuardIoT.common.exception.BusinessException;
import org.xinghe.AIThermalGuardIoT.common.exception.ErrorCode;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 统一封装结构化输出调用与重试策略。
 * <p>
 * 该组件负责：
 * </p>
 * <ul>
 *   <li>执行大模型结构化输出调用并按配置进行重试；</li>
 *   <li>在重试轮次追加严格 JSON 约束与可选错误上下文；</li>
 *   <li>对常见的未转义引号场景做本地修复后再次尝试解析；</li>
 *   <li>按上下文打点调用次数、尝试次数与延迟指标。</li>
 * </ul>
 */
@Component
public class StructuredOutputInvoker {

    private static final String STRICT_JSON_INSTRUCTION = """
请仅返回可被 JSON 解析器直接解析的 JSON 对象，并严格满足字段结构要求：
1) 不要输出 Markdown 代码块（如 ```json）。
2) 不要输出任何解释文字、前后缀、注释。
3) 所有字符串内引号必须正确转义。
    """;

    private static final String METRIC_INVOCATIONS = "app.ai.structured_output.invocations";
    private static final String METRIC_ATTEMPTS = "app.ai.structured_output.attempts";
    private static final String METRIC_LATENCY = "app.ai.structured_output.latency";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILURE = "failure";
    private static final int MAX_CONTEXT_TAG_LENGTH = 48;
    private static final Pattern NON_ALNUM_PATTERN = Pattern.compile("[^a-z0-9_]+");
    private static final Pattern MULTI_UNDERSCORE = Pattern.compile("_+");

    private final int maxAttempts;
    private final boolean includeLastErrorInRetryPrompt;
    private final boolean retryUseRepairPrompt;
    private final boolean retryAppendStrictJsonInstruction;
    private final int errorMessageMaxLength;
    private final boolean metricsEnabled;
    private final MeterRegistry meterRegistry;

    /**
     * 构造结构化输出调用器。
     *
     * @param properties 结构化输出配置属性
     * @param meterRegistry 指标注册器（可为空）
     */
    public StructuredOutputInvoker(
        StructuredOutputProperties properties,
        @Autowired(required = false) MeterRegistry meterRegistry
    ) {
        // 对关键配置做兜底，避免无效值影响重试与错误截断逻辑。
        this.maxAttempts = Math.max(1, properties.getStructuredMaxAttempts());
        this.includeLastErrorInRetryPrompt = properties.isStructuredIncludeLastError();
        this.retryUseRepairPrompt = properties.isStructuredRetryUseRepairPrompt();
        this.retryAppendStrictJsonInstruction = properties.isStructuredRetryAppendStrictJsonInstruction();
        this.errorMessageMaxLength = Math.max(20, properties.getStructuredErrorMessageMaxLength());
        this.metricsEnabled = properties.isStructuredMetricsEnabled();
        this.meterRegistry = meterRegistry;
    }

    /**
     * 调用模型并解析结构化输出，失败时按配置自动重试。
     * <p>
     * 所有重试失败后会抛出 {@link BusinessException}，错误码和前缀由调用方传入。
     * </p>
     *
     * @param chatClient 聊天客户端
     * @param systemPromptWithFormat 已包含输出格式约束的系统提示词
     * @param userPrompt 用户提示词
     * @param outputConverter 结构化输出转换器
     * @param errorCode 最终失败时抛出的业务错误码
     * @param errorPrefix 最终失败时的错误前缀
     * @param logContext 日志上下文标识
     * @param log 日志对象
     * @param <T> 结构化输出目标类型
     * @return 解析后的结构化对象
     */
    public <T> T invoke(
        ChatClient chatClient,
        String systemPromptWithFormat,
        String userPrompt,
        BeanOutputConverter<T> outputConverter,
        ErrorCode errorCode,
        String errorPrefix,
        String logContext,
        Logger log
    ) {
        // 记录整次调用的起始时间，用于最终 latency 指标统计。
        long startNanos = System.nanoTime();
        String contextTag = normalizeContextTag(logContext);
        // 在系统提示词上追加防注入约束，统一保护所有结构化调用。
        String securedSystemPrompt = systemPromptWithFormat
            + PromptSecurityConstants.ANTI_INJECTION_INSTRUCTION;
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            // 首次使用原提示词，后续重试按配置构建增强提示词。
            String attemptSystemPrompt = attempt == 1
                ? securedSystemPrompt
                : buildRetrySystemPrompt(securedSystemPrompt, lastError);
            try {
                // 调用模型获取文本输出，再做结构化转换。
                String content = chatClient.prompt()
                    .system(attemptSystemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

                //先执行常规转换；若失败则尝试修复未转义引号后再次转换。
                T result = convertWithRepair(content, outputConverter, logContext, log);
                recordAttempt(contextTag, STATUS_SUCCESS);
                recordInvocation(contextTag, STATUS_SUCCESS, startNanos);
                return result;
            } catch (Exception e) {
                lastError = e;
                recordAttempt(contextTag, STATUS_FAILURE);
                if (attempt < maxAttempts) {
                    log.warn("{}结构化解析失败，准备重试: attempt={}/{}, error={}",
                        logContext, attempt, maxAttempts, e.getMessage());
                } else {
                    log.error("{}结构化解析失败，已达最大重试次数: attempts={}, error={}",
                        logContext, maxAttempts, e.getMessage());
                }
            }
        }

        recordInvocation(contextTag, STATUS_FAILURE, startNanos);
        throw new BusinessException(
            errorCode,
            errorPrefix + (lastError != null ? lastError.getMessage() : "unknown")
        );
    }

    /**
     * 先执行常规转换；若失败则尝试修复未转义引号后再次转换。
     *
     * @param content 模型返回的文本内容
     * @param outputConverter 结构化输出转换器
     * @param logContext 日志上下文
     * @param log 日志对象
     * @param <T> 目标类型
     * @return 转换后的结构化对象
     */
    private <T> T convertWithRepair(
        String content,
        BeanOutputConverter<T> outputConverter,
        String logContext,
        Logger log
    ) {
        try {
            // 优先走标准转换路径，保持最小干预。
            return outputConverter.convert(content);
        } catch (Exception firstError) {
            // 当转换失败时，尝试修复常见的未转义引号问题后再解析一次。
            String repaired = repairUnescapedQuotesInJsonStrings(content);
            if (!repaired.equals(content)) {
                try {
                    T result = outputConverter.convert(repaired);
                    log.warn("{}结构化 JSON 存在未转义引号，已在本地修复后解析成功", logContext);
                    return result;
                } catch (Exception repairError) {
                    firstError.addSuppressed(repairError);
                }
            }
            throw firstError;
        }
    }

    /**
     * 修复 JSON 字符串值中疑似未转义的双引号。
     *
     * @param content 原始内容
     * @return 修复后的内容
     */
    private String repairUnescapedQuotesInJsonStrings(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        // 线性扫描 JSON 文本，仅在字符串上下文中修复疑似非法引号。
        StringBuilder repaired = new StringBuilder(content.length() + 16);
        boolean inString = false;
        boolean escaping = false;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (!inString) {
                if (ch == '"') {
                    inString = true;
                }
                repaired.append(ch);
                continue;
            }

            if (escaping) {
                repaired.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                repaired.append(ch);
                escaping = true;
                continue;
            }
            if (ch == '"') {
                if (isLikelyJsonStringTerminator(content, i + 1)) {
                    inString = false;
                    repaired.append(ch);
                } else {
                    repaired.append("\\\"");
                }
                continue;
            }
            repaired.append(ch);
        }
        return repaired.toString();
    }

    /**
     * 判断当前位置是否可能是 JSON 字符串结束引号。
     *
     * @param content 原始内容
     * @param start 从当前位置之后开始扫描的下标
     * @return 若更像结束符则返回 {@code true}
     */
    private boolean isLikelyJsonStringTerminator(String content, int start) {
        // 跳过空白后判断后继字符是否符合 JSON 字符串终止位置特征。
        for (int i = start; i < content.length(); i++) {
            char next = content.charAt(i);
            if (Character.isWhitespace(next)) {
                continue;
            }
            return next == ',' || next == '}' || next == ']' || next == ':';
        }
        return true;
    }

    /**
     * 构建重试轮次使用的系统提示词。
     *
     * @param systemPromptWithFormat 原始系统提示词
     * @param lastError 上一次失败异常
     * @return 重试使用的系统提示词
     */
    private String buildRetrySystemPrompt(String systemPromptWithFormat, Exception lastError) {
        if (!retryUseRepairPrompt) {
            // 关闭修复提示时，重试沿用原始系统提示词。
            return systemPromptWithFormat;
        }

        // 在原提示词基础上叠加严格 JSON 约束与上次错误摘要。
        StringBuilder prompt = new StringBuilder(systemPromptWithFormat)
            .append("\n\n");

        if (retryAppendStrictJsonInstruction) {
            prompt.append(STRICT_JSON_INSTRUCTION).append('\n');
        }
        prompt.append("上次输出解析失败，请仅返回合法 JSON。");

        if (includeLastErrorInRetryPrompt && lastError != null && lastError.getMessage() != null) {
            prompt.append("\n上次失败原因：")
                .append(sanitizeErrorMessage(lastError.getMessage()));
        }
        return prompt.toString();
    }

    /**
     * 清洗错误信息，限制长度并折叠为单行文本，避免污染提示词。
     *
     * @param message 原始错误信息
     * @return 清洗后的错误信息
     */
    private String sanitizeErrorMessage(String message) {
        // 错误信息压缩为单行并截断，避免把长异常文本注入下一轮 Prompt。
        String oneLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (oneLine.length() > errorMessageMaxLength) {
            return oneLine.substring(0, errorMessageMaxLength) + "...";
        }
        return oneLine;
    }

    /**
     * 记录单次尝试指标。
     *
     * @param contextTag 上下文标签
     * @param status 状态标签（success/failure）
     */
    private void recordAttempt(String contextTag, String status) {
        if (!isMetricsAvailable()) {
            return;
        }
        // 每次尝试单独计数，便于观察重试分布。
        meterRegistry.counter(
            METRIC_ATTEMPTS,
            Tags.of("context", contextTag, "status", status)
        ).increment();
    }

    /**
     * 记录整次调用指标与耗时。
     *
     * @param contextTag 上下文标签
     * @param status 状态标签（success/failure）
     * @param startNanos 调用起始纳秒时间
     */
    private void recordInvocation(String contextTag, String status, long startNanos) {
        if (!isMetricsAvailable()) {
            return;
        }
        // 记录整次调用结果与端到端耗时。
        Tags tags = Tags.of("context", contextTag, "status", status);
        meterRegistry.counter(METRIC_INVOCATIONS, tags).increment();
        meterRegistry.timer(METRIC_LATENCY, tags)
            .record(System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    /**
     * 判断当前是否启用并可用指标能力。
     *
     * @return 可打点返回 {@code true}
     */
    private boolean isMetricsAvailable() {
        // 只有显式开启且 MeterRegistry 注入成功才执行打点。
        return metricsEnabled && meterRegistry != null;
    }

    /**
     * 归一化日志/指标上下文标签。
     * <p>
     * 规则包括：小写化、空白替换、非法字符清理、连续下划线折叠与长度截断。
     * </p>
     *
     * @param raw 原始上下文字符串
     * @return 归一化后的标签
     */
    private String normalizeContextTag(String raw) {
        // 统一维度标签格式，防止监控中出现高基数和非法字符。
        String source = (raw == null || raw.isBlank()) ? "unknown" : raw;
        String normalized = source.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        normalized = NON_ALNUM_PATTERN.matcher(normalized).replaceAll("_");
        normalized = MULTI_UNDERSCORE.matcher(normalized).replaceAll("_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            normalized = "unknown";
        }
        if (normalized.length() > MAX_CONTEXT_TAG_LENGTH) {
            normalized = normalized.substring(0, MAX_CONTEXT_TAG_LENGTH);
        }
        return normalized;
    }


}
