package com.erp.inventory.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * erp-inventory RabbitMQ 配置
 * <p>
 * 声明用于消费 erp-sale 事件的队列和绑定关系
 * erp-sale 负责声明 Exchange；inventory 只需声明自己的 Queue 并绑定
 */
@Configuration
public class InventoryRabbitMqConfig {

    // =====================================================================
    // 来自 erp-sale 的 Exchange（由 sale 服务声明，此处仅 Reference）
    // =====================================================================
    public static final String SALE_EXCHANGE = "erp.sale.exchange";
    public static final String SALE_ORDER_CREATED_ROUTING_KEY = "erp.sale.order.created";

    // =====================================================================
    // inventory 消费队列
    // =====================================================================
    public static final String INVENTORY_SALE_ORDER_QUEUE = "erp.inventory.queue.sale.order.created";

    // =====================================================================
    // 死信配置
    // =====================================================================
    public static final String INVENTORY_DLX = "erp.inventory.dlx";
    public static final String INVENTORY_DL_QUEUE = "erp.inventory.dlq.sale.order.created";

    /**
     * 死信 Exchange（Direct 类型）
     */
    @Bean
    public DirectExchange inventoryDeadLetterExchange() {
        return new DirectExchange(INVENTORY_DLX, true, false);
    }

    /**
     * 死信队列（人工处理或定时重试）
     */
    @Bean
    public Queue inventoryDeadLetterQueue() {
        return QueueBuilder.durable(INVENTORY_DL_QUEUE).build();
    }

    @Bean
    public Binding inventoryDlqBinding() {
        return BindingBuilder.bind(inventoryDeadLetterQueue())
                .to(inventoryDeadLetterExchange())
                .with(INVENTORY_DL_QUEUE);
    }

    /**
     * 正常消费队列：绑定到 sale exchange，监听订单创建事件
     * 配置死信转发（处理失败消息）
     */
    @Bean
    public Queue inventorySaleOrderQueue() {
        return QueueBuilder.durable(INVENTORY_SALE_ORDER_QUEUE)
                .withArgument("x-dead-letter-exchange", INVENTORY_DLX)
                .withArgument("x-dead-letter-routing-key", INVENTORY_DL_QUEUE)
                .withArgument("x-message-ttl", 86400000)  // 24h TTL
                .build();
    }

    /**
     * 绑定：SALE_EXCHANGE → INVENTORY_SALE_ORDER_QUEUE（以 routing key 过滤）
     * 注意：TopicExchange 需要先声明（sale 服务声明），此处只做绑定
     */
    @Bean
    public TopicExchange saleExchangeRef() {
        // passive=true：Exchange 已存在则直接引用，不存在不报错
        return ExchangeBuilder.topicExchange(SALE_EXCHANGE).durable(true).build();
    }

    @Bean
    public Binding inventorySaleOrderBinding() {
        return BindingBuilder.bind(inventorySaleOrderQueue())
                .to(saleExchangeRef())
                .with(SALE_ORDER_CREATED_ROUTING_KEY);
    }

    // =====================================================================
    // 公共配置
    // =====================================================================

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    /**
     * 手动 ACK 监听容器工厂
     * 在 @RabbitListener 上指定 ackMode = "MANUAL" 后，此工厂生效
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(1);
        return factory;
    }
}
