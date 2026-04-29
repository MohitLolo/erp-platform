package com.erp.sale.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 销售订单明细
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sale_order_item")
public class SaleOrderItem extends BaseEntity {

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料编码（冗余）
     */
    private String materialCode;

    /**
     * 物料名称（冗余）
     */
    private String materialName;

    /**
     * 规格型号
     */
    private String spec;

    /**
     * 单位
     */
    private String unit;

    /**
     * 数量
     */
    private BigDecimal quantity;

    /**
     * 单价（不含税）
     */
    private BigDecimal unitPrice;

    /**
     * 税率（如 0.13 表示13%）
     */
    private BigDecimal taxRate;

    /**
     * 含税金额
     */
    private BigDecimal amount;

    /**
     * 仓库ID（出货仓库）
     */
    private Long warehouseId;

    /**
     * 已发货数量
     */
    private BigDecimal deliveredQty;
}
