package org.xinghe.AIThermalGuardIoT.common.async;

import lombok.extern.slf4j.Slf4j;
import org.xinghe.AIThermalGuardIoT.common.constant.AsyncTaskStreamConstants;
import org.xinghe.AIThermalGuardIoT.infrastructure.redis.RedisService;

import java.util.Map;

/**
 * Redis Stream 生产者模板基类。
 * 统一消息发送骨架与失败处理逻辑。
 */
@Slf4j
public abstract class AbstractStreamProducer<T> {

    private final RedisService redisService;

    protected AbstractStreamProducer(RedisService redisService) {
        this.redisService = redisService;
    }

    protected void sendTask(T payload) {
        try {
            String messageId = redisService.streamAdd(
                // Redis Stream 的 key（决定消息写入哪个任务队列）
                streamKey(),
                // 要写入 Stream 的消息体（序列化后的任务字段）
                buildMessage(payload),
                // Stream 允许保留的最大消息条数（超过后按近似裁剪）
                AsyncTaskStreamConstants.STREAM_MAX_LEN
            );
            log.info("{}任务已发送到Stream: {}, messageId={}",
                taskDisplayName(), payloadIdentifier(payload), messageId);
        } catch (Exception e) {
            log.error("发送{}任务失败: {}, error={}",
                taskDisplayName(), payloadIdentifier(payload), e.getMessage(), e);
            onSendFailed(payload, "任务入队失败: " + e.getMessage());
        }
    }

    protected String truncateError(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 500 ? error.substring(0, 500) : error;
    }

    protected abstract String taskDisplayName();

    protected abstract String streamKey();

    protected abstract Map<String, String> buildMessage(T payload);

    protected abstract String payloadIdentifier(T payload);

    protected abstract void onSendFailed(T payload, String error);
}
