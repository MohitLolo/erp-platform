package com.erp.sale.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.erp.common.result.R;
import com.erp.sale.application.service.SaleOrderService;
import com.erp.sale.domain.entity.SaleOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 销售订单控制器
 */
@RestController
@RequestMapping("/sale/order")
@RequiredArgsConstructor
public class SaleOrderController {

    private final SaleOrderService saleOrderService;

    /**
     * 确认销售订单（触发Seata分布式事务）
     */
    @PostMapping("/confirm")
    @SaCheckPermission("sale:order:create")
    public R<SaleOrder> confirmOrder(@RequestBody SaleOrder order) {
        return R.ok(saleOrderService.confirmOrder(order));
    }

    /**
     * 获取订单详情
     */
    @GetMapping("/{orderId}")
    @SaCheckPermission("sale:order:query")
    public R<SaleOrder> getDetail(@PathVariable Long orderId) {
        return R.ok(saleOrderService.getOrderDetail(orderId));
    }

    /**
     * 取消订单
     */
    @PostMapping("/{orderId}/cancel")
    @SaCheckPermission("sale:order:cancel")
    public R<Void> cancelOrder(@PathVariable Long orderId) {
        saleOrderService.cancelOrder(orderId);
        return R.ok();
    }
}
