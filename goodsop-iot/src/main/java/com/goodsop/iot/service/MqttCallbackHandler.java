package com.goodsop.iot.service;

import com.goodsop.iot.config.EmqxConfig;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * MQTT回调处理器
 * <p>
 * 实现 {@link MqttCallbackExtended} 接口，用于处理与MQTT代理的连接状态变化
 * (如连接成功、连接丢失)以及接收到的消息。
 */
@Slf4j
@Component
public class MqttCallbackHandler implements MqttCallbackExtended {

    private final EmqxConfig emqxConfig;
    private final IotDeviceStatusHandler iotDeviceStatusHandler; 
    // private final IotDeviceEventHandler iotDeviceEventHandler; // 未来可扩展事件处理器
    // private final IotCommandResponseHandler iotCommandResponseHandler; // 未来可扩展指令响应处理器
    
    private MqttClientService mqttClientService; // 用于在连接成功后重新订阅主题

    /**
     * 构造函数注入依赖。
     *
     * @param emqxConfig             EMQX配置信息，用于获取主题定义。
     * @param iotDeviceStatusHandler 设备状态消息处理器。
     *                               // 其他处理器可在此处添加
     */
    @Autowired
    public MqttCallbackHandler(EmqxConfig emqxConfig, 
                               IotDeviceStatusHandler iotDeviceStatusHandler) {
                               // IotDeviceEventHandler iotDeviceEventHandler, 
                               // IotCommandResponseHandler iotCommandResponseHandler) {
        this.emqxConfig = emqxConfig;
        this.iotDeviceStatusHandler = iotDeviceStatusHandler;
        // this.iotDeviceEventHandler = iotDeviceEventHandler;
        // this.iotCommandResponseHandler = iotCommandResponseHandler;
        log.info("MqttCallbackHandler 初始化完成，已注入EMQX配置和消息处理器。");
    }

    /**
     * 注入 MqttClientService (使用 @Lazy 解决循环依赖问题)。
     * MqttClientService 可能也需要 MqttCallbackHandler。
     * @param mqttClientService MQTT客户端服务
     */
    @Autowired
    public void setMqttClientService(@Lazy MqttClientService mqttClientService) {
        this.mqttClientService = mqttClientService;
    }

