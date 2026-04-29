package com.erp.sale.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.sale.domain.entity.SaleOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 销售订单Mapper
 */
@Mapper
public interface SaleOrderMapper extends BaseMapper<SaleOrder> {

    /**
     * 根据订单号查询（唯一索引）
     */
    @Select("SELECT * FROM sale_order WHERE order_no = #{orderNo} AND deleted = 0")
    SaleOrder findByOrderNo(@Param("orderNo") String orderNo);
}
