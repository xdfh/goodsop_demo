# Knife4j API文档配置指南

## 概述

本项目使用Knife4j 4.3.0（基于OpenAPI 3.0）作为API文档工具，通过配置实现了分模块展示API文档，方便开发和调试使用。

## 配置方式

为了避免多个API配置类导致的冲突问题，项目采用以下策略：

1. 全局只有一个 `GlobalOpenApiConfig` 配置类，位于server模块中
2. 各个业务模块不需要自定义API配置类
3. 主要通过application-dev.yml中的springdoc配置实现分组

## 全局OpenAPI配置类

在 `goodsop-server` 模块中，有一个全局的OpenAPI配置类：

```java
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
```

## application-dev.yml中的配置

在application-dev.yml中，通过以下配置实现分模块展示API文档：

```yaml
# Knife4j配置
knife4j:
  enable: true
  setting:
    language: zh-CN
    enable-swagger-models: true
    enable-document-manage: true
    swagger-model-name: 实体类列表
    enable-version: true
    enable-footer: false
    enable-group: true
  basic:
    enable: false
  documents:
    - group: default
      name: 使用指南
      locations: classpath:markdown/*

# SpringDoc配置
springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    disable-swagger-default-url: true
    display-request-duration: true
    operations-sorter: method
    tags-sorter: alpha
  group-configs:
    - group: 文件管理模块
      paths-to-match: /file/**
      packages-to-scan: com.goodsop.file.controller
    - group: 用户管理模块
      paths-to-match: /user/**
      packages-to-scan: com.goodsop.user.controller
    - group: 系统服务模块
      paths-to-match: /system/**
      packages-to-scan: com.goodsop.server.controller
    - group: IoT设备模块
      paths-to-match: /iot/**
      packages-to-scan: com.goodsop.iot.controller
```

## 注意事项

1. **避免重复配置**: 不要在各个业务模块中创建单独的API配置类，否则会导致冲突
2. **统一管理**: 所有API分组配置都在application-dev.yml中管理
3. **注解使用**: 在Controller类和方法上正确使用OpenAPI注解

## Controller注解示例

```java
@Tag(name = "文件上传", description = "文件上传相关接口")
@RestController
@RequestMapping("/file/upload")
public class FileUploadController {

    @Operation(summary = "上传文件", description = "上传单个文件")
    @PostMapping("/single")
    public Result<FileInfo> uploadSingle(@Parameter(description = "文件") @RequestPart MultipartFile file) {
        // 实现逻辑
    }
}
```

## 访问地址

- Knife4j文档地址: http://localhost:8080/api/doc.html
- 原始Swagger UI地址: http://localhost:8080/api/swagger-ui.html

## 常见问题排查

1. **Bean冲突**: 如果遇到"ConflictingBeanDefinitionException"错误，检查是否存在多个API配置类
2. **分组不显示**: 检查springdoc.group-configs配置是否正确，路径和包名是否匹配
3. **控制器未显示**: 检查Controller类上的注解是否正确，路径是否匹配配置 