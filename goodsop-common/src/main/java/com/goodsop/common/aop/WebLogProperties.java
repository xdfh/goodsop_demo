package com.goodsop.common.aop;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日志配置属性类
 */
@Data
@ConfigurationProperties(prefix = "goodsop.logging")
public class WebLogProperties {
    
    /**
     * 是否启用请求日志
     */
    private boolean enabled = true;
    
    /**
     * 是否记录请求参数
     */
    private boolean includeRequestParams = true;
    
    /**
     * 是否记录响应结果
     */
    private boolean includeResponseBody = true;
    
    /**
     * 服务层方法执行时间阈值(ms)，超过此阈值会记录警告日志
     */
    private long serviceSlowThreshold = 300;
    
    /**
     * 控制器方法执行时间阈值(ms)，超过此阈值会记录警告日志
     */
    private long controllerSlowThreshold = 500;
    
    /**
     * 日志ID长度
     */
    private int requestIdLength = 12;
} 