package com.goodsop.iot.service;

/**
 * IoT设备状态消息处理器接口
 * <p>
 * 定义了处理从MQTT接收到的设备状态消息的方法。
 */
public interface IotDeviceStatusHandler {

    /**
     * 处理设备状态消息。
     *
     * @param topic   消息的主题
     * @param payload 消息的字节数组内容 (通常是JSON字符串)
     */
    void handleStatusMessage(String topic, byte[] payload);

    /**
     * 从给定的MQTT主题字符串中提取设备ID。
     *
     * @param topic MQTT主题
     * @return 提取到的设备ID，如果无法提取或主题格式不匹配则返回null。
     */
    String extractDeviceIdFromTopic(String topic);
    
} 