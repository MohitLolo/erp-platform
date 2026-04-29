package com.erp.finance;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 财务服务启动类
 * <p>
 * 职责：应收管理（AR）、应付管理（AP）、总账（GL）、成本核算
 * 对外暴露 /finance/inner/** 供 erp-sale、erp-purchase 创建应收/应付凭证
 */
@SpringBootApplication(scanBasePackages = {"com.erp.finance", "com.erp.common"})
@EnableFeignClients(basePackages = "com.erp.finance.infrastructure.feign")
@MapperScan("com.erp.finance.infrastructure.mapper")
public class FinanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceApplication.class, args);
    }
}
