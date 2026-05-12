package org.xinghe.AIThermalGuardIoT.common.async;


import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.xinghe.AIThermalGuardIoT.common.constant.AsyncTaskStreamConstants;
import org.xinghe.AIThermalGuardIoT.infrastructure.redis.RedisService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
/**
 * Redis Stream 消费者抽象模板。
 * <p>
 * 该基类负责统一处理以下通用流程：
 * </p>
 * <ul>
 *   <li>消费者线程初始化与销毁</li>
 *   <li>消费组自动创建与消息轮询</li>
 *   <li>消息解析、业务处理、ACK 确认</li>
 *   <li>失败重试与最终失败状态落库</li>
 * </ul>
 * <p>
 * 子类只需关注业务相关的解析、状态变更和核心处理逻辑，实现对应抽象方法即可。
 * </p>
 *
 * @param <T> 业务负载类型（如知识库任务、简历解析任务、面试评估任务等）
 */
public abstract class AbstractStreamConsumer<T> {

    private final RedisService redisService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executorService;
    private String consumerName;

    /**
     * 构造消费模板实例。
     *
     * @param redisService Redis 基础能力封装，用于消费、ACK、创建消费组等操作
     */
    protected AbstractStreamConsumer(RedisService redisService) {
        this.redisService = redisService;
    }

    /**
     * 组件初始化入口，在 Bean 创建完成后自动执行。
     * <p>
     * 主要职责：
     * </p>
     * <ul>
     *   <li>生成当前消费者实例名称（consumerPrefix + 随机后缀）</li>
     *   <li>初始化单线程守护线程池，保证串行消费</li>
     *   <li>设置运行标记并异步启动消费主流程</li>
     * </ul>
     * <p>
     * 该方法由框架托管调用，通常无需子类覆写。
     * </p>
     */
    @PostConstruct
    public void init() {
        //生成唯一的consumerName
        this.consumerName = consumerPrefix() + UUID.randomUUID().toString().substring(0, 8);
        //单线程线程池
        this.executorService = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            r -> {
                Thread t = new Thread(r, threadName());
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy()
        );

        running.set(true);
        executorService.submit(this::startConsumer);
        log.info("{} consumer started: consumerName={}", taskDisplayName(), consumerName);
    }

