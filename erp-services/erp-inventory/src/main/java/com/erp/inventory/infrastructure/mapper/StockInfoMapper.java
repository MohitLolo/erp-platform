package com.erp.inventory.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.inventory.domain.entity.StockInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 库存Mapper
 */
@Mapper
public interface StockInfoMapper extends BaseMapper<StockInfo> {

    /**
     * 查询库存（仓库+物料，加行锁用于update）
     */
    @Select("SELECT * FROM inv_stock WHERE warehouse_id = #{warehouseId} AND material_id = #{materialId} AND tenant_id = #{tenantId} AND deleted = 0 FOR UPDATE")
    StockInfo findForUpdate(@Param("warehouseId") Long warehouseId,
                             @Param("materialId") Long materialId,
                             @Param("tenantId") String tenantId);

    @Select("SELECT * FROM inv_stock WHERE warehouse_id = #{warehouseId} AND material_id = #{materialId} AND tenant_id = #{tenantId} AND deleted = 0")
    StockInfo findOne(@Param("warehouseId") Long warehouseId,
                       @Param("materialId") Long materialId,
                       @Param("tenantId") String tenantId);
}
