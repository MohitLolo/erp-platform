package com.erp.report.infrastructure.mapper;

import com.erp.report.domain.entity.SaleAnalysisVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 销售分析Mapper（查询Doris OLAP数据库）
 * Doris兼容MySQL协议，通过MySQL Driver连接
 */
@Mapper
public interface SaleAnalysisMapper {

    /**
     * 按月统计销售额（从Doris ads_sale_monthly宽表查询）
     */
    @Select("""
            SELECT
                tenant_id,
                year_month,
                customer_name,
                material_name,
                SUM(qty) AS total_qty,
                SUM(amount) AS total_amount,
                AVG(unit_price) AS avg_unit_price
            FROM ads_sale_monthly
            WHERE tenant_id = #{tenantId}
              AND year_month BETWEEN #{startMonth} AND #{endMonth}
            GROUP BY tenant_id, year_month, customer_name, material_name
            ORDER BY year_month DESC, total_amount DESC
            """)
    List<SaleAnalysisVO> querySaleByMonth(@Param("tenantId") String tenantId,
                                           @Param("startMonth") String startMonth,
                                           @Param("endMonth") String endMonth);
}
