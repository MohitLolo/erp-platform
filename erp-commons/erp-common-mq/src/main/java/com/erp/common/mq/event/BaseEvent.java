package com.erp.common.mq.event;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 所有领域事件的基类
 *
 * <p>事件命名规范：{服务}.{聚合根}.{动词过去式}，例如：
 * <ul>
 *   <li>sale.order.created</li>
 *   <li>inventory.stock.locked</li>
 *   <li>finance.receivable.created</li>
 * </ul>
 *
 * @author erp
 * @since 1.0.0
 */
@Data
public abstract class BaseEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件唯一 ID（UUID）
     */
    private String eventId;

    /**
     * 事件类型（子类可覆盖）
     */
    private String eventType;

    /**
     * 发生时间
     */
    private LocalDateTime occurredAt;

    /**
     * 来源服务名
     */
    private String sourceService;

    /**
     * 租户 ID（用于多租户场景下的消费过滤）
     */
    private String tenantId;

    /**
     * 链路追踪 ID（透传 SkyWalking traceId）
     */
    private String traceId;

    protected BaseEvent() {
        this.eventId = UUID.randomUUID().toString().replace("-", "");
        this.occurredAt = LocalDateTime.now();
        this.eventType = this.getClass().getSimpleName();
    }
}
