package com.erp.production.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 生产工单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pro_work_order")
public class WorkOrder extends BaseEntity {

    private String tenantId;
    private String workOrderNo;

    /**
     * 产品物料ID
     */
    private Long productMaterialId;
    private String productMaterialCode;
    private String productMaterialName;
    private Long bomId;

    /**
     * 计划产量
     */
    private BigDecimal planQty;

    /**
     * 实际完成量
     */
    private BigDecimal completedQty;

    /**
     * 报废量
     */
    private BigDecimal scrapQty;

    private LocalDate planStartDate;
    private LocalDate planEndDate;
    private LocalDate actualStartDate;
    private LocalDate actualEndDate;

    /**
     * 工单来源：SALE_ORDER-销售订单，MRP-MRP计划，MANUAL-手工创建
     */
    private String sourceType;
    private String sourceNo;

    /**
     * 状态：DRAFT-草稿, RELEASED-已下达, IN_PROGRESS-生产中,
     * COMPLETED-已完工, CLOSED-已关闭, CANCELLED-已取消
     */
    private String status;
    private String remark;
}
