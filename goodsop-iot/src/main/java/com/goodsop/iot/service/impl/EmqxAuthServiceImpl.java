package com.goodsop.iot.service.impl;

import com.goodsop.iot.model.dto.EmqxAuthRequest;
import com.goodsop.iot.model.vo.EmqxAuthResponse;
import com.goodsop.iot.service.EmqxAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmqxAuthServiceImpl implements EmqxAuthService {

    @Value("${device.auth.idPattern:GSDEV\\d{8}}")
    private String deviceIdPattern;

    @Value("${device.auth.timeFormat:yyyyMMddHH}")
    private String timeFormat;

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
            
            return new EmqxAuthResponse()
                    .setResult(isValid)
                    .set_superuser(false);
        } catch (Exception e) {
            log.error("设备认证过程发生错误", e);
            return new EmqxAuthResponse().setResult(false).set_superuser(false);
        }
    }

    private boolean isValidDeviceId(String deviceId) {
        // 设备ID格式验证：使用配置中的正则表达式
        return deviceId != null && deviceId.matches(deviceIdPattern);
    }

    private String generateExpectedPassword(String deviceId) {
        // 获取当前时间，使用配置中的时间格式
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern(timeFormat));
        // 生成MD5(设备ID+timeStr)
        return DigestUtils.md5Hex(deviceId + timeStr);
    }
} 