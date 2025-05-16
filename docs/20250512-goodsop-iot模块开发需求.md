# GoodSop IoT模块需求文档

## 1. 项目概述

GoodSop IoT模块是整个GoodSop平台中负责处理物联网设备通信、数据收集和设备管理的核心组件。该模块基于Spring Boot 3.1.5构建，使用JDK 17，主要负责与音频采集设备进行MQTT通信，处理设备上报的状态和事件信息，并提供设备管理和控制功能。

## 2. 系统架构

### 2.1 数据流向

```
设备状态等信息 -> MQTT 5.8.6开源版集群 -> goodsop-iot模块内消费信息 -> 保存到PostgreSQL数据库
```

### 2.2 技术选型

- [   ] 开发框架：Spring Boot 3.1.5
- [   ] 开发语言：Java 17
- [   ] 消息通信：MQTT 5.8.6
- [   ] 数据存储：PostgreSQL 1.17
- [   ] 持久层框架：MyBatis-Plus
- [   ] API文档：Knife4j
- [   ] 代码简化：Lombok
- [   ] 日志框架：SLF4J
- [   ] 测试框架：JUnit 5 + Mockito

## 3. 功能需求

### 3.1 设备连接管理

#### 3.1.1 设备认证机制

- [   ] 采用HTTP方式进行设备认证
- [   ] 认证参数：
  - [   ] username = 设备ID
  - [   ] password = MD5(设备ID + YYYYMMDDHHmm)
- [   ] 认证通过后允许设备建立MQTT连接

#### 3.1.2 设备连接状态监控

- [   ] 监控设备的在线/离线状态
- [   ] 记录设备最后连接时间和断开时间
- [   ] 提供设备连接状态查询接口

### 3.2 MQTT主题设计

MQTT主题采用以下格式设计：
```
/{env}/{tenantId}/{deviceType}/{deviceId}/{endpoint}
```

各部分含义：
- [   ] `{env}` - 环境标识（dev, test, prod）
- [   ] `{tenantId}` - B端客户租户ID
- [   ] `{deviceType}` - 设备类型（如audio-recorder）
- [   ] `{deviceId}` - 设备唯一标识符
- [   ] `{endpoint}` - 终端类别（base-设备端，app-应用端）

具体Topic定义：

1. [   ] 设备状态上报Topic：
   ```
   /{env}/{tenantId}/{deviceType}/{deviceId}/base/status
   ```

2. [   ] 设备事件上报Topic：
   ```
   /{env}/{tenantId}/{deviceType}/{deviceId}/base/event
   ```

3. [   ] 设备命令下发Topic（设备订阅）：
   ```
   /{env}/{tenantId}/{deviceType}/{deviceId}/base/command
   ```

4. [   ] 设备命令响应Topic（设备发布）：
   ```
   /{env}/{tenantId}/{deviceType}/{deviceId}/base/command/response
   ```

5. [   ] APP命令下发Topic（APP订阅）：
   ```
   /{env}/{tenantId}/{deviceType}/{deviceId}/app/command
   ```

6. [   ] 系统广播Topic：
   ```
   # 环境级广播
   /{env}/broadcast
   
   # 租户级广播
   /{env}/{tenantId}/broadcast
   
   # 设备类型级广播
   /{env}/{tenantId}/{deviceType}/broadcast
   ```

7. [   ] 设备分组Topic：
   ```
   /{env}/{tenantId}/groups/{groupId}/command
   ```

8. [   ] 设备状态Topic（LWT遗嘱消息）：
   ```
   /{env}/{tenantId}/{deviceType}/{deviceId}/state
   ```

订阅规则：
- [   ] 设备订阅：
  - [   ] 设备消息：`/{env}/{tenantId}/{deviceType}/{deviceId}/base/#`
  - [   ] 设备类型广播：`/{env}/{tenantId}/{deviceType}/broadcast`
  - [   ] 所属分组：`/{env}/{tenantId}/groups/+/command`（如果设备支持分组）

- [   ] APP订阅：
  - [   ] 应用消息：`/{env}/{tenantId}/{deviceType}/{deviceId}/app/#` 
  - [   ] 设备状态：`/{env}/{tenantId}/{deviceType}/{deviceId}/base/status`
  - [   ] 设备状态(LWT)：`/{env}/{tenantId}/{deviceType}/{deviceId}/state`

