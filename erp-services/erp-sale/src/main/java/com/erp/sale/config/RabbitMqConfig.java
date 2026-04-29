package com.erp.sale.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置
 * - 使用JSON序列化（Jackson2JsonMessageConverter）
 * - 配置死信队列（DLQ）处理失败消息
 */
@Configuration
public class RabbitMqConfig {

    public static final String SALE_EXCHANGE = "erp.sale.exchange";
    public static final String SALE_DLX = "erp.sale.dlx";

    // 销售队列
    public static final String ORDER_CREATED_QUEUE = "sale.order.created.queue";
    public static final String ORDER_CREATED_DLQ = "sale.order.created.dlq";
    public static final String ORDER_CREATED_ROUTING_KEY = "sale.order.created";

    /**
     * 主交换机（Topic类型，支持通配符路由）
     */
    @Bean
    public TopicExchange saleExchange() {
        return ExchangeBuilder.topicExchange(SALE_EXCHANGE).durable(true).build();
    }

    /**
     * 死信交换机
     */
    @Bean
    public DirectExchange saleDlx() {
        return ExchangeBuilder.directExchange(SALE_DLX).durable(true).build();
    }

    /**
     * 订单创建队列（配置死信队列）
     */
    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(ORDER_CREATED_QUEUE)
                .withArgument("x-dead-letter-exchange", SALE_DLX)
                .withArgument("x-dead-letter-routing-key", ORDER_CREATED_DLQ)
                .withArgument("x-message-ttl", 30000) // 30秒TTL
                .build();
    }

    /**
     * 订单创建死信队列
     */
    @Bean
    public Queue orderCreatedDlq() {
        return QueueBuilder.durable(ORDER_CREATED_DLQ).build();
    }

    @Bean
    public Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange saleExchange) {
        return BindingBuilder.bind(orderCreatedQueue)
                .to(saleExchange)
                .with(ORDER_CREATED_ROUTING_KEY);
    }

    @Bean
    public Binding orderCreatedDlqBinding(Queue orderCreatedDlq, DirectExchange saleDlx) {
        return BindingBuilder.bind(orderCreatedDlq)
                .to(saleDlx)
                .with(ORDER_CREATED_DLQ);
    }

    /**
     * JSON消息转换器
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate配置JSON序列化
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        return template;
    }

    /**
     * 监听容器工厂（手动ACK）
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        return factory;
    }
}
