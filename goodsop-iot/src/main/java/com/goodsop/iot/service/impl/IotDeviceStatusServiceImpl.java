package com.goodsop.iot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.goodsop.iot.entity.IotDeviceStatus;
import com.goodsop.iot.mapper.IotDeviceStatusMapper;
import com.goodsop.iot.service.IIotDeviceStatusService;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

/**
 * IoT设备状态Service实现类
 */
@Slf4j
@Service
public class IotDeviceStatusServiceImpl extends ServiceImpl<IotDeviceStatusMapper, IotDeviceStatus> implements IIotDeviceStatusService {
} 