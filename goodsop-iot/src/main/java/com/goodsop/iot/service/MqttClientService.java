package com.goodsop.iot.service;

import com.goodsop.iot.config.EmqxConfig;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * MQTT客户端服务
 * <p>
 * 负责管理MQTT客户端的整个生命周期，包括：
 * <ul>
 *     <li>初始化MQTT客户端实例 ({@link MqttClient})。</li>
 *     <li>使用配置的连接选项 ({@link MqttConnectOptions}) 连接到EMQX代理。</li>
 *     <li>设置消息回调处理器 ({@link MqttCallbackHandler})。</li>
 *     <li>在连接成功后订阅预定义的主题。</li>
 *     <li>在应用程序关闭时安全断开连接并释放资源。</li>
 * </ul>
 * 该服务在Spring Boot应用程序启动后通过 {@link PostConstruct} 注解的方法自动尝试连接。
 */
@Slf4j
@Service
public class MqttClientService {

    private final EmqxConfig emqxConfig;
    private final MqttConnectOptions mqttConnectOptions;
    private final MqttCallbackHandler mqttCallbackHandler; // 注入自定义的回调处理器

    private MqttClient mqttClient;

    @Autowired
    public MqttClientService(EmqxConfig emqxConfig, 
                             MqttConnectOptions mqttConnectOptions, 
                             MqttCallbackHandler mqttCallbackHandler) {
        this.emqxConfig = emqxConfig;
        this.mqttConnectOptions = mqttConnectOptions;
        this.mqttCallbackHandler = mqttCallbackHandler;
        log.info("MqttClientService 初始化，已注入EMQX配置、连接选项和回调处理器。");
    }

    /**
     * 在依赖注入完成后执行，用于初始化并连接MQTT客户端。
     * 这是Spring的生命周期回调注解。
     */
    @PostConstruct
    public void connect() {
        try {
            // 使用内存持久化，对于大多数服务端应用是足够的。如果需要磁盘持久化，可以使用 FilePersistence。
            MemoryPersistence persistence = new MemoryPersistence();
            String clientId = emqxConfig.getClientId();
            if (clientId == null || clientId.trim().isEmpty()) {
                // 如果配置文件中clientId为空，则生成一个随机的clientId，防止多个实例冲突
                clientId = MqttClient.generateClientId();
                log.warn("配置文件中未指定MQTT ClientID，已自动生成随机ClientID: {}", clientId);
            }
            
            this.mqttClient = new MqttClient(emqxConfig.getHost(), clientId, persistence);
            log.info("MQTT客户端实例已创建。服务器URI: {}, 客户端ID: {}", emqxConfig.getHost(), clientId);

            // 设置自定义的回调处理器
            this.mqttClient.setCallback(mqttCallbackHandler);
            log.info("已为MQTT客户端设置回调处理器: {}", mqttCallbackHandler.getClass().getName());

            log.info("准备连接到MQTT代理...");
            this.mqttClient.connect(mqttConnectOptions);
            // 连接成功后的订阅逻辑现在移至 MqttCallbackHandler 的 connectComplete 方法中，
            // 这样可以确保在（重新）连接成功时总是执行订阅。
            // 如果连接在 subscribeToTopics 之前丢失，connectComplete 仍会确保订阅。

        } catch (MqttException e) {
            log.error("MQTT客户端连接失败！原因: {} - {}", e.getReasonCode(), e.getMessage(), e);
            // 此处可以根据需要添加更复杂的重试逻辑或错误上报机制，
            // 但Paho客户端配置了自动重连 (setAutomaticReconnect(true))，它会自行尝试。
        } catch (Exception e) {
            log.error("初始化或连接MQTT客户端时发生未知异常。", e);
        }
    }

    /**
     * 订阅配置文件中定义的所有主题。
     * 此方法由 {@link MqttCallbackHandler#connectComplete(boolean, String)} 在连接成功后调用。
     */
    public void subscribeToTopics() {
        if (mqttClient == null || !mqttClient.isConnected()) {
            log.warn("MQTT客户端未连接，无法订阅主题。请等待连接成功。");
            return;
        }

        try {
            EmqxConfig.Topics topics = emqxConfig.getTopics();
            int qos = 1; // 对于服务端订阅，QoS 1 通常是合适的选择

            if (topics.getStatus() != null && !topics.getStatus().isEmpty()) {
                log.info("准备订阅设备状态主题: {}, QoS: {}", topics.getStatus(), qos);
                mqttClient.subscribe(topics.getStatus(), qos);
                log.info("成功订阅设备状态主题: {}", topics.getStatus());
            }

            if (topics.getEvent() != null && !topics.getEvent().isEmpty()) {
                log.info("准备订阅设备事件主题: {}, QoS: {}", topics.getEvent(), qos);
                mqttClient.subscribe(topics.getEvent(), qos);
                log.info("成功订阅设备事件主题: {}", topics.getEvent());
            }

            if (topics.getCommandResponse() != null && !topics.getCommandResponse().isEmpty()) {
                log.info("准备订阅设备指令响应主题: {}, QoS: {}", topics.getCommandResponse(), qos);
                mqttClient.subscribe(topics.getCommandResponse(), qos);
                log.info("成功订阅设备指令响应主题: {}", topics.getCommandResponse());
            }
            log.info("所有预定义的主题订阅尝试完成。");

        } catch (MqttException e) {
            log.error("订阅MQTT主题时发生错误: {} - {}", e.getReasonCode(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("订阅MQTT主题时发生未知异常。", e);
        }
    }

    /**
     * 在应用程序关闭前执行，用于断开MQTT连接并释放资源。
     * 这是Spring的生命周期回调注解。
     */
    @PreDestroy
    public void disconnect() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                log.info("准备断开MQTT客户端连接...");
                mqttClient.disconnect();
                log.info("MQTT客户端连接已成功断开。");
            } catch (MqttException e) {
                log.error("断开MQTT客户端连接时发生错误: {} - {}", e.getReasonCode(), e.getMessage(), e);
            } finally {
                closeClient();
            }
        } else {
            log.info("MQTT客户端未连接或已null，无需执行断开操作。");
            closeClient(); // 确保即使未连接也尝试关闭
        }
    }
    
    private void closeClient() {
        if (mqttClient != null) {
            try {
                mqttClient.close();
                log.info("MQTT客户端资源已关闭。");
            } catch (MqttException e) {
                log.error("关闭MQTT客户端资源时发生错误: {} - {}", e.getReasonCode(), e.getMessage(), e);
            }
        }
    }

    /**
     * 检查MQTT客户端是否已连接。
     *
     * @return 如果已连接则返回true，否则返回false。
     */
    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    // 未来可以添加发布消息的方法等
    // public void publish(String topic, String payload, int qos, boolean retained) throws MqttException {
    //     if (!isConnected()) {
    //         log.warn("MQTT客户端未连接，无法发布消息到主题: {}", topic);
    //         throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
    //     }
    //     MqttMessage message = new MqttMessage(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    //     message.setQos(qos);
    //     message.setRetained(retained);
    //     mqttClient.publish(topic, message);
    //     log.info("成功发布消息到主题: {}, QoS: {}, Retained: {}", topic, qos, retained);
    // }
} 