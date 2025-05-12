# IoT 项目 Kafka Topic 设置与验证指南

本文档指导您如何为 IoT 项目设置和验证 Kafka Topics，确保它们能够正确接收来自 EMQX 桥接的消息。

## 1. 前提条件

- Kafka 集群已成功部署并正在运行。
- EMQX 到 Kafka 的桥接已按照 `docs/2025050917-emqx-to-kafka-bridge-configuration-guide.md` 中的说明配置完成并已激活。
- 您可以访问 Kafka 容器以执行命令行工具。

## 2. Kafka Topic 创建

根据您的 Kafka `docker-compose.yml` 配置 (通常位于 `docs/2025050817-中间件部署代码.md` 的 Kafka 部分)，如果设置了 `KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE=true` (Bitnami Kafka 镜像默认为 true)，Kafka 将在 EMQX（作为生产者）首次尝试向一个不存在的 Topic 发送消息时自动创建该 Topic。

目标 Kafka Topics (根据 `docs/2025050816-iot模块需求.md` 定义):
- `iot-device-status`
- `iot-device-event`
- `iot-device-command-response`

自动创建的 Topic 将使用 Kafka Broker 的默认分区数和副本因子设置。您的 `docker-compose.yml` 中已配置：
- `KAFKA_CFG_DEFAULT_REPLICATION_FACTOR=3`
- `KAFKA_CFG_OFFSETS_TOPIC_REPLICATION_FACTOR=3`
- `KAFKA_CFG_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=3`
- `KAFKA_CFG_MIN_INSYNC_REPLICAS=2`

分区策略（如按 `deviceId` 分区）建议在 EMQX 桥接规则中通过设置 Kafka 消息的 `key` 来实现，如 `docs/2025050917-emqx-to-kafka-bridge-configuration-guide.md` 中规则 SQL 的示例所示。如果 `key` 设置正确，Kafka 将根据 `key` 的哈希值将消息路由到特定分区。

## 3. 验证步骤

### 3.1 发送测试 MQTT 消息

使用任意 MQTT客户端 (如 MQTTX, mosquitto_pub 等) 向 EMQX 发布几条测试消息到配置了桥接规则的 MQTT 主题。

**示例 MQTT 主题和载荷:**

1.  **设备状态消息:**
    *   MQTT Topic: `dev/goodsop/yourtenant/yourmodel/master/TESTDEVICE001/base/status`
    *   Payload (JSON):
        ```json
        {
          "deviceId": "TESTDEVICE001",
          "messageType": "STATUS",
          "timestamp": 1673856245000,
          "deviceTime": 1673856240000,
          "payload": {
            "firmwareVersion": "v1.2.3",
            "batteryLevel": 85,
            "signalStrength": 75,
            "recordingStatus": 1,
            "storageUsage": 45,
            "errorCode": 0,
            "networkType": "4G",
            "uploadQueue": 3,
            "temperature": 36.5
          }
        }
        ```

2.  **设备事件消息:**
    *   MQTT Topic: `dev/goodsop/yourtenant/yourmodel/master/TESTDEVICE002/base/event`
    *   Payload (JSON):
        ```json
        {
          "deviceId": "TESTDEVICE002",
          "messageType": "EVENT",
          "timestamp": 1673856300000,
          "deviceTime": 1673856295000,
          "payload": {
            "eventType": "RECORDING_START",
            "data": {
              "fileId": "FILE_20230116_123130_67890",
              "startTime": 1673856295000,
              "estimatedDuration": 600000
            }
          }
        }
        ```

### 3.2 检查 Kafka Topics 是否已创建

在任一 Kafka 容器内执行以下命令列出所有 Kafka topics：

```bash
docker exec kafka1 /opt/bitnami/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092
```
或者，如果 `localhost:9092` 在容器内无法正确解析，请使用 broker 列表：
```bash
docker exec kafka1 /opt/bitnami/kafka/bin/kafka-topics.sh --list --bootstrap-server kafka1:9092,kafka2:9092,kafka3:9092
```

**预期结果:** 您应该在列表中看到 `iot-device-status` 和 `iot-device-event` (以及 `iot-device-command-response`，如果也测试了对应MQTT主题)。

### 3.3 消费 Kafka Topics 中的消息

在任一 Kafka 容器内执行以下命令来消费相应 Topic 中的消息：

1.  **消费 `iot-device-status`:**
    ```bash
    docker exec -it kafka1 /opt/bitnami/kafka/bin/kafka-console-consumer.sh --topic iot-device-status --bootstrap-server localhost:9092 --from-beginning --timeout-ms 10000
    ```
    (添加了 `--timeout-ms 10000` 以便命令在10秒后如果没有新消息则自动退出，方便脚本化检查。如果您想持续监听，可以去掉此参数。)

2.  **消费 `iot-device-event`:**
    ```bash
    docker exec -it kafka1 /opt/bitnami/kafka/bin/kafka-console-consumer.sh --topic iot-device-event --bootstrap-server localhost:9092 --from-beginning --timeout-ms 10000
    ```

**预期结果:** 您应该能看到之前通过 MQTT 发送并由 EMQX 桥接过来的测试消息。

### 3.4 检查 Kafka UI

如果您的 Kafka 集群部署了 Kafka UI (例如 `provectuslabs/kafka-ui`，如 `docs/2025050817-中间件部署代码.md` 中所示)，您可以访问其 Web 界面 (默认为 `http://localhost:38080`)。

-   查看 Topics 列表，确认 `iot-device-status`, `iot-device-event` 等主题已创建。
-   点击进入具体 Topic，查看其分区、副本信息以及消息内容。

## 4. 故障排查提示

-   **检查 EMQX 日志**：查看 EMQX broker 日志以及桥接相关的日志，查找是否有连接 Kafka 失败或消息转发错误等信息。
-   **检查 Kafka Broker 日志**：查看 Kafka broker 日志，确认是否有连接拒绝、Topic 创建问题或生产者错误等。
-   **网络连通性**：再次确认 EMQX 容器与 Kafka brokers 之间的网络连通性（参考 `docs/2025050917-emqx-to-kafka-bridge-configuration-guide.md` 中的网络注意事项）。可以使用 `ping` 或 `telnet` (如果容器内已安装) 从 EMQX 容器尝试连接 Kafka broker 的 IP 和端口。
-   **MQTT 主题与桥接规则匹配**：确保您发送的 MQTT 消息的主题能够被 EMQX 桥接规则中的 Topic Filter 正确匹配。
-   **Payload 格式**：如果 EMQX 桥接配置或规则引擎中涉及 Payload 解析或转换，确保实际发送的 Payload 格式符合预期。

通过以上步骤，您应该能够成功配置并验证 EMQX 到 Kafka 的消息桥接功能。 