package com.goodsop.iot.model.dto;

import lombok.Data;

@Data
 public class MqttMessageDto {
     private String topic;
     private String payload;
     private int qos = 0; // 默认 QoS 0
     private boolean isRetained = false; // 默认不保留
 }
