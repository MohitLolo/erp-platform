package com.erp.api.system.dto;

import lombok.Data;

import java.util.List;

/**
 * 用户信息 DTO（供其他服务通过 Feign 查询）
 *
 * @author erp
 * @since 1.0.0
 */
@Data
public class UserInfoDTO {

    /** 用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String mobile;

    /** 租户 ID */
    private String tenantId;

    /** 所属部门 ID */
    private Long deptId;

    /** 角色码列表 */
    private List<String> roles;

    /** 权限码列表 */
    private List<String> permissions;

    /** 账号状态（0=正常，1=禁用） */
    private Integer status;
}
