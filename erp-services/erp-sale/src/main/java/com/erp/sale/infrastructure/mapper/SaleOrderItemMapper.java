package com.erp.sale.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.sale.domain.entity.SaleOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 销售订单明细Mapper
 */
@Mapper
public interface SaleOrderItemMapper extends BaseMapper<SaleOrderItem> {

    @Select("SELECT * FROM sale_order_item WHERE order_id = #{orderId} AND deleted = 0")
    List<SaleOrderItem> findByOrderId(@Param("orderId") Long orderId);
}
