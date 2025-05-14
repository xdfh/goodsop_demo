package com.goodsop.iot.config;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;

@Slf4j
public class MqttMessageCallback implements MqttCallbackExtended {

    @Override
    public void connectionLost(Throwable cause) {
        log.error("MQTT连接丢失", cause);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        log.info("收到MQTT消息: topic={}, payload={}", topic, new String(message.getPayload()));
        
        // 根据topic前缀分发消息到不同的处理服务
        if (topic.endsWith("/base/status")) {
            handleStatusMessage(topic, message);
        } else if (topic.endsWith("/base/event")) {
            handleEventMessage(topic, message);
        } else if (topic.endsWith("/base/command/response")) {
            handleCommandResponseMessage(topic, message);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        log.debug("MQTT消息发送完成: {}", token.getMessageId());
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("MQTT连接{} {}完成", serverURI, reconnect ? "重新" : "");
    }
    
    private void handleStatusMessage(String topic, MqttMessage message) {
        // TODO: 解析设备状态消息并保存到数据库
        log.info("处理设备状态消息: {}", topic);
    }
    
    private void handleEventMessage(String topic, MqttMessage message) {
        // TODO: 解析设备事件消息并保存到数据库
        log.info("处理设备事件消息: {}", topic);
    }
    
    private void handleCommandResponseMessage(String topic, MqttMessage message) {
        // TODO: 处理设备命令响应
        log.info("处理设备命令响应: {}", topic);
    }
} 