- [   ] 服务器订阅：
  - [   ] 按租户分组订阅：`/{env}/{specificTenantId}/+/+/base/status`
  - [   ] 事件处理服务：`/{env}/{specificTenantId}/+/+/base/event`
  - [   ] 命令响应服务：`/{env}/{specificTenantId}/+/+/base/command/response`
  - [   ] 设备状态变更：`/{env}/{specificTenantId}/+/+/state`

### 3.2.1 消息质量等级(QoS)设置

为确保消息可靠性和系统性能的平衡，设置不同类型消息的QoS级别：

- [   ] 状态消息：QoS 0（最多一次）- 定期上报，丢失单条影响小
- [   ] 事件消息：QoS 1（至少一次）- 确保送达，允许重复
- [   ] 命令消息：QoS 2（恰好一次）- 确保精确执行，不重复
- [   ] 广播消息：QoS 1（至少一次）- 确保送达，允许重复

### 3.2.2 保留消息和遗嘱消息

1. [   ] 保留消息：
   - [   ] 设备状态Topic设为保留消息，新连接客户端可立即获取最新状态
   - [   ] 格式：`/{env}/{tenantId}/{deviceType}/{deviceId}/base/status` (RETAIN=true)

2. [   ] 遗嘱消息(LWT)：
   - [   ] 设备连接MQTT服务器时设置遗嘱消息，当异常断开时自动发送
   - [   ] 主题：`/{env}/{tenantId}/{deviceType}/{deviceId}/state`
   - [   ] 内容：`{"status":"offline","timestamp":1234567890}`

### 3.2.3 访问控制策略

实施MQTT服务器访问控制列表(ACL)，确保安全性：

1. [   ] 设备权限：
   - [   ] 允许发布：自身ID相关主题
   - [   ] 允许订阅：自身ID相关主题、所属分组主题、广播主题

2. [   ] 应用权限：
   - [   ] 允许订阅：指定设备状态和事件
   - [   ] 允许发布：指定设备命令

3. [   ] 服务器权限：
   - [   ] 完全访问权限，但通过内部安全策略限制

### 3.3 设备消息处理

#### 3.3.1 设备状态信息处理

系统需要处理并存储设备上报的状态信息，包括但不限于：

| 字段名 | 类型 | 说明 | 示例值 |
|-------|------|------|--------|
| [   ] deviceId | String | 设备唯一标识符 | "GSDEV12345678" |
| [   ] firmwareVersion | String | 当前固件版本 | "v1.2.3" |
| [   ] batteryLevel | Integer | 电池电量百分比 | 85 |
| [   ] signalStrength | Integer | 信号强度（0-100） | 75 |
| [   ] recordingStatus | Integer | 录音状态（0-待机，1-录音中，2-暂停，3-故障） | 1 |
| [   ] storageUsage | Integer | 存储空间使用百分比 | 45 |
| [   ] errorCode | Integer | 错误代码（0表示正常） | 0 |
| [   ] timestamp | Long | 服务器接收状态时间戳（毫秒） | 1673856245000 |
| [   ] deviceTime | Long | 设备本机时间戳（毫秒） | 1673856240000 |
| [   ] networkType | String | 网络连接类型 | "4G/WIFI" |
| [   ] uploadQueue | Integer | 待上传文件数量 | 5 |
| [   ] temperature | Float | 设备温度（摄氏度） | 37.5 |

#### 3.3.2 设备事件信息处理

系统需要处理并存储设备上报的事件信息，主要事件类型包括：

| 事件类型 | 说明 | 附带信息 |
|---------|------|----------|
| [   ] POWER_ON | 设备开机 | 启动时间、电池电量 |
| [   ] POWER_OFF | 设备关机 | 关机方式（正常/异常）、电池电量 |
| [   ] RECORDING_START | 开始录音 | 文件标识、起始时间、预计时长 |
| [   ] RECORDING_STOP | 结束录音 | 文件标识、结束时间、实际时长、文件大小 |
| [   ] UPLOAD_START | 开始上传文件 | 文件标识、文件大小、文件类型 |
| [   ] UPLOAD_COMPLETE | 完成文件上传 | 文件标识、服务器返回信息 |
| [   ] UPLOAD_FAILED | 文件上传失败 | 文件标识、错误信息、重试次数 |
| [   ] LOW_BATTERY | 低电量警告 | 当前电量百分比 |
| [   ] ERROR | 设备错误 | 错误代码、错误描述 |
| [   ] CONFIG_CHANGED | 配置变更 | 变更项、变更值 |

### 3.4 设备控制命令

IoT模块需要提供以下设备控制功能：

