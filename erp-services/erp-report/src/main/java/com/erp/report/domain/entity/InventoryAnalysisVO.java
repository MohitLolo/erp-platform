package com.erp.report.domain.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存分析报表VO（查询Doris）
 */
@Data
public class InventoryAnalysisVO {

    private String tenantId;
    private String warehouseName;
    private String materialName;
    private String materialType;
    private BigDecimal onHandQty;
    private BigDecimal lockedQty;
    private BigDecimal availableQty;

    /**
     * 库存金额（onHandQty * standardCost）
     */
    private BigDecimal inventoryValue;

    /**
     * 库龄（天）
     */
    private Integer agingDays;
}
