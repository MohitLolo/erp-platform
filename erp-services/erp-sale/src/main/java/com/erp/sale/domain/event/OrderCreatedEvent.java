package com.erp.sale.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 销售订单创建事件
 * 发布到RabbitMQ，供下游服务（财务、仓库）异步消费
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {

    /**
     * 事件ID（幂等键）
     */
    private String eventId;

    /**
     * 事件发生时间
     */
    private LocalDateTime occurredAt;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 订单编号
     */
    private String orderNo;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 客户名称
     */
    private String customerName;

    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;

    /**
     * 订单明细（库存预留需要）
     */
    private List<OrderItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem {
        private Long materialId;
        private String materialCode;
        private BigDecimal quantity;
        private Long warehouseId;
        private BigDecimal unitPrice;
    }
}
