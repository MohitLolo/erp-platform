package com.erp.system.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.system.domain.entity.SysDept;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 部门 Mapper
 */
@Mapper
public interface SysDeptMapper extends BaseMapper<SysDept> {

    /**
     * 查询某部门的所有下级部门ID（含自身）
     *
     * <p>使用递归 CTE 查询部门树，适配 MySQL 8+
     */
    @Select("""
            WITH RECURSIVE dept_tree AS (
                SELECT id FROM sys_dept WHERE id = #{deptId} AND deleted = 0
                UNION ALL
                SELECT d.id FROM sys_dept d
                INNER JOIN dept_tree dt ON d.parent_id = dt.id
                WHERE d.deleted = 0
            )
            SELECT id FROM dept_tree
            """)
    List<Long> findSubDeptIds(@Param("deptId") Long deptId);
}
