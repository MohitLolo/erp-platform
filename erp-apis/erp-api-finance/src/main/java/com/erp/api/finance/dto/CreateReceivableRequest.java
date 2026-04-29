package com.erp.api.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 创建应收账款请求 DTO
 *
 * @author erp
 * @since 1.0.0
 */
@Data
public class CreateReceivableRequest {

    /**
     * 关联业务单号（如销售订单 ID）
     */
    private Long sourceOrderId;

    /**
     * 业务类型（SALE_ORDER / RETURN_ORDER 等）
     */
    private String businessType;

    /**
     * 客户 ID
     */
    private Long customerId;

    /**
     * 应收金额
     */
    private BigDecimal amount;

    /**
     * 应收账款到期日
     */
    private LocalDate dueDate;

    /**
     * 币种（默认 CNY）
     */
    private String currency = "CNY";
}
