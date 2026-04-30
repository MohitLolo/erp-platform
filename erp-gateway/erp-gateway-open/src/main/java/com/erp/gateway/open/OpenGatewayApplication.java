package com.erp.gateway.open;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 开放平台网关启动类（第三方合作伙伴 API）
 *
 * <p>面向外部开发者，可按需叠加 API Key 验证、签名校验、流量控制等过滤器。
 * 扫描公共库包以复用共享过滤器与配置。
 */
@SpringBootApplication(scanBasePackages = {
        "com.erp.gateway.common",
        "com.erp.gateway.open"
})
public class OpenGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenGatewayApplication.class, args);
    }
}
