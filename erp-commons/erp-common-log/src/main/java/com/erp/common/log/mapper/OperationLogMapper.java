package com.erp.common.log.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.common.log.entity.OperationLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 操作日志 Mapper。
 *
 * <p>类级 {@link DS} 切换到 {@code log} 数据源（业务服务在 {@code application.yml} 中
 * 配置 {@code spring.datasource.dynamic.datasource.log} 指向 erp_log 库）。
 * 任何调用本 Mapper 的方法都会自动走 erp_log 数据源，与业务库完全隔离。</p>
 */
@Mapper
@DS("log")
public interface OperationLogMapper extends BaseMapper<OperationLog> {

    /**
     * 按时间阈值分批删除（XXL-JOB 清理任务用）。
     *
     * <p>用 {@code LIMIT} 控制单批大小，避免一次锁太多行；调用方循环调用直到返回 0。</p>
     *
     * @param threshold 删除 occurred_at &lt; threshold 的记录
     * @param batchSize 单批最大删除条数
     * @return 实际删除条数
     */
    @Delete("DELETE FROM operation_log WHERE occurred_at < #{threshold} LIMIT #{batchSize}")
    int deleteBefore(@Param("threshold") LocalDateTime threshold, @Param("batchSize") int batchSize);
}
