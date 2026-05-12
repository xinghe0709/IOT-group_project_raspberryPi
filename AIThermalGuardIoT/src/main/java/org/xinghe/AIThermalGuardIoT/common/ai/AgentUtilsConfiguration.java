package org.xinghe.AIThermalGuardIoT.common.ai;

import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * spring-ai-agent-utils 工具配置。
 * 当前先接入 SkillsTool，复用 {@code resources/skills/{skillId}/SKILL.md}。
 * 通过 {@link AgentUtilsProperties} 读取技能根目录，并在启动时完成路径校验与归一化。
 */
@Configuration
@Slf4j
public class AgentUtilsConfiguration {

    private final ResourceLoader resourceLoader;
    private final AgentUtilsProperties agentUtilsProperties;

    public AgentUtilsConfiguration(
        ResourceLoader resourceLoader, AgentUtilsProperties agentUtilsProperties
    ) {
        this.resourceLoader = resourceLoader;
        this.agentUtilsProperties = agentUtilsProperties;
    }

    @Bean("visaSkillsToolCallback")
    public ToolCallback visaSkillsToolCallback() {
        String configuredSkillsRoot = agentUtilsProperties.getSkillsRoot();
        String normalizedSkillsRoot = normalizeSkillsRoot(configuredSkillsRoot);
        //
        Resource skillsRootResource = resourceLoader.getResource(normalizedSkillsRoot);

        // 启动期即校验资源可用性，避免模型运行时触发工具才发现目录缺失。
        if (!skillsRootResource.exists()) {
            throw new IllegalStateException("未找到 skills 根目录，请检查配置: " + normalizedSkillsRoot);
        }

        log.info("AgentUtils SkillsTool 已启用，skillsRoot={}, configured={}", normalizedSkillsRoot, configuredSkillsRoot);

        // 将 SkillsTool 封装为 ToolCallback 后，框架会把它作为"可调用工具"提供给模型。
        // 当模型决定调用时，最终由本服务执行技能发现/加载逻辑，再把结果返回给模型。
        return SkillsTool.builder()
            .addSkillsResource(skillsRootResource)
            .build();
    }

    @Bean("visaFileSystemToolsCallback")
    FileSystemTools visaFileSystemToolsCallback() {
        log.info("AgentUtils FileSystemTools 已启用");
        return FileSystemTools.builder().build();
    }

    /**
     * 将配置中的 skillsRoot 规范化为可被 ResourceLoader 识别的目录路径。
     * <p>
     * 处理规则：
     * <ul>
     *   <li>空值或空白值回退为 {@code classpath:skills}</li>
     *   <li>统一分隔符为 {@code /}</li>
     *   <li>若误传到具体文件（如 {@code /SKILL.md}），自动回退到目录</li>
     *   <li>若包含通配符（如 {@code *}），自动截断到通配符前缀目录</li>
     *   <li>移除末尾冗余斜杠</li>
     * </ul>
     *
     * @param raw 原始配置值
     * @return 规范化后的技能根目录
     */
    private String normalizeSkillsRoot(String raw) {
        if (raw == null || raw.isBlank()) {
            return "classpath:skills";
        }

        String normalized = raw.trim();
        normalized = normalized.replace('\\', '/');

        if (normalized.endsWith("/SKILL.md")) {
            normalized = normalized.substring(0, normalized.length() - "/SKILL.md".length());
        }

        int wildcardIndex = normalized.indexOf('*');
        if (wildcardIndex >= 0) {
            normalized = normalized.substring(0, wildcardIndex);
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized.isBlank() ? "classpath:skills" : normalized;
    }
}
