package com.erp.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统角色
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    private String tenantId;

    /**
     * 角色编码（全局唯一，如 ADMIN, PURCHASE_MANAGER）
     */
    private String roleCode;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 数据权限范围：
     * 1-全部数据，2-本部门数据，3-本部门及以下，4-仅本人数据
     */
    private Integer dataScope;

    /**
     * 状态：1-启用，0-禁用
     */
    private Integer status;

    private String remark;
}
