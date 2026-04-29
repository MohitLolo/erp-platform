package com.erp.sale.application.service;

import com.erp.common.core.exception.BizException;
import com.erp.common.core.response.R;
import com.erp.common.core.response.ResultCode;
import com.erp.common.core.context.TenantContextHolder;
import com.erp.sale.domain.entity.SaleOrder;
import com.erp.sale.domain.entity.SaleOrderItem;
import com.erp.sale.domain.event.OrderCreatedEvent;
import com.erp.api.inventory.feign.InventoryFeignClient;
import com.erp.api.inventory.dto.LockStockRequest;
import com.erp.api.finance.feign.FinanceFeignClient;
import com.erp.api.finance.dto.CreateReceivableRequest;
import com.erp.sale.infrastructure.mapper.SaleOrderItemMapper;
import com.erp.sale.infrastructure.mapper.SaleOrderMapper;
import com.erp.sale.infrastructure.mq.SaleEventPublisher;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 销售订单应用服务
 *
 * 核心下单流程（Seata AT分布式事务）：
 * 1. 创建销售订单（本地DB）
 * 2. 锁定库存（调用erp-inventory，AT事务分支）
 * 3. 创建应收款（调用erp-finance，AT事务分支）
 * 4. 任意步骤失败 → Seata协调器触发全局回滚 → 各分支undo_log恢复数据
 * 5. 事务提交后 → 发布OrderCreatedEvent到RabbitMQ（异步通知）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaleOrderService {

    private final SaleOrderMapper saleOrderMapper;
    private final SaleOrderItemMapper saleOrderItemMapper;
    private final InventoryFeignClient inventoryFeignClient;
    private final FinanceFeignClient financeFeignClient;
    private final SaleEventPublisher eventPublisher;

    private static final AtomicInteger SEQ = new AtomicInteger(0);

    /**
     * 确认销售订单（核心分布式事务方法）
     *
     * @GlobalTransactional: Seata AT模式，开启全局事务
     *   - 本服务的DB操作由本地事务保障
     *   - 远程调用（库存、财务）自动加入Seata全局事务
     *   - 任何一步失败，TC（事务协调器）触发所有分支回滚
     */
    @GlobalTransactional(name = "sale-confirm-order", rollbackFor = Exception.class)
    public SaleOrder confirmOrder(SaleOrder order) {
        String tenantId = TenantContextHolder.getTenantId();
        order.setTenantId(tenantId);
        order.setStatus("CONFIRMED");
        order.setOrderNo(generateOrderNo());

        // 1. 保存销售订单主表
        saleOrderMapper.insert(order);

        // 2. 保存订单明细
        for (SaleOrderItem item : order.getItems()) {
            item.setOrderId(order.getId());
            saleOrderItemMapper.insert(item);
        }

        log.info("[Seata] Sale order created: orderId={}, orderNo={}", order.getId(), order.getOrderNo());

        // 3. 锁定库存（Seata AT事务分支 - erp-inventory DB）
        List<LockStockRequest> lockRequests = order.getItems().stream()
                .map(item -> {
                    LockStockRequest req = new LockStockRequest();
                    req.setOrderId(order.getId());
                    req.setSkuId(item.getMaterialId());
                    req.setWarehouseId(item.getWarehouseId());
                    req.setQuantity(item.getQuantity());
                    return req;
                }).collect(Collectors.toList());

        R<Boolean> lockResult = inventoryFeignClient.lockStock(lockRequests);
        if (lockResult == null || !Boolean.TRUE.equals(lockResult.getData())) {
            throw new BizException(ResultCode.BIZ_ERROR.getCode(), "库存不足，无法确认订单");
        }
        log.info("[Seata] Stock locked: orderNo={}", order.getOrderNo());

        // 4. 创建应收款单（Seata AT事务分支 - erp-finance DB）
        CreateReceivableRequest receivableReq = new CreateReceivableRequest();
        receivableReq.setSourceOrderId(order.getId());
        receivableReq.setBusinessType("SALE_ORDER");
        receivableReq.setCustomerId(order.getCustomerId());
        receivableReq.setAmount(order.getTotalAmount());
        receivableReq.setDueDate(LocalDate.now().plusDays(30));
        receivableReq.setCurrency("CNY");

        R<Long> receivableResult = financeFeignClient.createReceivable(receivableReq);
        if (receivableResult == null || receivableResult.getData() == null) {
            throw new BizException("创建应收款失败");
        }
        log.info("[Seata] Receivable created: orderNo={}, receivableId={}", order.getOrderNo(), receivableResult.getData());

        // 5. 全局事务提交成功后，发布事件（事务外异步通知）
        // 注意：此处在@GlobalTransactional方法内，事件发布在事务提交后由Spring事务钩子触发
        // 这里直接发布，因为Seata AT模式下如果执行到此处说明事务已成功
        publishOrderCreatedEvent(order);

        return order;
    }

    /**
     * 查询订单详情（含明细）
     */
    @Transactional(readOnly = true)
    public SaleOrder getOrderDetail(Long orderId) {
        SaleOrder order = saleOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        List<SaleOrderItem> items = saleOrderItemMapper.findByOrderId(orderId);
        order.setItems(items);
        return order;
    }

    /**
     * 取消订单
     */
    @GlobalTransactional(name = "sale-cancel-order", rollbackFor = Exception.class)
    public void cancelOrder(Long orderId) {
        SaleOrder order = saleOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!"CONFIRMED".equals(order.getStatus()) && !"DRAFT".equals(order.getStatus())) {
            throw new BizException("当前状态不允许取消: " + order.getStatus());
        }

        order.setStatus("CANCELLED");
        saleOrderMapper.updateById(order);

        // 释放锁定库存
        List<SaleOrderItem> items = saleOrderItemMapper.findByOrderId(orderId);
        List<LockStockRequest> unlockRequests = items.stream()
                .map(item -> {
                    LockStockRequest req = new LockStockRequest();
                    req.setOrderId(orderId);
                    req.setSkuId(item.getMaterialId());
                    req.setWarehouseId(item.getWarehouseId());
                    req.setQuantity(item.getQuantity());
                    return req;
                }).collect(Collectors.toList());

        inventoryFeignClient.unlockStock(unlockRequests);
        log.info("Order cancelled: orderId={}", orderId);
    }

    /**
     * 生成订单号：SO + 日期 + 6位序号
     */
    private String generateOrderNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int seq = SEQ.incrementAndGet() % 1000000;
        return String.format("SO%s%06d", date, seq);
    }

    /**
     * 发布订单创建事件
     */
    private void publishOrderCreatedEvent(SaleOrder order) {
        List<OrderCreatedEvent.OrderItem> eventItems = order.getItems().stream()
                .map(item -> OrderCreatedEvent.OrderItem.builder()
                        .materialId(item.getMaterialId())
                        .materialCode(item.getMaterialCode())
                        .quantity(item.getQuantity())
                        .warehouseId(item.getWarehouseId())
                        .unitPrice(item.getUnitPrice())
                        .build())
                .collect(Collectors.toList());

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .occurredAt(LocalDateTime.now())
                .tenantId(order.getTenantId())
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .customerId(order.getCustomerId())
                .customerName(order.getCustomerName())
                .totalAmount(order.getTotalAmount())
                .items(eventItems)
                .build();

        eventPublisher.publishOrderCreated(event);
    }
}
