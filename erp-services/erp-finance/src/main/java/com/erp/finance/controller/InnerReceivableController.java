package com.erp.finance.controller;

import com.erp.common.result.R;
import com.erp.finance.application.service.ReceivableService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 财务内部接口（供erp-sale Feign调用）
 */
@RestController
@RequestMapping("/finance/inner/receivable")
@RequiredArgsConstructor
public class InnerReceivableController {

    private final ReceivableService receivableService;

    /**
     * 创建应收款
     */
    @PostMapping("/create")
    public R<Long> createReceivable(@RequestBody CreateReceivableRequest request) {
        Long id = receivableService.createReceivable(
                request.getSourceNo(), request.getSourceType(),
                request.getCustomerId(), request.getCustomerName(),
                request.getAmount(), request.getDueDate(),
                request.getTenantId(), request.getCurrency()
        );
        return R.ok(id);
    }

    @Data
    public static class CreateReceivableRequest {
        private String sourceNo;
        private String sourceType;
        private Long customerId;
        private String customerName;
        private BigDecimal amount;
        private LocalDate dueDate;
        private String tenantId;
        private String currency;
    }
}
