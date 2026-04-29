package com.erp.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户角色关联（多对多）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user_role")
public class SysUserRole {
    private Long userId;
    private Long roleId;
}
