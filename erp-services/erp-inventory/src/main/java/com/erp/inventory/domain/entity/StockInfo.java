package com.erp.inventory.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.erp.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 库存信息
 * 使用乐观锁（@Version）防止并发超卖
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_stock")
public class StockInfo extends BaseEntity {

    private String tenantId;

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 批次号（可选）
     */
    private String batchNo;

    /**
     * 可用库存（可下单）
     */
    private BigDecimal availableQty;

    /**
     * 锁定库存（已下单未发货）
     */
    private BigDecimal lockedQty;

    /**
     * 实际库存 = availableQty + lockedQty
     */
    private BigDecimal onHandQty;

    /**
     * 在途库存（采购在途）
     */
    private BigDecimal inTransitQty;

    /**
     * 乐观锁版本号（防并发超卖核心）
     */
    @Version
    private Integer version;

    /**
     * 安全库存预警线
     */
    private BigDecimal safetyStock;

    /**
     * 单位
     */
    private String unit;
}
