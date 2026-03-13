package com.crossborder.erp.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 平台服务启动类
 */
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class ErpPlatformServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ErpPlatformServiceApplication.class, args);
    }
}
