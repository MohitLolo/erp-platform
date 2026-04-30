package com.erp.api.finance.feign;

import com.erp.api.finance.dto.CreateReceivableRequest;
import com.erp.common.core.response.R;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 财务服务 Feign 客户端
 *
 * <p>供销售服务在生成应收账款时调用。
 *
 * @author erp
 * @since 1.0.0
 */
@FeignClient(
        name = "erp-finance",
        url = "${feign.finance.url:http://erp-finance:8080}"
)
public interface FinanceFeignClient {

    /**
     * 创建应收账款
     *
     * @param request 应收账款创建请求
     * @return 创建的应收账款 ID
     */
    @PostMapping("/finance/inner/receivable/create")
    @CircuitBreaker(name = "financeService", fallbackMethod = "createReceivableFallback")
    R<Long> createReceivable(@RequestBody CreateReceivableRequest request);

    default R<Long> createReceivableFallback(CreateReceivableRequest request, Throwable t) {
        return R.fail(503, "财务服务暂不可用: " + t.getMessage());
    }
}
