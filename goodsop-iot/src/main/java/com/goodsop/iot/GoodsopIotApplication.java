package com.goodsop.iot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class GoodsopIotApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoodsopIotApplication.class, args);
    }
} 