1. [   ] 远程启动/停止录音
2. [   ] 获取设备当前状态
3. [   ] 设备配置更新
4. [   ] 固件更新通知
5. [   ] 远程重启设备
6. [   ] 网络配置修改
7. [   ] 系统时间同步

### 3.5 设备固件管理

1. [   ] 发布固件版本
2. [   ] 推送固件更新通知
3. [   ] 监控固件更新进度
4. [   ] 固件版本回滚

## 4. 非功能需求

### 4.1 性能需求

1. [   ] 系统应能支持至少1000台设备并发连接
2. [   ] 消息处理延迟不超过500毫秒
3. [   ] 数据库写入性能满足每秒1000条记录的要求
4. [   ] API接口响应时间不超过200毫秒

### 4.2 安全需求

1. [   ] 所有通信采用TLS加密
2. [   ] 设备认证机制确保只有授权设备可以连接
3. [   ] API接口需实现权限控制
4. [   ] 敏感数据需要加密存储

### 4.3 可靠性需求

1. [   ] 系统可用性达到99.9%
2. [   ] 支持消息队列缓存，避免消息丢失
3. [   ] 实现数据定期备份机制
4. [   ] 提供系统监控和报警功能

## 5. 数据库设计

### 5.1 设备状态历史表

[   ] 创建设备状态历史表：
```sql
CREATE TABLE t_iot_device_status (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    battery_level INT,
    signal_strength INT,
    recording_status INT,
    storage_usage INT,
    error_code INT,
    network_type VARCHAR(16),
    temperature FLOAT,
    device_time BIGINT,
    status_time TIMESTAMP NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE t_iot_device_status IS '设备状态历史表';
COMMENT ON COLUMN t_iot_device_status.id IS '主键ID';
COMMENT ON COLUMN t_iot_device_status.device_id IS '设备唯一标识符';
COMMENT ON COLUMN t_iot_device_status.battery_level IS '电池电量百分比';
COMMENT ON COLUMN t_iot_device_status.signal_strength IS '信号强度(0-100)';
COMMENT ON COLUMN t_iot_device_status.recording_status IS '录音状态(0-待机,1-录音中,2-暂停,3-故障)';
COMMENT ON COLUMN t_iot_device_status.storage_usage IS '存储空间使用百分比';
COMMENT ON COLUMN t_iot_device_status.error_code IS '错误代码(0表示正常)';
COMMENT ON COLUMN t_iot_device_status.network_type IS '网络连接类型';
COMMENT ON COLUMN t_iot_device_status.temperature IS '设备温度(摄氏度)';
COMMENT ON COLUMN t_iot_device_status.device_time IS '设备本机时间戳(毫秒)';
COMMENT ON COLUMN t_iot_device_status.status_time IS '状态记录时间';
COMMENT ON COLUMN t_iot_device_status.create_time IS '创建时间';
COMMENT ON COLUMN t_iot_device_status.is_deleted IS '是否删除(0-未删除,1-已删除)';
```

### 5.2 设备事件表

[   ] 创建设备事件表：
```sql
CREATE TABLE t_iot_device_event (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    event_data JSONB NOT NULL,
    event_time TIMESTAMP NOT NULL,
    device_time BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE t_iot_device_event IS '设备事件表';
COMMENT ON COLUMN t_iot_device_event.id IS '主键ID';
COMMENT ON COLUMN t_iot_device_event.device_id IS '设备唯一标识符';
COMMENT ON COLUMN t_iot_device_event.event_type IS '事件类型';
COMMENT ON COLUMN t_iot_device_event.event_data IS '事件数据(JSON格式)';
COMMENT ON COLUMN t_iot_device_event.event_time IS '事件时间';
COMMENT ON COLUMN t_iot_device_event.device_time IS '设备本机时间戳(毫秒)';
COMMENT ON COLUMN t_iot_device_event.create_time IS '创建时间';
COMMENT ON COLUMN t_iot_device_event.is_deleted IS '是否删除(0-未删除,1-已删除)';
```

### 5.3 设备信息表

