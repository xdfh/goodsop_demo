package com.goodsop.iot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "emqx.mqtt")
public class EmqxConfig {
    private String host;
    private String username;
    private String password;
    private String clientId;
    private Topics topics;

    private int keepAliveInterval;
    private int connectionTimeout;

    @Data
    public static class Topics {
        private String status;
        private String event;
        private String commandResponse;
    }
} 