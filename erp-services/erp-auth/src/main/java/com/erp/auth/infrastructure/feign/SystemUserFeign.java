package com.erp.auth.infrastructure.feign;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * erp-system 用户验证Feign客户端
 */
@FeignClient(
        name = "erp-system",
        url = "${feign.system.url:http://erp-system:8080}",
        configuration = com.erp.common.web.feign.TtlFeignRequestInterceptor.class
)
public interface SystemUserFeign {

    /**
     * 验证用户凭证（tenantId + username + passwordMd5）
     * 返回用户基本信息，验证失败返回null
     */
    @GetMapping("/system/inner/user/verify")
    @CircuitBreaker(name = "systemService", fallbackMethod = "verifyUserFallback")
    UserInfo verifyUser(
            @RequestParam("tenantId") String tenantId,
            @RequestParam("username") String username,
            @RequestParam("password") String passwordMd5
    );

    default UserInfo verifyUserFallback(String tenantId, String username, String passwordMd5, Exception e) {
        throw new RuntimeException("系统服务暂时不可用，请稍后重试", e);
    }

    /**
     * 用户信息VO（内部接口专用）
     */
    @Data
    class UserInfo {
        private Long userId;
        private String username;
        private String tenantId;
        private String tenantName;
        private boolean enabled;
    }
}
