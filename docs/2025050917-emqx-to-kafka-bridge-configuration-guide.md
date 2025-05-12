# EMQX 到 Kafka 桥接配置指南 (文件配置方式)

本文档指导您如何通过文件配置 EMQX (v5.8.6 开源版) 集群，以便将特定的 MQTT 消息桥接到已部署的 Kafka 集群。EMQX 5.x 的数据集成功能虽然主要通过 Dashboard 管理，但其核心的 Connector (资源) 和 Rule (规则) 也可以通过 HOCON 格式的配置文件进行设置。

## 1. 前提条件

- EMQX v5.8.6 集群已成功部署并正在运行。
- Kafka 集群已成功部署并正在运行。根据 `docs/2025050817-中间件部署代码.md`，Kafka brokers 内部监听地址为 `kafka1:9092`, `kafka2:9092`, `kafka3:9092`。
- 您能够访问 EMQX 节点在主机上映射的配置目录。

## 2. 网络确认

根据 `docs/2025050817-中间件部署代码.md`，您的 EMQX 容器和 Kafka 容器均已连接到 `kafka-net` Docker 网络。这允许 EMQX 容器直接使用 Kafka 服务名 (如 `kafka1:9092`) 进行通信，无需额外网络配置。

## 3. EMQX Kafka 桥接文件配置步骤

