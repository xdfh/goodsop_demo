package com.goodsop.iot.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmqxAuthRequest {
    private String clientid;
    private String username;
    private String password;
    private String ip;
    private String proto;
    private String mountpoint;
    private String deviceId;
} 