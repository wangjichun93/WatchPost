package com.watchpost.manager.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger UI) 装配。
 * 访问: /swagger-ui.html ; API 文档: /v3/api-docs。
 * 配置 Bearer JWT 鉴权, 便于在线调试受保护端点。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI watchpostOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("望哨 WATCHPOST API")
                        .description("无 Agent 实时监控系统 · REST API 契约 (v1.0)")
                        .version("v1.0")
                        .license(new License().name("Apache 2.0")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
