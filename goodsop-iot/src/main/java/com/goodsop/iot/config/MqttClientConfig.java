package com.goodsop.iot.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQTT客户端连接选项配置类
 * <p>
 * 负责配置连接到EMQX代理所需的参数，例如服务器URI、用户名、密码、
 * 清理会话、自动重连、连接超时和心跳间隔等。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MqttClientConfig {

    private final EmqxConfig emqxConfig; // 通过构造函数注入EMQX的配置信息

    /**
     * 配置并返回MQTT连接选项 {@link MqttConnectOptions}。
     *
     * @return 配置好的MqttConnectOptions实例
     */
    @Bean
    public MqttConnectOptions mqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();

        // 设置MQTT服务器的URI，例如 tcp://localhost:1883
        options.setServerURIs(new String[]{emqxConfig.getHost()});
        log.info("MQTT连接选项 - 服务器URI: {}", emqxConfig.getHost());

        // 设置连接用户名
        if (emqxConfig.getUsername() != null && !emqxConfig.getUsername().isEmpty()) {
            options.setUserName(emqxConfig.getUsername());
            log.info("MQTT连接选项 - 用户名: {}", emqxConfig.getUsername());
        }

        // 设置连接密码
        if (emqxConfig.getPassword() != null && !emqxConfig.getPassword().isEmpty()) {
            options.setPassword(emqxConfig.getPassword().toCharArray());
            log.info("MQTT连接选项 - 密码已设置 (出于安全考虑，不打印密码内容)");
        }

        // 设置是否清除会话状态。true表示建立一个全新的会话，false表示恢复之前的会话（如果存在）。
        // 清理会话为true时，客户端断开连接后，代理将清除所有与该客户端相关的订阅信息和未确认的消息。
        options.setCleanSession(true); // 通常对于服务端应用，如果不需要持久会话，建议设置为true
        log.info("MQTT连接选项 - 清理会话 (CleanSession): true");

        // 设置连接超时时间（秒）。如果在指定时间内未能建立连接，则连接失败。
        options.setConnectionTimeout(emqxConfig.getConnectionTimeout());
        log.info("MQTT连接选项 - 连接超时时间 (秒): {}", emqxConfig.getConnectionTimeout());

        // 设置心跳间隔时间（秒）。客户端会定期向代理发送心跳包以保持连接活跃。
        // 0表示禁用客户端心跳。
        options.setKeepAliveInterval(emqxConfig.getKeepAliveInterval());
        log.info("MQTT连接选项 - 心跳间隔时间 (秒): {}", emqxConfig.getKeepAliveInterval());

        // 设置是否自动重连。如果连接丢失，客户端将尝试自动重新连接。
        options.setAutomaticReconnect(true);
        log.info("MQTT连接选项 - 自动重连: true");

        // 设置MQTT版本。默认为3.1.1。如果EMQX支持MQTT 5，可以考虑设置为 MqttConnectOptions.MQTT_VERSION_5。
        // options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_5);
        // log.info("MQTT连接选项 - MQTT版本: (默认或指定版本)");

        // 可选：设置"遗嘱"消息 (Will Message)，当客户端异常断开时，代理会发布此消息。
        // options.setWill("your/will/topic", "client connection lost".getBytes(), 0, false);

        log.info("MQTT连接选项配置完成。");
        return options;
    }
} 