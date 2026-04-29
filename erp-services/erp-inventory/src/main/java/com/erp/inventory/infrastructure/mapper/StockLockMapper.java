package com.erp.inventory.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.inventory.domain.entity.StockLock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 库存锁定记录Mapper
 */
@Mapper
public interface StockLockMapper extends BaseMapper<StockLock> {

    @Select("""
            SELECT * FROM inv_stock_lock
            WHERE biz_no = #{bizNo}
              AND warehouse_id = #{warehouseId}
              AND material_id = #{materialId}
              AND deleted = 0
            LIMIT 1
            """)
    StockLock findByBizNoAndMaterial(@Param("bizNo") String bizNo,
                                      @Param("warehouseId") Long warehouseId,
                                      @Param("materialId") Long materialId);
}
