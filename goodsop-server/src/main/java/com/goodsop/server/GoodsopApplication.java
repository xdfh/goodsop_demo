package com.goodsop.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 应用程序启动类
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.goodsop"})
@MapperScan(basePackages = {"com.goodsop.**.mapper"})
public class GoodsopApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoodsopApplication.class, args);
    }
} 