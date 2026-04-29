package com.erp.sale.infrastructure.mq;

import com.erp.sale.domain.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 销售事件发布器
 * 负责将订单相关事件发布到RabbitMQ
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaleEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public static final String SALE_EXCHANGE = "erp.sale.exchange";
    public static final String ORDER_CREATED_ROUTING_KEY = "sale.order.created";

    /**
     * 发布订单创建事件
     * 注意：此方法在Seata事务提交后调用（非事务内），避免事务回滚时消息已发出
     */
    public void publishOrderCreated(OrderCreatedEvent event) {
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID().toString());
        }
        try {
            rabbitTemplate.convertAndSend(SALE_EXCHANGE, ORDER_CREATED_ROUTING_KEY, event);
            log.info("Published OrderCreatedEvent: orderId={}, eventId={}", event.getOrderId(), event.getEventId());
        } catch (Exception e) {
            // 消息发送失败记录日志，依赖补偿机制（定时任务重推）
            log.error("Failed to publish OrderCreatedEvent: orderId={}", event.getOrderId(), e);
        }
    }
}
