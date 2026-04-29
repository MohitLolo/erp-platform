package com.erp.production;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 生产服务启动类
 * <p>
 * 职责：BOM管理、工单下达、生产报工、生产领料、完工入库
 * 与 erp-inventory（领料/入库）、erp-base（物料/BOM）协作
 */
@SpringBootApplication(scanBasePackages = {"com.erp.production", "com.erp.common"})
@EnableFeignClients(basePackages = "com.erp.production.infrastructure.feign")
@MapperScan("com.erp.production.infrastructure.mapper")
public class ProductionApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductionApplication.class, args);
    }
}
