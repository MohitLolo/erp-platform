package com.erp.base.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 供应商档案
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bas_supplier")
public class Supplier extends BaseEntity {

    private String tenantId;
    private String supplierCode;
    private String supplierName;
    private String shortName;

    /**
     * 供应商类别：RAW_MATERIAL-原材料，AUXILIARY-辅材，SERVICE-服务
     */
    private String supplierType;

    private String contactPerson;
    private String contactPhone;
    private String email;
    private String address;
    private String taxNo;
    private String bankName;
    private String bankAccount;

    /**
     * 账期（天）
     */
    private Integer paymentTermDays;

    private Integer status;
    private String remark;
}
