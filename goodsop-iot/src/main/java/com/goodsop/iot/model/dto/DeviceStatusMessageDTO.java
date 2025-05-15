package com.goodsop.iot.model.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 设备状态消息数据传输对象
 */
@Data
public class DeviceStatusMessageDTO {
    /**
     * 设备ID
     */
    private String deviceId;
    
    /**
     * 设备名称
     */
    private String deviceName;
    
    /**
     * 设备状态：0-离线，1-在线，2-故障
     */
    private Integer status;
    
    /**
     * 在线状态：0-离线，1-在线
     */
    private Integer onlineStatus;
    
    /**
     * 最后在线时间
     */
    private LocalDateTime lastOnlineTime;
    
    /**
     * 备注信息
     */
    private String remark;
} 