# Task Plan: 后端冗余模块删除（方案 B）

## 目标
删除 AIThermalGuardIoT 项目中所有未被气象站模块使用的死代码和依赖，简化后端逻辑。

## 阶段

### Phase 1: 删除死代码 Java 文件 — 完成
### Phase 2: 修改 LlmProviderRegistry.java — 完成
### Phase 3: 精简 ErrorCode.java — 完成
### Phase 4: 精简 application.yaml — 完成
### Phase 5: 精简 build.gradle 依赖 — 完成
### Phase 6: 构建验证 — 完成（14 tests passed）

## 结果
所有任务完成。项目现在仅依赖 PostgreSQL，无需 Redis/S3。
