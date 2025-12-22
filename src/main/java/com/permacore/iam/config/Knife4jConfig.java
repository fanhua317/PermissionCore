package com.permacore.iam.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j配置类
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI permacoreOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PermaCore IAM API")
                        .description("企业级RBAC3权限管理系统接口文档")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("开发团队")
                                .email("dev@permacore.com")));
    }

    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder()
                .group("1. 认证模块")
                .pathsToMatch("/api/auth/**")
                .build();
    }

    @Bean
    public GroupedOpenApi sysApi() {
        return GroupedOpenApi.builder()
                .group("2. 系统管理")
                .pathsToMatch("/api/user/**", "/api/role/**", "/api/permission/**", "/api/dept/**", "/api/sod-constraint/**")
                .build();
    }

    @Bean
    public GroupedOpenApi logApi() {
        return GroupedOpenApi.builder()
                .group("3. 日志管理")
                .pathsToMatch("/api/login-log/**", "/api/oper-log/**")
                .build();
    }
}