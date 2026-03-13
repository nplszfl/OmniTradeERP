package com.crossborder.erp.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 订单服务启动类
 */
@SpringBootApplication
@EnableFeignClients
public class ErpOrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ErpOrderServiceApplication.class, args);
    }
}
