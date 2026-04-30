package com.erp.inventory.controller;

import com.erp.common.core.response.R;
import com.erp.inventory.application.service.StockService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 库存内部接口（供erp-sale通过Feign调用）
 */
@RestController
@RequestMapping("/inventory/inner/stock")
@RequiredArgsConstructor
public class InnerStockController {

    private final StockService stockService;

    /**
     * 批量锁定库存
     */
    @PostMapping("/lock")
    public R<Boolean> lockStock(@RequestBody List<LockStockRequest> requests) {
        List<StockService.LockStockCmd> cmds = requests.stream()
                .map(r -> new StockService.LockStockCmd(
                        r.getWarehouseId(), r.getMaterialId(),
                        r.getQuantity(), r.getBizNo(), r.getTenantId()))
                .collect(Collectors.toList());
        return R.ok(stockService.lockStockBatch(cmds));
    }

    /**
     * 批量解锁库存
     */
    @PostMapping("/unlock")
    public R<Boolean> unlockStock(@RequestBody List<LockStockRequest> requests) {
        List<StockService.LockStockCmd> cmds = requests.stream()
                .map(r -> new StockService.LockStockCmd(
                        r.getWarehouseId(), r.getMaterialId(),
                        r.getQuantity(), r.getBizNo(), r.getTenantId()))
                .collect(Collectors.toList());
        return R.ok(stockService.unlockStockBatch(cmds));
    }

    @Data
    public static class LockStockRequest {
        private Long warehouseId;
        private Long materialId;
        private BigDecimal quantity;
        private String bizNo;
        private String tenantId;
    }
}