[   ] 创建设备信息表：
```sql
CREATE TABLE t_iot_device (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL UNIQUE,
    tenant_id VARCHAR(64) NOT NULL,
    device_model VARCHAR(32) NOT NULL,
    firmware_version VARCHAR(32),
    branch VARCHAR(32) DEFAULT 'master',
    online_status INT DEFAULT 0,
    last_online_time TIMESTAMP,
    last_offline_time TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE t_iot_device IS '设备信息表';
COMMENT ON COLUMN t_iot_device.id IS '主键ID';
COMMENT ON COLUMN t_iot_device.device_id IS '设备唯一标识符';
COMMENT ON COLUMN t_iot_device.tenant_id IS '租户ID';
COMMENT ON COLUMN t_iot_device.device_model IS '设备型号';
COMMENT ON COLUMN t_iot_device.firmware_version IS '固件版本';
COMMENT ON COLUMN t_iot_device.branch IS '分支';
COMMENT ON COLUMN t_iot_device.online_status IS '在线状态(0-离线,1-在线)';
COMMENT ON COLUMN t_iot_device.last_online_time IS '最后上线时间';
COMMENT ON COLUMN t_iot_device.last_offline_time IS '最后离线时间';
COMMENT ON COLUMN t_iot_device.create_time IS '创建时间';
COMMENT ON COLUMN t_iot_device.update_time IS '更新时间';
COMMENT ON COLUMN t_iot_device.is_deleted IS '是否删除(0-未删除,1-已删除)';
```

### 5.4 固件版本表

[   ] 创建固件版本表：
```sql
CREATE TABLE t_iot_firmware (
    id BIGSERIAL PRIMARY KEY,
    firmware_id VARCHAR(64) NOT NULL UNIQUE,
    device_model VARCHAR(32) NOT NULL,
    version VARCHAR(32) NOT NULL,
    branch VARCHAR(32) DEFAULT 'master',
    file_url VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    md5_checksum VARCHAR(32) NOT NULL,
    release_notes TEXT,
    status INT DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE t_iot_firmware IS '固件版本表';
COMMENT ON COLUMN t_iot_firmware.id IS '主键ID';
COMMENT ON COLUMN t_iot_firmware.firmware_id IS '固件ID';
COMMENT ON COLUMN t_iot_firmware.device_model IS '适用设备型号';
COMMENT ON COLUMN t_iot_firmware.version IS '版本号';
COMMENT ON COLUMN t_iot_firmware.branch IS '分支';
COMMENT ON COLUMN t_iot_firmware.file_url IS '固件文件URL';
COMMENT ON COLUMN t_iot_firmware.file_size IS '文件大小(字节)';
COMMENT ON COLUMN t_iot_firmware.md5_checksum IS 'MD5校验和';
COMMENT ON COLUMN t_iot_firmware.release_notes IS '发布说明';
COMMENT ON COLUMN t_iot_firmware.status IS '状态(0-草稿,1-已发布,2-已废弃)';
COMMENT ON COLUMN t_iot_firmware.create_time IS '创建时间';
COMMENT ON COLUMN t_iot_firmware.update_time IS '更新时间';
COMMENT ON COLUMN t_iot_firmware.is_deleted IS '是否删除(0-未删除,1-已删除)';
```

## 6. 接口设计

### 6.1 MQTT消息接口

#### 6.1.1 设备状态消息格式

[   ] 实现设备状态消息格式：
```json
{
  "deviceId": "GSDEV12345678",
  "firmwareVersion": "v1.2.3",
  "batteryLevel": 85,
  "signalStrength": 75,
  "recordingStatus": 1,
  "storageUsage": 45,
  "errorCode": 0,
  "timestamp": 1673856245000,
  "deviceTime": 1673856240000,
  "networkType": "4G",
  "uploadQueue": 5,
  "temperature": 37.5
}
```

#### 6.1.2 设备事件消息格式

[   ] 实现设备事件消息格式：
```json
{
  "deviceId": "GSDEV12345678",
  "eventType": "RECORDING_START",
  "eventData": {
    "fileId": "REC20250512123456",
    "startTime": 1673856245000,
    "expectedDuration": 3600
  },
  "timestamp": 1673856245000,
  "deviceTime": 1673856240000
}
```

#### 6.1.3 设备命令格式

[   ] 实现设备命令格式：
```json
{
  "commandId": "CMD20250512123456",
  "commandType": "START_RECORDING",
  "params": {
    "duration": 3600,
    "quality": "HIGH"
  },
  "timestamp": 1673856245000
}
```

#### 6.1.4 命令响应格式

[   ] 实现命令响应格式：
```json
{
  "commandId": "CMD20250512123456",
  "result": "SUCCESS",
  "data": {
    "fileId": "REC20250512123456"
  },
  "timestamp": 1673856245000,
  "deviceTime": 1673856240000
}
```

### 6.2 HTTP REST API接口

#### 6.2.1 设备管理API

