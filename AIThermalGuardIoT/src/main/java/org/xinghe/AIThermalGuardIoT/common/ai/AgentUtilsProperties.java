package org.xinghe.AIThermalGuardIoT.common.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 工具相关配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai.agent-utils")
public class AgentUtilsProperties {

    /**
     * Skills 根目录，默认读取 classpath 下的 skills 目录。
     */
    private String skillsRoot = "classpath:skills";
}
