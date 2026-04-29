package com.erp.purchase;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 采购服务启动类
 * <p>
 * 职责：采购申请 → 采购订单 → 收货单 → 发票匹配 → 付款申请
 * 与 erp-inventory（入库）、erp-finance（应付）、erp-base（供应商/物料）协作
 */
@SpringBootApplication(scanBasePackages = {"com.erp.purchase", "com.erp.common"})
@EnableFeignClients(basePackages = "com.erp.purchase.infrastructure.feign")
@MapperScan("com.erp.purchase.infrastructure.mapper")
public class PurchaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(PurchaseApplication.class, args);
    }
}
