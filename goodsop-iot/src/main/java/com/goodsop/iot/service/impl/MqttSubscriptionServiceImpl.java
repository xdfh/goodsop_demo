package com.goodsop.iot.service.impl;

import com.goodsop.iot.config.EmqxConfig;
import com.goodsop.iot.service.MqttSubscriptionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttSubscriptionServiceImpl implements MqttSubscriptionService {

    private final MqttClient mqttClient;
    private final EmqxConfig emqxConfig;

    @PostConstruct
    public void init() {
        subscribeToTopics();
    }

    @Override
    public void subscribeToTopics() {
        try {
            // 订阅设备状态主题
            mqttClient.subscribe(emqxConfig.getTopics().getStatus(), 1);
            log.info("已订阅设备状态主题: {}", emqxConfig.getTopics().getStatus());

            // 订阅设备事件主题
            mqttClient.subscribe(emqxConfig.getTopics().getEvent(), 1);
            log.info("已订阅设备事件主题: {}", emqxConfig.getTopics().getEvent());

            // 订阅命令响应主题
            mqttClient.subscribe(emqxConfig.getTopics().getCommandResponse(), 1);
            log.info("已订阅命令响应主题: {}", emqxConfig.getTopics().getCommandResponse());
        } catch (MqttException e) {
            log.error("MQTT主题订阅失败", e);
        }
    }

    @Override
    public void publishCommand(String deviceId, String tenantId, String deviceModel, String branch, String message) {
        try {
            String topic = String.format("/dev/%s/%s/%s/%s/base/command", 
                    tenantId, deviceModel, branch, deviceId);
            mqttClient.publish(topic, message.getBytes(), 1, false);
            log.info("命令已发送: topic={}, message={}", topic, message);
        } catch (MqttException e) {
            log.error("命令发送失败", e);
        }
    }
} 