    /**
     * 当与MQTT代理的连接成功建立时调用。
     * <p>
     * 如果是自动重连后的连接成功，reconnect参数为true。
     * 在此方法中，通常会重新订阅之前的主题。
     *
     * @param reconnect         如果为true，表示这是一次自动重连后的连接成功。
     * @param serverURI         成功连接到的服务器URI。
     */
    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("MQTT连接成功完成。是否为重连: {}, 服务器URI: {}", reconnect, serverURI);
        // 连接成功后，重新订阅所有必要的主题
        if (mqttClientService != null) {
            log.info("尝试重新订阅主题...");
            mqttClientService.subscribeToTopics();
        } else {
            log.warn("MqttClientService尚未注入，无法在连接成功后自动重新订阅主题。");
        }
    }

    /**
     * 当与MQTT代理的连接丢失时调用。
     *
     * @param cause 连接丢失的原因异常。
     */
    @Override
    public void connectionLost(Throwable cause) {
        log.error("MQTT连接丢失！原因: {}", cause.getMessage(), cause);
        // 自动重连通常由 MqttConnectOptions 中的 setAutomaticReconnect(true) 处理
        // 此处可以添加额外的逻辑，例如记录更详细的诊断信息或通知相关模块
    }

    /**
     * 当接收到来自MQTT代理的消息时调用。
     *
     * @param topic       消息的主题。
     * @param mqttMessage 接收到的MQTT消息对象 {@link MqttMessage}。
     * @throws Exception 如果处理消息时发生任何异常。
     */
    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
        String payload = new String(mqttMessage.getPayload(), java.nio.charset.StandardCharsets.UTF_8);
        log.info("===> MQTT消息到达 <=== 主题 (Topic): [{}], QoS: [{}], 保留消息 (Retained): [{}], 消息内容 (Payload): {}", 
                 topic, mqttMessage.getQos(), mqttMessage.isRetained(), payload);

        try {
            // 根据主题路由到不同的处理器
            // 注意：这里使用的是Java的startsWith方法进行简单匹配，对于使用通配符订阅的主题，
            // 服务端收到的topic是具体的、匹配了通配符的那个主题。
            // 例如，如果订阅了 /dev/+/+/+/+/base/status，收到的可能是 /dev/env1/tenant1/model1/dev1/base/status

            // 主题定义应该从 emqxConfig.getTopics() 获取，并进行更精确的匹配，
            // 例如使用 TopicMatcher 工具类或更完善的路由逻辑。
            // 为简化起见，这里直接比较，但实际项目中建议优化。
            
            String statusTopicPattern = emqxConfig.getTopics().getStatus().replace("/+", "/[^/]+"); // 简单转换为正则
            // TODO: 实际项目中，应该使用更健壮的方式来匹配通配符主题，例如 AntPathMatcher 或专门的MQTT主题匹配库

            if (topic.matches(topicToRegex(emqxConfig.getTopics().getStatus()))) {
                 log.debug("消息主题 {} 匹配设备状态主题模式，交由 IotDeviceStatusHandler 处理。", topic);
                iotDeviceStatusHandler.handleStatusMessage(topic, mqttMessage.getPayload());
            }
            // else if (topic.matches(topicToRegex(emqxConfig.getTopics().getEvent()))) {
            // log.debug("消息主题 {} 匹配设备事件主题模式，交由 IotDeviceEventHandler 处理。", topic);
            //     iotDeviceEventHandler.handleEventMessage(topic, mqttMessage.getPayload());
            // }
            // else if (topic.matches(topicToRegex(emqxConfig.getTopics().getCommandResponse()))) {
            // log.debug("消息主题 {} 匹配指令响应主题模式，交由 IotCommandResponseHandler 处理。", topic);
            //     iotCommandResponseHandler.handleCommandResponseMessage(topic, mqttMessage.getPayload());
            // }
            else {
                log.warn("接收到MQTT消息，但其主题 {} 未匹配任何已知的处理器规则。消息内容: {}", topic, payload);
            }
        } catch (Exception e) {
            log.error("处理到达的MQTT消息时发生异常: 主题={}, 消息ID={}, QoS={}", 
                      topic, mqttMessage.getId(), mqttMessage.getQos(), e);
            // 根据需要，可以选择是否向上抛出异常。如果Paho客户端配置了自动重连，
            // 抛出异常可能会影响客户端的某些内部状态或重连逻辑，需谨慎。
        }
    }

    /**
     * 将MQTT主题通配符（+和#）转换为Java正则表达式的模式字符串。
     * '+' 匹配单层路径。
     * '#' 匹配多层路径 (必须在主题末尾)。
     *
     * @param topicFilter MQTT主题过滤器
     * @return 对应的正则表达式字符串
     */
    private String topicToRegex(String topicFilter) {
        if (topicFilter == null) return "";
        String regex = topicFilter
                .replace("+", "[^/]+")       // 将 '+' 替换为匹配除 '/' 外任意字符一次或多次
                .replace("/#", "(/.*)?");   // 将 '/#' 替换为匹配 '/...' (可选的后续所有路径)
        // 如果 '#' 在主题中间或开头，或者单独存在，这个简单替换可能不完全准确，
        // 但对于 emqxConfig 中定义的 typical server-side subscriptions 来说是可用的。
        if (regex.endsWith("#") && !regex.endsWith("/#") && !regex.equals("#")) { // 处理单独的 '#' 或 '.../#' 结尾但前面没有斜杠的情况
           if (regex.equals("#")) regex = ".*"; // '#' 匹配所有
           else regex = regex.substring(0, regex.length() -1) + ".*"; // 'foo/#' -> 'foo/.*'
        }
        return "^" + regex + "$"; // 完全匹配
    }


    /**
     * 当一条消息成功传递给MQTT代理后调用 (对于QoS 1和2的消息)。
     *
     * @param token 包含消息上下文和传递状态的token。
     */
    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        try {
            if (token != null && token.getMessage() != null) {
                log.debug("MQTT消息传递成功。消息ID: {}, 主题: {}", token.getMessageId(), 
                          (token.getTopics() != null && token.getTopics().length > 0 ? token.getTopics()[0] : "N/A"));
            } else {
                log.debug("MQTT消息传递成功。Token: {}", token);
            }
        } catch (Exception e) {
            log.warn("处理消息传递成功回调时发生异常。", e);
        }
    }
} 