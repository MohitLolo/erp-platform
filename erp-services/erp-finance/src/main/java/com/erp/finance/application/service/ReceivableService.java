package com.erp.finance.application.service;

import com.erp.common.core.exception.BizException;
import com.erp.common.core.response.ResultCode;
import com.erp.finance.domain.entity.Receivable;
import com.erp.finance.infrastructure.mapper.ReceivableMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 应收款服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceivableService {

    private final ReceivableMapper receivableMapper;
    private static final AtomicInteger SEQ = new AtomicInteger(0);

    /**
     * 创建应收款（供erp-sale通过Feign调用，在Seata全局事务内）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createReceivable(String sourceNo, String sourceType, Long customerId,
                                  String customerName, BigDecimal amount,
                                  LocalDate dueDate, String tenantId, String currency) {
        // 幂等检查
        Receivable existing = receivableMapper.findBySourceNo(sourceNo);
        if (existing != null) {
            log.info("Receivable already exists for sourceNo={}, idempotent return", sourceNo);
            return existing.getId();
        }

        Receivable receivable = new Receivable();
        receivable.setTenantId(tenantId);
        receivable.setReceivableNo(generateReceivableNo());
        receivable.setSourceNo(sourceNo);
        receivable.setSourceType(sourceType);
        receivable.setCustomerId(customerId);
        receivable.setCustomerName(customerName);
        receivable.setAmount(amount);
        receivable.setReceivedAmount(BigDecimal.ZERO);
        receivable.setUnreceived(amount);
        receivable.setCurrency(currency);
        receivable.setDueDate(dueDate);
        receivable.setStatus("PENDING");

        receivableMapper.insert(receivable);
        log.info("[Seata] Receivable created: receivableNo={}, amount={}", receivable.getReceivableNo(), amount);
        return receivable.getId();
    }

    /**
     * 记录收款（核销应收款）
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordReceipt(Long receivableId, BigDecimal receiptAmount) {
        Receivable receivable = receivableMapper.selectById(receivableId);
        if (receivable == null) {
            throw new BizException(ResultCode.NOT_FOUND, "应收款不存在");
        }
        if ("SETTLED".equals(receivable.getStatus())) {
            throw new BizException(ResultCode.BIZ_ERROR, "应收款已结清");
        }

        BigDecimal newReceived = receivable.getReceivedAmount().add(receiptAmount);
        if (newReceived.compareTo(receivable.getAmount()) > 0) {
            throw new BizException(ResultCode.BIZ_ERROR, "收款金额超过应收金额");
        }

        receivable.setReceivedAmount(newReceived);
        receivable.setUnreceived(receivable.getAmount().subtract(newReceived));

        if (newReceived.compareTo(receivable.getAmount()) == 0) {
            receivable.setStatus("SETTLED");
        } else {
            receivable.setStatus("PARTIAL");
        }
        receivableMapper.updateById(receivable);
    }

    private String generateReceivableNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int seq = SEQ.incrementAndGet() % 1000000;
        return String.format("AR%s%06d", date, seq);
    }
}
