package com.goodsop.common.config;

import com.goodsop.common.aop.WebLogProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 日志配置类
 * 用于启用WebLogProperties配置属性
 */
@Configuration
@EnableConfigurationProperties(WebLogProperties.class)
public class LoggingConfiguration {
    // 无需其他配置，@EnableConfigurationProperties注解会将WebLogProperties注册为Bean
} 