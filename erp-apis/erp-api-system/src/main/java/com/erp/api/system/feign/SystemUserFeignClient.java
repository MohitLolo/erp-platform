package com.erp.api.system.feign;

import com.erp.api.system.dto.UserInfoDTO;
import com.erp.common.core.response.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 系统服务 Feign 客户端
 *
 * <p>提供用户信息查询接口，供其他服务获取当前登录用户的详情。
 *
 * @author erp
 * @since 1.0.0
 */
@FeignClient(
        name = "erp-system",
        url = "${feign.system.url:http://erp-system.erp-prod.svc.cluster.local:8080}"
)
public interface SystemUserFeignClient {

    /**
     * 根据用户 ID 获取用户信息
     *
     * @param userId 用户 ID
     * @return 用户信息
     */
    @GetMapping("/system/inner/user/{userId}")
    R<UserInfoDTO> getUserById(@PathVariable("userId") Long userId);
}
