package com.crossborder.logistics;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 物流服务
 *
 * <p>⚠️ 这个模块之前根本不存在 —— 前端 erp-web/src/views/logistics/Logistics.vue
 * 有完整页面（渠道列表、运费计算、渠道推荐、费率管理），但后端一个接口都没有，
 * 页面所有数据都来自前端 mock。现在补齐。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@SpringBootApplication
@MapperScan("com.crossborder.logistics.mapper")
public class LogisticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogisticsServiceApplication.class, args);
    }
}
