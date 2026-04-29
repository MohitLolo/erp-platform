package com.erp.inventory.application.consumer;

import com.erp.inventory.application.service.StockService;
import com.erp.inventory.domain.entity.StockLockRequest;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 销售订单创建事件消费者
 * <p>
 * 监听 erp.sale.exchange → erp.sale.order.created 路由键的消息
 * 收到事件后，执行库存预占（也可改为 Seata @GlobalTransactional 同步调用，此处演示异步方案）
 * <p>
 * 手动 ACK 策略：
 * - 业务处理成功 → basicAck
 * - 业务异常（幂等重复等）→ basicReject(false)  丢弃，不重入队列
 * - 系统异常（DB挂等）→ basicNack(false, false) 消息进 DLQ 等待人工处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaleOrderCreatedConsumer {

    private static final String QUEUE_NAME = "erp.inventory.queue.sale.order.created";

    private final StockService stockService;

    @RabbitListener(queues = QUEUE_NAME, ackMode = "MANUAL", concurrency = "2-5")
    public void onOrderCreated(OrderCreatedEventMessage event, Message message, Channel channel)
            throws IOException {

        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String eventId = event.getEventId();

        log.info("[库存消费] 收到销售订单创建事件, eventId={}, orderId={}", eventId, event.getOrderId());

        try {
            // 转换请求，逐行锁定库存
            List<StockLockRequest> lockRequests = event.getItems().stream()
                    .map(item -> StockLockRequest.builder()
                            .warehouseId(item.getWarehouseId())
                            .materialId(item.getMaterialId())
                            .quantity(item.getQuantity())
                            // bizNo = "SALE:" + orderId + ":" + materialId，保证幂等
                            .bizNo("SALE:" + event.getOrderId() + ":" + item.getMaterialId())
                            .bizType("SALE_ORDER")
                            .tenantId(event.getTenantId())
                            .build())
                    .collect(Collectors.toList());

            stockService.batchLockStock(lockRequests);

            // 业务成功，确认消息
            channel.basicAck(deliveryTag, false);
            log.info("[库存消费] 库存预占成功, eventId={}, orderId={}", eventId, event.getOrderId());

        } catch (com.erp.common.exception.BizException e) {
            // 业务异常：幂等重复 / 库存不足（异步方案需要补偿）
            log.warn("[库存消费] 业务异常，丢弃消息: eventId={}, error={}", eventId, e.getMessage());
            // false = 不重新入队；业务层已处理幂等或需要业务补偿
            channel.basicReject(deliveryTag, false);

        } catch (Exception e) {
            // 系统异常：网络抖动/DB临时不可用，消息投递到 DLQ 等待处理
            log.error("[库存消费] 系统异常，转入死信队列: eventId={}", eventId, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    // -----------------------------------------------------------------------
    // 内部消息结构（Jackson 反序列化，与 erp-sale 的 OrderCreatedEvent 对应）
    // -----------------------------------------------------------------------

    @lombok.Data
    public static class OrderCreatedEventMessage {
        private String eventId;
        private String occurredAt;
        private String tenantId;
        private Long orderId;
        private String orderNo;
        private Long customerId;
        private String customerName;
        private java.math.BigDecimal totalAmount;
        private List<Item> items;

        @lombok.Data
        public static class Item {
            private Long materialId;
            private String materialCode;
            private Long warehouseId;
            private java.math.BigDecimal quantity;
        }
    }
}
