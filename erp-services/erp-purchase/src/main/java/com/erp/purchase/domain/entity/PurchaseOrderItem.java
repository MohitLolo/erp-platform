package com.erp.purchase.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 采购订单明细
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pur_order_item")
public class PurchaseOrderItem extends BaseEntity {

    private Long orderId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String spec;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal taxRate;
    private BigDecimal amount;
    private Long warehouseId;
    private BigDecimal receivedQty;
    private BigDecimal invoicedQty;
}
