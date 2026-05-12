package org.xinghe.AIThermalGuardIoT.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("智能 AI 签证移民咨询工具")
                        .description("各国签证办理，查看移民方案，规划移民路径")
                        .version("1.0.0"));
    }
}
