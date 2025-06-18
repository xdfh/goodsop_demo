package com.goodsop.iot.service.impl;

import com.goodsop.iot.model.dto.EmqxAuthRequest;
import com.goodsop.iot.model.vo.EmqxAuthResponse;
import com.goodsop.iot.service.EmqxAuthService;
import com.goodsop.iot.model.dto.MqttMessageDto;
import com.goodsop.iot.config.EmqxConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class EmqxAuthServiceImpl implements EmqxAuthService {

    private final EmqxConfig emqxConfig;

    @Value("${device.auth.idPattern:GSDEV\\d{8}}")
    private String deviceIdPattern;

    @Value("${device.auth.timeFormat:yyyyMMddHHmm}")
    private String timeFormat;

    public EmqxAuthServiceImpl(EmqxConfig emqxConfig) {
        this.emqxConfig = emqxConfig;
    }

    @Override
    public EmqxAuthResponse authenticate(EmqxAuthRequest request) {
        try {
            // 尝试从deviceId字段获取设备ID，如果为空则使用username
            String deviceId = request.getUsername();
            
//            // 验证设备ID格式
//            if (!isValidDeviceId(deviceId)) {
//                log.warn("设备ID格式无效: {}", deviceId);
//                return new EmqxAuthResponse().setResult(false).set_superuser(false);
//            }

            // 生成预期的密码
            String expectedPassword = generateExpectedPassword(deviceId);

            // 验证密码
            boolean isValid = expectedPassword.equals(request.getPassword());
            log.info("设备认证结果: deviceId={}, result={}", deviceId, isValid);
            if(isValid){
                return new EmqxAuthResponse()
                        .setResult("allow");
            }else {
                return new EmqxAuthResponse()
                        .setResult("ignore");
            }

        } catch (Exception e) {
            log.error("设备认证过程发生错误", e);
            return new EmqxAuthResponse().setResult("allow");
        }
    }

    private boolean isValidDeviceId(String deviceId) {
        // 设备ID格式验证：使用配置中的正则表达式
        return deviceId != null && deviceId.matches(deviceIdPattern);
    }

    private String generateExpectedPassword(String deviceId) {
        // 获取当前时间，使用配置中的时间格式
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern(timeFormat));
        // 生成SHA256(设备ID+timeStr)
         String pwd = DigestUtils.sha256Hex(deviceId + timeStr);


        log.info("=======>>服务器时间: {}", timeStr);
        log.info("=======>>设备认证服务器生成的密码: {}", pwd);

        return pwd;
    }

} 