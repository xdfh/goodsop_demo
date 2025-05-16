package com.goodsop.iot.service;

import com.goodsop.iot.model.dto.EmqxAuthRequest;
import com.goodsop.iot.model.vo.EmqxAuthResponse;
import com.goodsop.iot.model.dto.MqttMessageDto;

public interface EmqxAuthService {
    EmqxAuthResponse authenticate(EmqxAuthRequest request);
}