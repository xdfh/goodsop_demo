package com.goodsop.iot.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodsop.iot.entity.IotDeviceStatus;
import com.goodsop.iot.model.dto.DeviceStatusMessageDTO;
import com.goodsop.iot.service.IIotDeviceStatusService;
import com.goodsop.iot.service.IotDeviceStatusHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IoT设备状态处理器实现类
 * 该类负责处理来自MQTT的设备状态上报消息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IotDeviceStatusHandlerImpl implements IotDeviceStatusHandler {

    private final IIotDeviceStatusService deviceStatusService;
    private final ObjectMapper objectMapper;
    
    // 解析主题的正则表达式：/{env}/{tenantId}/{deviceModel}/{branch}/{deviceId}/base/status
    // 根据需求文档，deviceId是第五个路径参数。例如: /dev/T001/MOD002/master/XYZ123/base/status
    private static final Pattern TOPIC_PATTERN = Pattern.compile("/([^/]+)/([^/]+)/([^/]+)/([^/]+)/([^/]+)/base/status");

    /**
     * 处理接收到的设备状态消息。
     *
     * @param topic   消息的主题
     * @param payload 消息的字节数组内容 (通常是JSON字符串)
     */
    @Override
    public void handleStatusMessage(String topic, byte[] payload) {
        log.info("开始处理设备状态消息，主题: {}", topic);
        try {
            String deviceId = extractDeviceIdFromTopic(topic);
            if (deviceId == null) {
                // 日志已在 extractDeviceIdFromTopic 方法中记录
                log.warn("由于无法从主题 {} 中提取有效的设备ID，消息处理终止。", topic);
                return;
            }
            
            String messageContent = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
            log.debug("设备状态消息原始内容 (UTF-8解码后): {}", messageContent);
            DeviceStatusMessageDTO messageDTO = objectMapper.readValue(messageContent, DeviceStatusMessageDTO.class);
            
            // 确保消息体中的deviceId与从主题中提取的一致
            // 如果消息体中没有deviceId，则使用从主题中提取的
            if (messageDTO.getDeviceId() == null || messageDTO.getDeviceId().trim().isEmpty()) {
                log.debug("消息体中未提供deviceId，将使用从主题 {} 中提取的deviceId: {}", topic, deviceId);
                messageDTO.setDeviceId(deviceId);
            } else if (!messageDTO.getDeviceId().equals(deviceId)) {
                log.warn("消息体中的deviceId ({}) 与从主题 {} 中提取的deviceId ({}) 不一致。将优先使用从主题中提取的ID。", 
                         messageDTO.getDeviceId(), topic, deviceId);
                messageDTO.setDeviceId(deviceId); // 或者根据业务规则决定如何处理这种冲突
            }
            
            IotDeviceStatus deviceStatus = convertToEntity(messageDTO);
            
            saveOrUpdateDeviceStatus(deviceStatus);
            
            log.info("设备状态消息处理成功: 主题={}, deviceId={}, 存储的状态详情={}", topic, deviceId, deviceStatus.toString());
        } catch (Exception e) {
            log.error("处理设备状态消息时发生严重异常: 主题={}", topic, e);
        }
    }

    /**
     * 从给定的MQTT主题字符串中提取设备ID。
     *
     * @param topic MQTT主题
     * @return 提取到的设备ID，如果无法提取或主题格式不匹配则返回null。
     */
    @Override
    public String extractDeviceIdFromTopic(String topic) {
        if (topic == null || topic.trim().isEmpty()) {
            log.warn("输入的主题为空或仅包含空白字符，无法提取设备ID。");
            return null;
        }
        Matcher matcher = TOPIC_PATTERN.matcher(topic);
        if (matcher.matches()) {
            // 根据正则表达式，deviceId 在第五个捕获组 (matcher.group(0)是整个匹配的字符串)
            if (matcher.groupCount() >= 5) { 
                String deviceId = matcher.group(4);
                log.debug("从主题 {} 中成功提取到设备ID: {}", topic, deviceId);
                return deviceId;
            } else {
                log.warn("主题 {} 匹配了模式，但捕获组数量不足 (期望至少5个，实际为 {}), 无法提取设备ID。模式: {}", topic, matcher.groupCount(), TOPIC_PATTERN.pattern());
            }
        } else {
            log.warn("主题 {} 未匹配预期的设备状态主题模式。预期模式: {}", topic, TOPIC_PATTERN.pattern());
        }
        return null;
    }
    
    /**
     * 将设备状态消息DTO (Data Transfer Object) 转换为数据库实体对象。
     *
     * @param dto 从消息解析得到的DTO对象
     * @return 转换后的 IotDeviceStatus 实体对象
     */
    private IotDeviceStatus convertToEntity(DeviceStatusMessageDTO dto) {
        IotDeviceStatus entity = new IotDeviceStatus();
        entity.setDeviceId(dto.getDeviceId());
        // 注意: 下列字段可能在 DeviceStatusMessageDTO 中不存在，需要根据实际DTO定义进行调整
        entity.setDeviceName(dto.getDeviceName()); 
        entity.setStatus(dto.getStatus());
        entity.setOnlineStatus(dto.getOnlineStatus()); 
        entity.setLastOnlineTime(dto.getLastOnlineTime() != null ? dto.getLastOnlineTime() : LocalDateTime.now()); // 如果DTO中没有上报时间，则使用当前时间
        entity.setRemark(dto.getRemark()); 
        entity.setUpdateTime(LocalDateTime.now()); // 记录状态更新的时间
        log.debug("将DeviceStatusMessageDTO转换为IotDeviceStatus实体完成: Input DTO={}, Output Entity={}", dto, entity);
        return entity;
    }
    
    /**
     * 将设备状态实体保存到数据库，如果已存在则更新，否则创建新记录。
     *
     * @param deviceStatus 要保存或更新的设备状态实体
     */
    private void saveOrUpdateDeviceStatus(IotDeviceStatus deviceStatus) {
        // 根据deviceId查询数据库中是否已存在该设备的状态记录
        IotDeviceStatus existingStatus = deviceStatusService.lambdaQuery()
                .eq(IotDeviceStatus::getDeviceId, deviceStatus.getDeviceId())
                .one(); // 查询单条记录
        
        if (existingStatus != null) {
            // 如果记录已存在，则更新
            log.debug("数据库中已存在设备 {} 的状态记录 (ID={})，准备更新。", deviceStatus.getDeviceId(), existingStatus.getId());
            deviceStatus.setId(existingStatus.getId()); // 设置ID以执行更新操作
            deviceStatus.setCreateTime(existingStatus.getCreateTime()); // 保留首次创建时间，不应在更新时修改
            boolean updated = deviceStatusService.updateById(deviceStatus);
            if (updated) {
                log.info("成功更新设备状态记录: deviceId={}", deviceStatus.getDeviceId());
            } else {
                log.warn("更新设备状态记录失败 (可能数据未变化或乐观锁冲突): deviceId={}", deviceStatus.getDeviceId());
            }
        } else {
            // 如果记录不存在，则创建新记录
            log.debug("数据库中不存在设备 {} 的状态记录，准备创建新记录。", deviceStatus.getDeviceId());
            deviceStatus.setCreateTime(LocalDateTime.now()); // 设置创建时间
            boolean saved = deviceStatusService.save(deviceStatus);
            if (saved) {
                log.info("成功新增设备状态记录: deviceId={}", deviceStatus.getDeviceId());
            } else {
                log.error("新增设备状态记录失败: deviceId={}", deviceStatus.getDeviceId());
            }
        }
    }
} 