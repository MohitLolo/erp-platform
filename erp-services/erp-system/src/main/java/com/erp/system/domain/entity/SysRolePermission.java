package com.erp.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色权限关联（多对多）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_role_permission")
public class SysRolePermission {
    private Long roleId;
    private Long permissionId;
}
