package com.erp.inventory.domain.entity;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存锁定请求 DTO
 * 由 InnerStockController 入参 或 Consumer 内部构造
 */
@Data
@Builder
public class StockLockRequest {

    /** 仓库ID */
    private Long warehouseId;

    /** 物料ID */
    private Long materialId;

    /** 锁定数量 */
    private BigDecimal quantity;

    /**
     * 业务单号（幂等键）
     * 格式建议：业务类型:主单号:行号/物料ID
     * 例：SALE:10001:1001
     */
    private String bizNo;

    /**
     * 业务类型
     * SALE_ORDER / PURCHASE_ORDER / WORK_ORDER / MANUAL
     */
    private String bizType;

    /** 租户ID（多租户隔离） */
    private String tenantId;
}
