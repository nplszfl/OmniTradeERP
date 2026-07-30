package com.crossborder.erp.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger配置
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("跨境电商ERP - 订单服务API")
                        .description("订单管理相关接口文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("nplszfl")
                                .email("support@example.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://choosealicense.com/licenses/mit")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("本地开发环境"),
                        new Server().url("http://test-api.example.com").description("测试环境"),
                        new Server().url("https://api.example.com").description("生产环境")
                ));
    }
}
