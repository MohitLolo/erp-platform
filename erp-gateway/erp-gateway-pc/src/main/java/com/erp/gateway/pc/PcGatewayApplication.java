package com.erp.gateway.pc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PC 端网关启动类
 *
 * <p>scanBasePackages 同时扫描公共库包，使 {@code erp-gateway-common} 中的
 * {@link com.erp.gateway.common.filter.AuthGlobalFilter}、
 * {@link com.erp.gateway.common.filter.GrayRoutingFilter} 等
 * {@code @Component} 能被自动注册。
 */
@SpringBootApplication(scanBasePackages = {
        "com.erp.gateway.common",
        "com.erp.gateway.pc"
})
public class PcGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(PcGatewayApplication.class, args);
    }
}
