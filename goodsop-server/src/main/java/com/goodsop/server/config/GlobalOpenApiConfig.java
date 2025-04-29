package com.goodsop.server.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 全局OpenAPI配置类
 */
@Configuration
public class GlobalOpenApiConfig {

    /**
     * 全局API定义
     */
    @Bean
    @Primary
    public OpenAPI globalOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Goodsop API文档")
                        .description("Goodsop REST API接口文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Goodsop技术团队")
                                .email("admin@goodsop.com")
                                .url("https://goodsop.com"))
                        .license(new License().name("Apache 2.0").url("https://goodsop.com")))
                .components(new Components()
                        .addSecuritySchemes("bearer-key",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
} 