package com.erp.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户信息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant")
public class SysTenant extends BaseEntity {

    /**
     * 租户唯一编码（登录时填写）
     */
    private String tenantCode;

    /**
     * 租户名称
     */
    private String tenantName;

    /**
     * Schema名称（数据库隔离：erp_tenant_001）
     */
    private String schemaName;

    /**
     * 联系人
     */
    private String contactPerson;

    /**
     * 联系电话
     */
    private String contactPhone;

    /**
     * 联系邮箱
     */
    private String contactEmail;

    /**
     * 过期时间（为null表示永不过期）
     */
    private java.time.LocalDateTime expireTime;

    /**
     * 状态：1-正常，0-禁用
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;
}
