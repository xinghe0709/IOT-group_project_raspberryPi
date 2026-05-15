package org.xinghe.AIThermalGuardIoT.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {
    
    // ========== 通用错误 1xxx ==========
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    
    // ========== AI服务错误 7xxx ==========
    AI_SERVICE_UNAVAILABLE(7001, "AI服务暂时不可用，请稍后重试"),
    AI_SERVICE_TIMEOUT(7002, "AI服务响应超时"),
    AI_SERVICE_ERROR(7003, "AI服务调用失败"),
    AI_API_KEY_INVALID(7004, "AI服务密钥无效"),
    AI_RATE_LIMIT_EXCEEDED(7005, "AI服务调用频率超限"),

    // ========== 气象站模块错误 12xxx ==========
    WEATHER_RECORD_SAVE_FAILED(12001, "气象数据保存失败"),
    WEATHER_ADVISORY_GENERATION_FAILED(12002, "AI环境分析生成失败"),
    WEATHER_ADVISORY_NOT_FOUND(12003, "环境建议不存在"),
    WEATHER_STATION_INVALID(12004, "气象站标识无效"),
    WEATHER_QUERY_MISSING_DATE(12005, "查询时间参数缺失"),
    WEATHER_QUERY_DATE_INVALID(12006, "开始时间不能晚于结束时间"),
    WEATHER_QUERY_RANGE_TOO_LARGE(12007, "查询时间范围不能超过90天");

    private final Integer code;
    private final String message;
}
