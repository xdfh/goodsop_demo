package com.goodsop.iot.service.impl;

import com.goodsop.auth.detail.AuthUserDetails;
import com.goodsop.auth.util.JwtTokenUtil;
import com.goodsop.iot.config.EmqxConfig;
import com.goodsop.iot.model.dto.EmqxAuthRequest;
import com.goodsop.iot.model.vo.EmqxAuthResponse;
import com.goodsop.iot.service.EmqxAuthService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class EmqxAuthServiceImpl implements EmqxAuthService {

    private final EmqxConfig emqxConfig;
    private final JwtTokenUtil jwtTokenUtil;

    @Value("${device.auth.idPattern:GSDEV\\d{8}}")
    private String deviceIdPattern;

    @Value("${device.auth.timeFormat:yyyyMMddHHmm}")
    private String timeFormat;

    @Autowired
    public EmqxAuthServiceImpl(EmqxConfig emqxConfig, JwtTokenUtil jwtTokenUtil) {
        this.emqxConfig = emqxConfig;
        this.jwtTokenUtil = jwtTokenUtil;
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
                // 生成符合系统规范的JWT token
                // 创建设备用户详情对象
                AuthUserDetails deviceUserDetails = new AuthUserDetails()
                        .setUserId(0L) // 设备用户ID设为0
                        .setUsername(deviceId)
                        .setNickname("设备_" + deviceId)
                        .setDeviceId(deviceId)
                        .setPassword(null)
                        .setAuthorities(AuthorityUtils.createAuthorityList("ROLE_DEVICE"));
                
                // 使用JWT工具类生成token
                String jwtToken = jwtTokenUtil.generateToken(deviceUserDetails);
                log.info("为设备 {} 生成JWT token", jwtToken);

                
                return new EmqxAuthResponse()
                        .setToken(jwtToken)
                        .setResult("allow");
            }else {
                return new EmqxAuthResponse()
                        .setResult("ignore");
            }

        } catch (Exception e) {
            log.error("设备认证过程发生错误", e);
            return new EmqxAuthResponse()
                    .setResult("ignore");
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