package com.erp.sale.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 销售订单主表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sale_order")
public class SaleOrder extends BaseEntity {

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 订单编号（业务唯一键）
     */
    private String orderNo;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 客户名称（冗余，避免跨服务查询）
     */
    private String customerName;

    /**
     * 销售员ID
     */
    private Long salespersonId;

    /**
     * 订单日期
     */
    private LocalDate orderDate;

    /**
     * 要求交货日期
     */
    private LocalDate deliveryDate;

    /**
     * 订单金额（含税）
     */
    private BigDecimal totalAmount;

    /**
     * 税额
     */
    private BigDecimal taxAmount;

    /**
     * 订单状态：
     * DRAFT-草稿, CONFIRMED-已确认, IN_DELIVERY-发货中,
     * DELIVERED-已发货, INVOICED-已开票, CLOSED-已关闭, CANCELLED-已取消
     */
    private String status;

    /**
     * 收货地址
     */
    private String deliveryAddress;

    /**
     * 备注
     */
    private String remark;

    /**
     * 订单明细（非持久化，业务层组装）
     */
    @TableField(exist = false)
    private List<SaleOrderItem> items;
}
