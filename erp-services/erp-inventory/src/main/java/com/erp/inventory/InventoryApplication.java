package com.erp.inventory;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 库存服务启动类
 * <p>
 * 职责：库存台账管理、库存锁定/释放、出入库记录、库存盘点
 * 对外暴露 /inventory/inner/** 接口供 erp-sale、erp-purchase、erp-production 调用
 */
@SpringBootApplication(scanBasePackages = {"com.erp.inventory", "com.erp.common"})
@EnableFeignClients(basePackages = "com.erp.inventory.infrastructure.feign")
@MapperScan("com.erp.inventory.infrastructure.mapper")
public class InventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }
}
