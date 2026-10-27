package com.crossborder.erp.finance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 财务服务启动类
 *
 * <p>负责财务领域：记账流水 / 账户 / 分类 / 报表统计 / 趋势 / 对账。</p>
 *
 * @author 火球鼠
 * @since 2026-10-28
 */
@SpringBootApplication
@EnableFeignClients
public class FinanceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceServiceApplication.class, args);
    }
}