    /**
     * 组件销毁入口，在 Bean 下线前自动执行。
     * <p>
     * 主要职责：
     * </p>
     * <ul>
     *   <li>停止消费循环</li>
     *   <li>关闭线程池，释放后台资源</li>
     *   <li>输出消费者停止日志</li>
     * </ul>
     */
    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (executorService != null) {
            executorService.shutdown();
        }
        log.info("{} consumer stopped: consumerName={}", taskDisplayName(), consumerName);
    }

    /**
     * 初始化消费者组
     * <p>
     * 启动时会先尝试创建/确认消费组；若创建失败仅记录告警，不中断后续消费，
     * 以兼容消费组已存在等幂等场景。
     * </p>
     */
    private void startConsumer() {
        try {
            redisService.createStreamGroup(streamKey(), groupName());
            log.info("Redis Stream group is ready: {}", groupName());
        } catch (Exception e) {
            log.warn("Failed to prepare Redis Stream group: groupName={}", groupName(), e);
        }

        consumeLoop();
    }

    /**
     * 消费主循环。
     * <p>
     * 循环条件为 {@code running == true}。每次轮询按批次从 Redis Stream 读取消息，
     * 并委托 {@link #processMessage(StreamMessageId, Map)} 处理。遇到线程中断时立即退出；
     * 其他异常仅记录日志并继续下一轮，避免单次异常导致消费者整体停止。
     * </p>
     */
    private void consumeLoop() {
        while (running.get()) {
            try {
                redisService.streamConsumeMessages(
                    streamKey(),
                    groupName(),
                    consumerName,
                    AsyncTaskStreamConstants.BATCH_SIZE,
                    AsyncTaskStreamConstants.POLL_INTERVAL_MS,
                    this::processMessage
                );
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    log.info("Consumer thread interrupted");
                    break;
                }
                log.error("Failed to consume message", e);
            }
        }
    }

    /**
     * 单条消息处理模板。
     * <p>
     * 处理顺序如下：
     * </p>
     * <ol>
     *   <li>调用 {@link #parsePayload(StreamMessageId, Map)} 解析业务负载</li>
     *   <li>若解析失败（返回 {@code null}），直接 ACK 丢弃</li>
     *   <li>记录日志并标记任务为处理中（{@link #markProcessing(Object)}）</li>
     *   <li>执行核心业务（{@link #processBusiness(Object)}）</li>
     *   <li>成功则标记完成并 ACK</li>
     *   <li>失败则根据重试次数决定重试（{@link #retryMessage(Object, int)}）
     *   或最终失败（{@link #markFailed(Object, String)}），最后 ACK</li>
     * </ol>
     *
     * @param messageId Stream 消息 ID
     * @param data      消息字段键值对
     */
    private void processMessage(StreamMessageId messageId, Map<String, String> data) {
        T payload = parsePayload(messageId, data);
        if (payload == null) {
            ackMessage(messageId);
            return;
        }

        int retryCount = parseRetryCount(data);
        log.info("Processing {} task: payload={}, messageId={}, retryCount={}",
            taskDisplayName(), payloadIdentifier(payload), messageId, retryCount);

        try {
            //核心业务逻辑
            markProcessing(payload);
            processBusiness(payload);
            markCompleted(payload);
            ackMessage(messageId);
            log.info("{} task completed: {}", taskDisplayName(), payloadIdentifier(payload));
        } catch (Exception e) {
            log.error("{} task failed: {}", taskDisplayName(), payloadIdentifier(payload), e);
            if (retryCount < AsyncTaskStreamConstants.MAX_RETRY_COUNT) {
                //重新将消息放入队列中，直到达到最大重试次数
                retryMessage(payload, retryCount + 1);
            } else {
                markFailed(payload, truncateError(
                    taskDisplayName() + " failed after retry " + retryCount + ": " + e.getMessage()
                ));
            }
            ackMessage(messageId);
        }
    }

    /**
     * 解析消息中的重试次数。
     * <p>
     * 从字段 {@link AsyncTaskStreamConstants#FIELD_RETRY_COUNT} 读取值，缺失或格式非法时返回 0。
     * </p>
     *
     * @param data 消息字段键值对
     * @return 当前消息重试次数，最小为 0
     */
    protected int parseRetryCount(Map<String, String> data) {
        try {
            return Integer.parseInt(data.getOrDefault(AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 截断错误信息长度，避免过长文本写入存储层。
     *
     * @param error 原始错误信息
     * @return 截断后的错误信息；当入参为 {@code null} 时返回 {@code null}
     */
    protected String truncateError(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 500 ? error.substring(0, 500) : error;
    }

    /**
     * 向 Redis Stream 发送 ACK，确认消息已处理。
     * <p>
     * ACK 失败仅记录错误日志，不抛出异常，避免影响当前消费线程稳定性。
     * </p>
     *
     * @param messageId 需要确认的消息 ID
     */
    private void ackMessage(StreamMessageId messageId) {
        try {
            redisService.streamAck(streamKey(), groupName(), messageId);
        } catch (Exception e) {
            log.error("Failed to ack stream message: messageId={}", messageId, e);
        }
    }

    /**
     * 暴露 Redis 基础能力给子类。
     *
     * @return 当前消费者持有的 RedisService 实例
     */
    protected RedisService redisService() {
        return redisService;
    }

    /**
     * 获取任务展示名称，用于统一日志与错误信息。
     *
     * @return 任务名称（例如：KnowledgeBaseVectorize、ResumeParse）
     */
    protected abstract String taskDisplayName();

    /**
     * 获取 Redis Stream Key。
     *
     * @return 当前消费者监听的 Stream Key
     */
    protected abstract String streamKey();

    /**
     * 获取 Redis 消费组名称。
     *
     * @return 消费组名称
     */
    protected abstract String groupName();

    /**
     * 获取消费者名称前缀。
     * <p>
     * 实际 consumerName 由该前缀与随机后缀拼接生成，用于区分同组内多个消费者实例。
     * </p>
     *
     * @return 消费者名称前缀
     */
    protected abstract String consumerPrefix();

    /**
     * 获取消费者线程名。
     *
     * @return 后台消费线程名称，建议体现业务语义，便于排障
     */
    protected abstract String threadName();

    /**
     * 将 Stream 原始消息解析为业务负载对象。
     *
     * @param messageId 消息 ID
     * @param data      消息字段键值对
     * @return 业务负载；返回 {@code null} 表示消息不可用并会被直接 ACK 丢弃
     */
    protected abstract T parsePayload(StreamMessageId messageId, Map<String, String> data);

    /**
     * 获取负载唯一标识，用于日志追踪。
     *
     * @param payload 业务负载
     * @return 负载标识（例如任务 ID）
     */
    protected abstract String payloadIdentifier(T payload);

    /**
     * 将任务状态标记为处理中。
     * <p>
     * 建议在该方法中完成任务状态持久化更新，保证任务可观测性。
     * </p>
     *
     * @param payload 业务负载
     */
    protected abstract void markProcessing(T payload);

    /**
     * 执行核心业务处理逻辑。
     * <p>
     * 抛出的异常将由模板统一捕获并触发重试或失败落库。
     * </p>
     *
     * @param payload 业务负载
     */
    protected abstract void processBusiness(T payload);

    /**
     * 将任务状态标记为已完成。
     *
     * @param payload 业务负载
     */
    protected abstract void markCompleted(T payload);

    /**
     * 将任务状态标记为最终失败（超过最大重试次数后调用）。
     *
     * @param payload 业务负载
     * @param error   失败原因（通常已通过 {@link #truncateError(String)} 截断）
     */
    protected abstract void markFailed(T payload, String error);

    /**
     * 重新投递任务消息用于后续重试。
     *
     * @param payload    业务负载
     * @param retryCount 新的重试次数（通常为当前次数 + 1）
     */
    protected abstract void retryMessage(T payload, int retryCount);
}
