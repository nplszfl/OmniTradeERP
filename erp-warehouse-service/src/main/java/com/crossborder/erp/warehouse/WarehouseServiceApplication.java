package com.crossborder.erp.warehouse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 仓库服务启动类
 *
 * <p>负责 WMS 领域：仓库 / 库位 / 入库 / 出库 / 调拨 / 库存批次 / 波次拣货 / 包装材料。</p>
 *
 * @author 火球鼠
 * @since 2026-10-28
 */
@SpringBootApplication
@EnableFeignClients
public class WarehouseServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WarehouseServiceApplication.class, args);
    }
}