1. [   ] 获取设备列表
2. [   ] 获取设备详情
3. [   ] 添加设备
4. [   ] 更新设备信息
5. [   ] 删除设备

#### 6.2.2 设备状态API

1. [   ] 获取设备当前状态
2. [   ] 获取设备历史状态
3. [   ] 获取设备状态统计信息

#### 6.2.3 设备事件API

1. [   ] 获取设备事件列表
2. [   ] 获取设备事件详情
3. [   ] 获取设备事件统计信息

#### 6.2.4 设备控制API

1. [   ] 发送命令到设备
2. [   ] 查询命令执行状态
3. [   ] 批量发送命令

#### 6.2.5 固件管理API

1. [   ] 上传新固件
2. [   ] 获取固件列表
3. [   ] 获取固件详情
4. [   ] 推送固件更新
5. [   ] 获取固件更新状态

## 7. 技术实现方案

### 7.1 MQTT客户端实现

[   ] 使用Eclipse Paho MQTT客户端库实现与MQTT服务器的通信，负责：
1. [   ] 建立与MQTT服务器的连接
2. [   ] 订阅相关主题
3. [   ] 处理接收到的消息
4. [   ] 发布命令到设备
5. [   ] 配置遗嘱消息(LWT)和保留消息
6. [   ] 实现断线重连机制
7. [   ] 支持消息QoS级别管理

### 7.2 设备认证实现

[   ] 实现HTTP认证服务，处理设备连接请求：
1. [   ] 验证设备ID有效性
2. [   ] 验证密码是否符合规则
3. [   ] 记录认证日志

### 7.3 消息处理实现

[   ] 采用消费者模式处理接收到的MQTT消息：
1. [   ] 状态消息处理器
2. [   ] 事件消息处理器
3. [   ] 命令响应处理器

### 7.4 数据持久化实现

[   ] 使用MyBatis-Plus实现数据访问层：
1. [   ] 设备数据访问对象
2. [   ] 状态数据访问对象
3. [   ] 事件数据访问对象
4. [   ] 固件数据访问对象

### 7.5 API实现

[   ] 使用Spring Boot实现RESTful API接口：
1. [   ] 控制器层实现
2. [   ] 服务层实现
3. [   ] 数据传输对象设计
4. [   ] 集成Knife4j生成API文档

## 8. 测试计划

### 8.1 单元测试

1. [   ] 服务层单元测试
2. [   ] 数据访问层测试
3. [   ] 控制器层测试
4. [   ] MQTT消息处理测试

### 8.2 集成测试

1. [   ] API接口集成测试
2. [   ] 数据库集成测试
3. [   ] MQTT通信集成测试

### 8.3 性能测试

1. [   ] 并发连接测试
2. [   ] 消息吞吐量测试
3. [   ] 数据库性能测试
4. [   ] API响应时间测试

### 8.4 安全测试

1. [   ] 认证机制测试
2. [   ] 加密通信测试
3. [   ] API权限测试
4. [   ] 敏感数据保护测试

## 9. 部署计划

1. [   ] 开发环境部署
2. [   ] 测试环境部署
3. [   ] 生产环境部署
4. [   ] 扩展性设计
5. [   ] 灾备方案

## 10. 项目里程碑

| 阶段 | 任务 | 计划完成时间 |
|-----|------|------------|
| 设计阶段 | [   ] 完成系统设计文档 | 第1周 |
| 开发阶段 | [   ] 实现MQTT客户端和设备认证 | 第2周 |
| | [   ] 实现设备状态和事件处理 | 第3周 |
| | [   ] 实现设备命令管理 | 第4周 |
| | [   ] 实现固件管理 | 第5周 |
| | [   ] 实现API接口 | 第6周 |
| 测试阶段 | [   ] 单元测试和集成测试 | 第7周 |
| | [   ] 性能测试和安全测试 | 第8周 |
| 部署阶段 | [   ] 环境部署和验收 | 第9-10周 |

## 11. 风险与缓解措施

| 风险 | 影响 | 缓解措施 |
|-----|------|---------|
| [   ] MQTT服务器性能瓶颈 | 可能导致消息延迟或丢失 | 实施集群方案，增加监控和预警 |
| [   ] 设备认证安全风险 | 可能导致未授权设备连接 | 实施强认证机制，定期更新密钥 |
| [   ] 数据库性能问题 | 可能影响系统整体性能 | 采用分表分库策略，实施数据归档 |
| [   ] 网络不稳定 | 可能导致设备连接中断 | 实施重连机制，数据本地缓存 | 