package com.erp.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.erp.common.core.response.R;
import com.erp.purchase.domain.entity.PurchaseOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 采购订单接口
 *
 * <p>权限码规范：{模块}:{资源}:{操作}
 * 对应 sys_permission.perm_code，需在数据库中有对应记录。
 */
@Slf4j
@RestController
@RequestMapping("/purchase/orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    // TODO: 注入 PurchaseOrderService（待实现）

    /**
     * 查询采购订单列表
     */
    @GetMapping("/list")
    @SaCheckPermission("purchase:order:list")
    public R<List<PurchaseOrder>> list() {
        // TODO: 调用 service
        return R.ok(List.of());
    }

    /**
     * 查询采购订单详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("purchase:order:query")
    public R<PurchaseOrder> getById(@PathVariable Long id) {
        // TODO: 调用 service
        return R.ok(null);
    }

    /**
     * 新建采购订单（草稿）
     */
    @PostMapping
    @SaCheckPermission("purchase:order:create")
    public R<Void> create(@RequestBody PurchaseOrder order) {
        // TODO: 调用 service
        return R.ok();
    }

    /**
     * 确认采购订单
     */
    @PutMapping("/{id}/confirm")
    @SaCheckPermission("purchase:order:confirm")
    public R<Void> confirm(@PathVariable Long id) {
        // TODO: 调用 service
        return R.ok();
    }

    /**
     * 取消采购订单
     */
    @PutMapping("/{id}/cancel")
    @SaCheckPermission("purchase:order:cancel")
    public R<Void> cancel(@PathVariable Long id) {
        // TODO: 调用 service
        return R.ok();
    }
}
