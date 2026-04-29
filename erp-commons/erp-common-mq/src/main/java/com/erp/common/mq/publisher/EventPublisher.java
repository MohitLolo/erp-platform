package com.erp.common.mq.publisher;

import com.erp.common.core.context.TenantContextHolder;
import com.erp.common.mq.event.BaseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 领域事件发布工具
 *
 * <p>统一处理事件发布前的上下文注入（tenantId / traceId），
 * 并通过 RabbitTemplate 发送到指定 Exchange。
 *
 * <p>使用示例：
 * <pre>
 *   OrderCreatedEvent event = new OrderCreatedEvent(order);
 *   eventPublisher.publish("erp.sale.exchange", "order.created", event);
 * </pre>
 *
 * @author erp
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布领域事件
     *
     * @param exchange   Exchange 名称
     * @param routingKey Routing Key
     * @param event      事件对象（继承 BaseEvent）
     */
    public void publish(String exchange, String routingKey, BaseEvent event) {
        // 注入上下文
        event.setTenantId(TenantContextHolder.getTenantId());
        event.setTraceId(MDC.get("traceId"));

        log.info("Publishing event: type={}, eventId={}, exchange={}, routingKey={}",
                event.getEventType(), event.getEventId(), exchange, routingKey);

        rabbitTemplate.convertAndSend(exchange, routingKey, event);
    }
}
