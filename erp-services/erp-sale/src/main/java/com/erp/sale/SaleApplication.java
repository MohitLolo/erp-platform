package com.erp.sale;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
@MapperScan("com.erp.sale.infrastructure.mapper")
public class SaleApplication {
    public static void main(String[] args) {
        SpringApplication.run(SaleApplication.class, args);
    }
}
