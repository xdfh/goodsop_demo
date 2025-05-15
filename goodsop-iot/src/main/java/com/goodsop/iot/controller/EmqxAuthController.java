package com.goodsop.iot.controller;

import com.goodsop.iot.model.dto.EmqxAuthRequest;
import com.goodsop.iot.model.vo.EmqxAuthResponse;
import com.goodsop.iot.service.EmqxAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/mqtt")
@RequiredArgsConstructor
public class EmqxAuthController {

    private final EmqxAuthService emqxAuthService;

    @PostMapping("/auth")
    public EmqxAuthResponse authenticate(@RequestBody EmqxAuthRequest request) {
        log.info("收到EMQX认证请求: clientid={}, username={}, ip={}", 
                request.getClientid(), request.getUsername(), request.getIp());
        
        // 如果username为空但deviceId不为空，则使用deviceId作为username
        if (request.getUsername() == null && request.getDeviceId() != null) {
            log.info("使用deviceId作为username: {}", request.getDeviceId());
        }
        
        EmqxAuthResponse response = emqxAuthService.authenticate(request);
        log.info("认证结果: {}", response.toString());
        
        return response;
    }
} 