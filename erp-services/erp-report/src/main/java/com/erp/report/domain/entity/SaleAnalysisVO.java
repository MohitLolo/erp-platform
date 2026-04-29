package com.erp.report.domain.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售分析报表VO（查询Doris，不映射MySQL表）
 */
@Data
public class SaleAnalysisVO {

    private String tenantId;
    private String yearMonth;
    private String customerName;
    private String materialName;
    private BigDecimal totalQty;
    private BigDecimal totalAmount;
    private BigDecimal avgUnitPrice;

    /**
     * 环比增长率
     */
    private BigDecimal momGrowthRate;
}
