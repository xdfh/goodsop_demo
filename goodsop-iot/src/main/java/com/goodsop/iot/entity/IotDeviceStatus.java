package com.goodsop.iot.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * IoT设备状态实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("t_iot_device_status")
@Schema(description = "IoT设备状态实体")
public class IotDeviceStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "设备ID", example = "DEVICE_001")
    @TableField("device_id")
    private String deviceId;

    @Schema(description = "设备名称", example = "温度传感器-01")
    @TableField("device_name")
    private String deviceName;

    @Schema(description = "设备状态：0-离线，1-在线，2-故障", example = "1")
    @TableField("status")
    private Integer status;

    @Schema(description = "在线状态：0-离线，1-在线", example = "1")
    @TableField("online_status")
    private Integer onlineStatus;

    @Schema(description = "最后在线时间")
    @TableField("last_online_time")
    private LocalDateTime lastOnlineTime;

    @Schema(description = "创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField("update_time")
    private LocalDateTime updateTime;

    @Schema(description = "备注", example = "位于一号车间")
    @TableField("remark")
    private String remark;
} 