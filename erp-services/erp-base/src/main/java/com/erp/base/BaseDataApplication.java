package com.erp.base;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 基础数据服务启动类
 * <p>
 * 职责：客户档案、供应商档案、物料主数据、仓库管理、计量单位
 * 是其他业务服务的数据来源，被 sale/purchase/inventory/production 通过 Feign 查询
 */
@SpringBootApplication(scanBasePackages = {"com.erp.base", "com.erp.common"})
@EnableFeignClients(basePackages = "com.erp.base.infrastructure.feign")
@MapperScan("com.erp.base.infrastructure.mapper")
public class BaseDataApplication {

    public static void main(String[] args) {
        SpringApplication.run(BaseDataApplication.class, args);
    }
}
