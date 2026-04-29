package com.erp.inventory.application.service;

import com.erp.common.exception.BizException;
import com.erp.common.result.ResultCode;
import com.erp.common.tenant.TenantContextHolder;
import com.erp.inventory.domain.entity.StockInfo;
import com.erp.inventory.domain.entity.StockLock;
import com.erp.inventory.infrastructure.mapper.StockInfoMapper;
import com.erp.inventory.infrastructure.mapper.StockLockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存服务
 *
 * 核心防超卖机制：
 * 1. 乐观锁（@Version）：MyBatis-Plus自动在update语句中加version条件
 * 2. 重试机制：乐观锁冲突时最多重试3次
 * 3. Seata AT：作为事务分支，全局回滚时undo_log自动恢复库存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockInfoMapper stockInfoMapper;
    private final StockLockMapper stockLockMapper;

    private static final int MAX_RETRY = 3;

    /**
     * 锁定库存（供erp-sale通过Feign调用，在Seata全局事务内）
     * 幂等设计：同一bizNo重复调用不重复锁定
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean lockStock(Long warehouseId, Long materialId,
                              BigDecimal quantity, String bizNo, String tenantId) {
        // 幂等检查：同一bizNo+物料已锁定则直接返回
        StockLock existingLock = stockLockMapper.findByBizNoAndMaterial(bizNo, warehouseId, materialId);
        if (existingLock != null && "LOCKED".equals(existingLock.getLockStatus())) {
            log.info("Stock already locked, idempotent: bizNo={}, materialId={}", bizNo, materialId);
            return true;
        }

        // 乐观锁重试
        for (int i = 0; i < MAX_RETRY; i++) {
            StockInfo stock = stockInfoMapper.findOne(warehouseId, materialId, tenantId);
            if (stock == null) {
                throw new BizException(ResultCode.STOCK_INSUFFICIENT.getCode(),
                        "物料库存记录不存在: materialId=" + materialId);
            }

            if (stock.getAvailableQty().compareTo(quantity) < 0) {
                throw new BizException(ResultCode.STOCK_INSUFFICIENT.getCode(),
                        String.format("库存不足: materialId=%d, 可用=%.2f, 需要=%.2f",
                                materialId, stock.getAvailableQty(), quantity));
            }

            // 乐观锁更新：MyBatis-Plus自动加 version 条件
            stock.setAvailableQty(stock.getAvailableQty().subtract(quantity));
            stock.setLockedQty(stock.getLockedQty().add(quantity));
            int updated = stockInfoMapper.updateById(stock);

            if (updated > 0) {
                // 记录锁定明细
                StockLock lockRecord = new StockLock();
                lockRecord.setTenantId(tenantId);
                lockRecord.setWarehouseId(warehouseId);
                lockRecord.setMaterialId(materialId);
                lockRecord.setLockedQty(quantity);
                lockRecord.setBizNo(bizNo);
                lockRecord.setBizType("SALE_ORDER");
                lockRecord.setLockStatus("LOCKED");
                lockRecord.setLockTime(LocalDateTime.now());
                stockLockMapper.insert(lockRecord);

                log.info("Stock locked: materialId={}, qty={}, bizNo={}", materialId, quantity, bizNo);
                return true;
            }
            // 乐观锁冲突，重试
            log.warn("Optimistic lock conflict, retry {}/{}: materialId={}", i + 1, MAX_RETRY, materialId);
        }
        throw new BizException("库存锁定失败，并发冲突，请重试");
    }

    /**
     * 释放锁定库存（订单取消时调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean unlockStock(Long warehouseId, Long materialId,
                                BigDecimal quantity, String bizNo, String tenantId) {
        StockLock lockRecord = stockLockMapper.findByBizNoAndMaterial(bizNo, warehouseId, materialId);
        if (lockRecord == null || !"LOCKED".equals(lockRecord.getLockStatus())) {
            log.warn("No active lock found: bizNo={}, materialId={}", bizNo, materialId);
            return true;
        }

        for (int i = 0; i < MAX_RETRY; i++) {
            StockInfo stock = stockInfoMapper.findOne(warehouseId, materialId, tenantId);
            if (stock == null) return false;

            stock.setAvailableQty(stock.getAvailableQty().add(quantity));
            stock.setLockedQty(stock.getLockedQty().subtract(quantity));
            int updated = stockInfoMapper.updateById(stock);

            if (updated > 0) {
                lockRecord.setLockStatus("RELEASED");
                lockRecord.setReleaseTime(LocalDateTime.now());
                stockLockMapper.updateById(lockRecord);
                log.info("Stock unlocked: materialId={}, qty={}, bizNo={}", materialId, quantity, bizNo);
                return true;
            }
        }
        throw new BizException("库存解锁失败，并发冲突，请重试");
    }

    /**
     * 批量锁定库存（供Feign调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean lockStockBatch(List<LockStockCmd> cmds) {
        for (LockStockCmd cmd : cmds) {
            lockStock(cmd.getWarehouseId(), cmd.getMaterialId(),
                    cmd.getQuantity(), cmd.getBizNo(), cmd.getTenantId());
        }
        return true;
    }

    /**
     * 批量解锁（供Feign调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean unlockStockBatch(List<LockStockCmd> cmds) {
        String tenantId = TenantContextHolder.getTenantId();
        for (LockStockCmd cmd : cmds) {
            unlockStock(cmd.getWarehouseId(), cmd.getMaterialId(),
                    cmd.getQuantity(), cmd.getBizNo(), tenantId);
        }
        return true;
    }

    /**
     * 锁定库存命令对象
     */
    public record LockStockCmd(Long warehouseId, Long materialId,
                                BigDecimal quantity, String bizNo, String tenantId) {
    }
}
