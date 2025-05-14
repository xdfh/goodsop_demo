package com.goodsop.iot.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MqttClientConfig {

    private final EmqxConfig emqxConfig;

    @Bean
    public MqttConnectOptions mqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{emqxConfig.getHost()});
        options.setUserName(emqxConfig.getUsername());
        options.setPassword(emqxConfig.getPassword().toCharArray());
        options.setKeepAliveInterval(60);
        options.setConnectionTimeout(30);
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        return options;
    }

    @Bean
    public MqttClient mqttClient(MqttConnectOptions mqttConnectOptions) {
        try {
            MqttClient mqttClient = new MqttClient(
                    emqxConfig.getHost(),
                    emqxConfig.getClientId(),
                    new MemoryPersistence());
            
            mqttClient.setCallback(new MqttMessageCallback());
            mqttClient.connect(mqttConnectOptions);
            
            log.info("MQTT客户端已连接到服务器：{}", emqxConfig.getHost());
            return mqttClient;
        } catch (MqttException e) {
            log.error("MQTT客户端连接失败", e);
            throw new RuntimeException("MQTT客户端连接失败", e);
        }
    }
} 