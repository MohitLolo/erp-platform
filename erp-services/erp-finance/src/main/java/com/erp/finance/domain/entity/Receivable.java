package com.erp.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应收款单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_receivable")
public class Receivable extends BaseEntity {

    private String tenantId;

    /**
     * 应收款单号
     */
    private String receivableNo;

    /**
     * 来源单号（销售订单号）
     */
    private String sourceNo;

    /**
     * 来源类型：SALE_ORDER, SERVICE_FEE
     */
    private String sourceType;

    private Long customerId;
    private String customerName;

    /**
     * 应收金额
     */
    private BigDecimal amount;

    /**
     * 已收金额
     */
    private BigDecimal receivedAmount;

    /**
     * 未收金额 = amount - receivedAmount
     */
    private BigDecimal unreceived;

    /**
     * 币种：CNY, USD, EUR
     */
    private String currency;

    /**
     * 到期日
     */
    private LocalDate dueDate;

    /**
     * 状态：PENDING-待收款，PARTIAL-部分收款，SETTLED-已结清，OVERDUE-逾期
     */
    private String status;

    private String remark;
}
