package com.erp.gateway.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 移动端网关启动类（iOS / Android App）
 *
 * <p>scanBasePackages 同时扫描公共库包，使 {@code erp-gateway-common} 中的过滤器
 * 能被自动注册。
 */
@SpringBootApplication(scanBasePackages = {
        "com.erp.gateway.common",
        "com.erp.gateway.app"
})
public class AppGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppGatewayApplication.class, args);
    }
}
