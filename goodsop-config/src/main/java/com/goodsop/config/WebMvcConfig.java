package com.goodsop.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC配置类，主要用于配置跨域等Web相关功能
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 配置全局跨域处理
     * @param registry 跨域注册表
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 允许的来源域名，生产环境应该指定具体的前端域名
                .allowedOriginPatterns("*")
                // 允许的HTTP方法
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // 允许的请求头
                .allowedHeaders("*")
                // 是否允许发送Cookie
                .allowCredentials(true)
                // 预检请求的有效期，单位秒
                .maxAge(3600);
    }
} 