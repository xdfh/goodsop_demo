package com.goodsop.iot.controller;

import com.goodsop.iot.model.dto.MqttMessageDto;
import com.goodsop.iot.service.EmqxAuthService;
import com.goodsop.iot.service.MqttClientService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.eclipse.paho.client.mqttv3.MqttException;

@Slf4j
@RestController
@RequestMapping("/iot/mqtt/test")
@RequiredArgsConstructor
@Hidden
@Tag(name = "MQTT测试", description = "MQTT消息通信测试接口")
public class TestMqttController {

    private final MqttClientService mqttClientService;


    @Operation(summary = "发布MQTT消息 (通过MqttClientService)", description = "向指定的MQTT主题发布一条消息。此接口使用长连接的MqttClientService。")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "消息已成功通过MqttClientService提交发布",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "无效的请求参数",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "500", description = "服务器内部错误/消息发布失败 (MqttClientService)",
                    content = @Content(mediaType = "application/json"))
    })
    @PostMapping("/managed-client/publish")
    public ResponseEntity<String> publishMessageWithManagedClient(@RequestBody MqttMessageDto messageDto) {
        log.info("接收到MQTT发布请求 (MqttClientService): topic='{}', qos={}, retained={}, payload='{}'",
                messageDto.getTopic(), messageDto.getQos(), messageDto.isRetained(), messageDto.getPayload());
        try {
            if (messageDto.getTopic() == null || messageDto.getTopic().trim().isEmpty()) {
                log.warn("MQTT发布请求的Topic为空 (MqttClientService)");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Topic不能为空");
            }
            if (messageDto.getPayload() == null) {
                log.warn("MQTT发布请求的Payload为null (MqttClientService)");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Payload不能为空");
            }

            mqttClientService.publish(
                    messageDto.getTopic(),
                    messageDto.getPayload(),
                    messageDto.getQos(),
                    messageDto.isRetained()
            );
            log.info("MQTT消息已成功通过MqttClientService提交发布: topic='{}'", messageDto.getTopic());
            return ResponseEntity.ok("消息已成功通过MqttClientService提交发布。");

        } catch (MqttException e) {
            log.error("通过MqttClientService发布MQTT消息时发生MqttException: {} - {}", e.getReasonCode(), e.getMessage(), e);
            String errorMessage = "通过MqttClientService发布消息失败";
            if (e.getReasonCode() == MqttException.REASON_CODE_CLIENT_NOT_CONNECTED) {
                errorMessage = "MQTT客户端未连接，无法发布消息。";
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMessage + " (" + e.getMessage() + ")");
        } catch (IllegalArgumentException e) {
            log.warn("无效的MQTT发布请求 (MqttClientService): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("请求无效: " + e.getMessage());
        } catch (Exception e) {
            log.error("通过MqttClientService发布MQTT消息时发生未知错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("消息发布时发生未知错误 (MqttClientService): " + e.getMessage());
        }
    }
}
