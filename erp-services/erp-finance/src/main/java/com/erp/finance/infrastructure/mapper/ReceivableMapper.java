package com.erp.finance.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.finance.domain.entity.Receivable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 应收款Mapper
 */
@Mapper
public interface ReceivableMapper extends BaseMapper<Receivable> {

    @Select("SELECT * FROM fin_receivable WHERE source_no = #{sourceNo} AND deleted = 0 LIMIT 1")
    Receivable findBySourceNo(@Param("sourceNo") String sourceNo);
}
