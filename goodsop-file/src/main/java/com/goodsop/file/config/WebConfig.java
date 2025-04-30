package com.goodsop.file.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置类，用于静态资源映射
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final FileProperties fileProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 添加文件存储目录的静态资源映射
        // 映射规则：/files/** -> 文件存储路径
        String filesLocation = "file:" + fileProperties.getStorage().getPath();
        registry.addResourceHandler("/files/**")
                .addResourceLocations(filesLocation);
    }
} 