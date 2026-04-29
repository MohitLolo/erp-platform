package com.erp.api.inventory.feign;

import com.erp.api.inventory.dto.LockStockRequest;
import com.erp.common.core.response.R;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 库存服务 Feign 客户端
 *
 * <p>仅供服务间调用，接口路径为 {@code /inventory/inner/*}，
 * 网关层会拒绝外部请求访问 inner 路径。
 *
 * @author erp
 * @since 1.0.0
 */
@FeignClient(
        name = "erp-inventory",
        url = "${feign.inventory.url:http://erp-inventory.erp-prod.svc.cluster.local:8080}"
)
public interface InventoryFeignClient {

    /**
     * 锁定库存（下单时预占）
     *
     * @param requests 锁库请求列表（每个元素对应一个 SKU）
     * @return 锁库是否全部成功
     */
    @PostMapping("/inventory/inner/stock/lock")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "lockStockFallback")
    R<Boolean> lockStock(@RequestBody List<LockStockRequest> requests);

    /**
     * 解锁库存（订单取消时释放预占）
     *
     * @param requests 解锁请求列表
     * @return 解锁是否全部成功
     */
    @PostMapping("/inventory/inner/stock/unlock")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "unlockStockFallback")
    R<Boolean> unlockStock(@RequestBody List<LockStockRequest> requests);

    default R<Boolean> lockStockFallback(List<LockStockRequest> requests, Throwable t) {
        return R.fail(503, "库存服务暂不可用: " + t.getMessage());
    }

    default R<Boolean> unlockStockFallback(List<LockStockRequest> requests, Throwable t) {
        return R.fail(503, "库存服务暂不可用: " + t.getMessage());
    }
}
