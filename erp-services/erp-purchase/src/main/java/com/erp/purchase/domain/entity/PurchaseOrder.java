package com.erp.purchase.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 采购订单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pur_order")
public class PurchaseOrder extends BaseEntity {

    private String tenantId;
    private String orderNo;
    private Long supplierId;
    private String supplierName;
    private Long purchaserId;
    private LocalDate orderDate;
    private LocalDate expectedDate;
    private BigDecimal totalAmount;
    private BigDecimal taxAmount;

    /**
     * 状态：DRAFT-草稿, CONFIRMED-已确认, IN_RECEIPT-收货中,
     * RECEIPTED-已收货, INVOICED-已开票, CLOSED-已关闭, CANCELLED-已取消
     */
    private String status;

    private String deliveryAddress;
    private String remark;

    @TableField(exist = false)
    private List<PurchaseOrderItem> items;
}
