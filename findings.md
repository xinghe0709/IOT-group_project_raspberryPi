# Findings: 后端冗余模块探索

## 死代码清单

### 完全死代码（零引用）
- `infrastructure/file/` (8 files): 文档解析/存储子系统，来自旧 VISA Helper 项目
- `infrastructure/redis/RedisService.java`: Redisson 封装，无人调用
- `common/async/` (2 files): Redis Stream 模板，无子类实现
- `common/config/S3Config.java` + `StorageConfigProperties.java`: S3 配置，无消费者
- `common/constant/AsyncTaskStreamConstants.java`: 仅被死代码引用
- `common/exception/RateLimitExceededException.java`: 从未被抛出
- `ErrorCode.java` 2xxx~11xxx: 简历/面试/知识库/语音等不存在的模块

### 灰色地带（建议删除）
- `AgentUtilsConfiguration.java` + `AgentUtilsProperties.java`: SkillsTool/FileSystemTools 创建
- SkillsTool: 空壳 placeholder skill，LLM 无法实际使用
- FileSystemTools: 给 LLM 文件读写权限，存在安全风险

### 死依赖（build.gradle）
- redisson, aws-sdk-s3, tika-core/parsers, itext-core/font-asian, pinyin4j, dashscope-sdk-java, mapstruct
- spring-ai-starter-vector-store-pgvector, spring-ai-agent-utils

### 死配置（application.yaml）
- spring.redis.redisson, spring.ai.vectorstore.pgvector, spring.servlet.multipart
- app.storage, app.ai.rag, app.ai.security, app.ai.agent-utils

### 额外发现
- springdoc.packages-to-scan 写的是旧项目名 `org.xinghe.visahelper`，应改为 `org.xinghe.AIThermalGuardIoT`

## 活跃代码（必须保留）
- weather/ (16 files): 气象站核心
- common/ai/ (5 files after cleanup): LLM 注册中心、结构化输出、API 路径解析、Prompt 安全
- common/config/ CorsConfig, CorsProperties, LlmProviderProperties, OpenApiConfig
- common/constant/CommonConstants
- common/exception/ BusinessException, ErrorCode (trimmed), GlobalExceptionHandler
- common/result/Result
