package com.erp.inventory.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存锁定记录
 * 记录每次锁定的来源和数量，用于解锁时核对
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_stock_lock")
public class StockLock extends BaseEntity {

    private String tenantId;
    private Long warehouseId;
    private Long materialId;
    private BigDecimal lockedQty;

    /**
     * 业务单号（幂等键，如销售订单号 SO20240101000001）
     */
    private String bizNo;

    /**
     * 业务类型：SALE_ORDER, PURCHASE_ORDER
     */
    private String bizType;

    /**
     * 锁定状态：LOCKED-锁定中，RELEASED-已释放，CONSUMED-已消费（发货）
     */
    private String lockStatus;

    private LocalDateTime lockTime;
    private LocalDateTime releaseTime;
}
