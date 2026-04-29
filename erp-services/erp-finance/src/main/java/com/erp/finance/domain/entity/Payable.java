package com.erp.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应付款单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_payable")
public class Payable extends BaseEntity {

    private String tenantId;
    private String payableNo;
    private String sourceNo;

    /**
     * 来源类型：PURCHASE_ORDER
     */
    private String sourceType;

    private Long supplierId;
    private String supplierName;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    private BigDecimal unpaid;
    private String currency;
    private LocalDate dueDate;

    /**
     * 状态：PENDING-待付款，PARTIAL-部分付款，SETTLED-已结清
     */
    private String status;
    private String remark;
}
