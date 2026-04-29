package com.erp.auth.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 登录响应DTO
 */
@Data
@Builder
public class LoginResponse {

    /**
     * Sa-Token JWT token
     */
    private String token;

    /**
     * token类型
     */
    private String tokenType;

    /**
     * 过期时间（秒）
     */
    private long expiresIn;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 租户名称
     */
    private String tenantName;
}
