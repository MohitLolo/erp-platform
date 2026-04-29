package com.erp.api.base.feign;

import com.erp.api.base.dto.MaterialDTO;
import com.erp.common.core.response.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 基础数据服务 Feign 客户端（物料）
 *
 * @author erp
 * @since 1.0.0
 */
@FeignClient(
        name = "erp-base",
        url = "${feign.base.url:http://erp-base.erp-prod.svc.cluster.local:8080}"
)
public interface MaterialFeignClient {

    /**
     * 根据物料 ID 查询物料信息
     *
     * @param materialId 物料 ID
     * @return 物料信息
     */
    @GetMapping("/base/inner/material/{materialId}")
    R<MaterialDTO> getMaterialById(@PathVariable("materialId") Long materialId);
}
