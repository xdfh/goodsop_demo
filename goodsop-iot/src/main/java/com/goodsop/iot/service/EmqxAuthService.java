package com.goodsop.iot.service;

import com.goodsop.iot.model.dto.EmqxAuthRequest;
import com.goodsop.iot.model.vo.EmqxAuthResponse;

public interface EmqxAuthService {
    EmqxAuthResponse authenticate(EmqxAuthRequest request);
} 