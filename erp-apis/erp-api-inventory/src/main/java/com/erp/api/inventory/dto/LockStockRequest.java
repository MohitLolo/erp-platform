package com.erp.api.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 锁库/解锁库存请求 DTO
 *
 * @author erp
 * @since 1.0.0
 */
@Data
public class LockStockRequest {

    /**
     * 订单 ID（用于幂等控制）
     */
    private Long orderId;

    /**
     * 商品 SKU ID
     */
    private Long skuId;

    /**
     * 仓库 ID
     */
    private Long warehouseId;

    /**
     * 锁定数量
     */
    private BigDecimal quantity;
}
