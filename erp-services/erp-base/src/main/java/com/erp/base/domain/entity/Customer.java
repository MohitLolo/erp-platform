package com.erp.base.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户档案
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bas_customer")
public class Customer extends BaseEntity {

    private String tenantId;

    /**
     * 客户编码（租户内唯一）
     */
    private String customerCode;

    private String customerName;
    private String shortName;

    /**
     * 客户类型：ENTERPRISE-企业，INDIVIDUAL-个人
     */
    private String customerType;

    private String creditLevel;
    private String contactPerson;
    private String contactPhone;
    private String email;
    private String address;
    private String taxNo;

    /**
     * 信用额度
     */
    private java.math.BigDecimal creditLimit;

    /**
     * 账期（天）
     */
    private Integer paymentTermDays;

    /**
     * 状态：1-启用，0-禁用
     */
    private Integer status;
    private String remark;
}
