package com.goodsop.iot.model.vo;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class EmqxAuthResponse {
    private String result;

    private String token;
}