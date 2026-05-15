# Progress: 后端冗余模块删除

## Session 2026-05-15

### 已完成
- 两个 Agent 并行探索项目结构，确认死代码范围
- 与用户对齐方案 B（推荐方案）
- Phase 1: 删除 17 个死代码 Java 文件 + skills/placeholder 目录
- Phase 2: 修改 LlmProviderRegistry.java（移除 SkillsTool/FileSystemTools）
- Phase 3: 精简 ErrorCode.java（删除 2xxx~11xxx）
- Phase 4: 精简 application.yaml（删除 redis/pgvector/multipart/storage/rag/security/agent-utils 配置块）
- Phase 5: 精简 build.gradle 依赖（删除 12 个依赖）+ libs.versions.toml
- Phase 6: 构建验证 — `./gradlew test` 14 个测试全部通过
- 更新 CLAUDE.md 反映简化后的架构

### 修复的问题
- OpenAiAudioSpeechAutoConfiguration 需要 spring.ai.openai.api-key，已恢复该配置行

### 最终统计
- 删除 Java 文件: 17 个
- 删除 Gradle 依赖: 12 个
- 删除 YAML 配置行: ~60 行
- 简化 ErrorCode: 删除 ~50 个枚举值