由于您使用的是3节点 EMQX 集群，并且每个节点的 `/opt/emqx/etc` 目录都独立映射到主机上的不同路径 (`D:\docker\emqx\node1\etc\`, `D:\docker\emqx\node2\etc\`, `D:\docker\emqx\node3\etc\`)，以下配置步骤需要在**每个 EMQX 节点对应的主机配置目录**中分别操作。

### 步骤 3.1: 创建 Kafka 桥接配置文件

在每个 EMQX 节点对应的主机配置目录下，例如 para `emqx-node1` 是 `D:\docker\emqx\node1\etc\`，创建一个新的配置文件，例如 `kafka_bridge.conf`。

**主机文件路径示例 (以 node1 为例):** `D:\docker\emqx\node1\etc\kafka_bridge.conf`

在该文件中，我们将定义 Kafka 连接器 (Resource) 和转发规则 (Rules)。

**`kafka_bridge.conf` 文件内容 (HOCON 格式):**

```hocon
# 定义 Kafka Producer 资源 (连接器)
resources {
  // 为您的 Kafka 连接器命名，例如 kafka_iot_producer
  kafka_iot_producer {
    // 指定资源类型为 kafka_producer
    type = kafka_producer
    // 连接器的具体配置
    config {
      // Kafka Broker 服务器列表
      servers = ["kafka1:9092", "kafka2:9092", "kafka3:9092"]
      
      // Kafka 消息的 Key 和 Value 编码类型
      // 根据 iot模块需求.md，payload 为 JSON，deviceId 可作为 key
      key_encode_type = plaintext  // Kafka 消息 Key 的编码 (例如：设备ID)
      value_encode_type = json     // Kafka 消息 Value 的编码 (原始MQTT payload)
      
      // Kafka 生产者参数
      compression = snappy        // 压缩类型
      acks = 1                    // 确认机制 (1 表示 leader 确认即可)
                                  // 可选: all (所有ISR确认), 0 (不确认)
      
      // 连接池大小等其他高级参数可按需配置
      // pool_size = 8
      // buffer_memory = "32MB"
      // batch_size = "16KB"
    }
  }
}

# 定义转发规则
rules {
  // 规则1: 设备状态消息转发到 Kafka
  iot_device_status_to_kafka {
    // SQL 定义了哪些消息会被处理
    // 从特定 MQTT 主题选择消息，并提取字段
    // MQTT Topic: /dev/goodsop/TENANT_ID/MODEL_ID/BRANCH_ID/DEVICE_ID/base/status
    sql = """
      SELECT
        payload.deviceId as kafka_message_key, // 将 payload 中的 deviceId 作为 Kafka key
        payload                               // 整个 MQTT payload 作为 Kafka value
      FROM
        "/dev/goodsop/+/master/+/base/status"  // 监听的 MQTT 主题，使用通配符
    """
    // 定义动作：将 SQL 选出的数据发送到指定的资源 (Kafka 连接器)
    actions = [
      {
        // 引用上面定义的 Kafka Producer 资源名
        name = kafka_iot_producer 
        // 传递给动作的参数
        params = {
          // 目标 Kafka Topic
          topic = "iot-device-status" 
          // Kafka 消息的 Key，使用 SQL 中选取的 kafka_message_key 字段
          key = "${kafka_message_key}"   
          // Kafka 消息的 Value，使用 SQL 中选取的 payload 字段
          // value = "${payload}" // 如果SQL中SELECT了整个payload，就这样写
                                // 或者，如果 value_encode_type=json 且 SQL SELECT payload,
                                // EMQX 通常会自动将选取的 payload 整体作为 JSON value。
                                // 如果 SQL SELECT 了多个字段，这里可以构建 JSON 对象:
                                // value = "{\"device_id\": \"${kafka_message_key}\", \"data\": ${payload}}"
        }
      }
    ]
    // 启用此规则
    enable = true
    // 规则描述 (可选)
    description = "Forward device status messages from MQTT to Kafka topic iot-device-status"
  }

  // 规则2: 设备事件消息转发到 Kafka
  iot_device_event_to_kafka {
    sql = """
      SELECT
        payload.deviceId as kafka_message_key,
        payload
      FROM
        "/dev/goodsop/+/master/+/base/event"
    """
    actions = [
      {
        name = kafka_iot_producer
        params = {
          topic = "iot-device-event"
          key = "${kafka_message_key}"
        }
      }
    ]
    enable = true
    description = "Forward device event messages from MQTT to Kafka topic iot-device-event"
  }

  // 规则3: 设备命令响应消息转发到 Kafka
  iot_device_cmd_response_to_kafka {
    sql = """
      SELECT
        payload.deviceId as kafka_message_key,
        payload
      FROM
        "/dev/goodsop/+/master/+/base/command/response"
    """
    actions = [
      {
        name = kafka_iot_producer
        params = {
          topic = "iot-device-command-response"
          key = "${kafka_message_key}"
        }
      }
    ]
    enable = true
    description = "Forward device command responses from MQTT to Kafka topic iot-device-command-response"
  }
}

```

**重要说明:**
-   `key_encode_type = plaintext` 和 `value_encode_type = json` 是示例，具体支持的类型和行为请参考 EMQX 5.8.6 文档。如果 `payload` 本身就是 JSON 字符串，直接将其作为 Kafka 消息的 Value 通常是可行的。
-   `${kafka_message_key}` 这样的占位符用于引用 SQL 查询结果中的字段。
-   MQTT 主题 `/dev/goodsop/+/master/+/base/status` 中的 `+` 是单层通配符，匹配任意租户ID、设备型号和设备ID。
-   确保 `kafka_iot_producer` 的名称在 `resources` 和 `rules.actions.name` 中一致。

### 步骤 3.2: 在主配置文件中加载桥接配置

在每个 EMQX 节点对应的主机配置目录下，编辑主配置文件 `emqx.conf`。
**主机文件路径示例 (以 node1 为例):** `D:\docker\emqx\node1\etc\emqx.conf`

在 `emqx.conf` 文件中，添加一行来加载您在步骤 3.1 中创建的 `kafka_bridge.conf` 文件。通常可以加在文件末尾或相关配置区域。

```hocon
# 在 emqx.conf 文件末尾添加:
include "etc/kafka_bridge.conf"
```
**注意:** 路径 `etc/kafka_bridge.conf` 是相对于 EMQX 容器内部的 `/opt/emqx/` 目录的。由于 `kafka_bridge.conf` 和 `emqx.conf` 都在容器内的 `/opt/emqx/etc/` 目录下，所以这样引用是正确的。

### 步骤 3.3: 重启 EMQX 节点

完成以上文件修改后，您需要重启所有的 EMQX Docker 容器以使配置生效。

```bash
docker restart emqx-node1
docker restart emqx-node2
docker restart emqx-node3
```

### 步骤 3.4: 检查 EMQX 日志

重启后，检查每个 EMQX 节点的日志，确认 `kafka_bridge.conf` 是否被成功加载，以及 Kafka 连接器和规则是否正确初始化，有无报错信息。

**查看日志命令示例 (以 node1 为例):**
```bash
docker logs emqx-node1
```
留意与 `kafka_iot_producer` 资源和相关规则（如 `iot_device_status_to_kafka`）相关的日志条目。

## 4. 移除或调整原文件中的 Dashboard 配置说明

由于您确认了 Dashboard 不适用于开源版的 Kafka 桥接配置，原文档中第 4 节 "使用 EMQX Dashboard 配置数据桥接 (推荐方式)" 可以被视为不适用或优先级较低。本文件已专注于文件配置方式。

## 5. 验证

配置并重启 EMQX 节点后，请按照 `docs/2025050917-kafka-topic-setup-and-verification-for-iot.md` 中的步骤进行验证：
1.  发送测试 MQTT 消息到 EMQX。
2.  检查 Kafka 中对应的 Topic 是否收到消息。

这能确保桥接配置按预期工作。 