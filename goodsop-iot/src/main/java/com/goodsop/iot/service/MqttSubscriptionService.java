package com.goodsop.iot.service;

public interface MqttSubscriptionService {
    /**
     * 订阅MQTT主题
     */
    void subscribeToTopics();
    
    /**
     * 发布命令到设备
     *
     * @param deviceId    设备ID
     * @param tenantId    租户ID
     * @param deviceModel 设备型号
     * @param branch      固件分支
     * @param message     命令消息
     */
    void publishCommand(String deviceId, String tenantId, String deviceModel, String branch, String message);
} 