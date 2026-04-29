package com.erp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求DTO
 */
@Data
public class LoginRequest {

    /**
     * 租户ID（多租户场景必须）
     */
    @NotBlank(message = "租户ID不能为空")
    private String tenantId;

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码（MD5加密后传输）
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 验证码（可选）
     */
    private String captcha;

    /**
     * 验证码Key（可选）
     */
    private String captchaKey;
}
