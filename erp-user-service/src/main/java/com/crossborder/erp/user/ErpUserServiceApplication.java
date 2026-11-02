package com.crossborder.erp.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class ErpUserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ErpUserServiceApplication.class, args);
    }
}
