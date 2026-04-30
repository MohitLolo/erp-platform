package com.erp.system.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.system.domain.entity.SysUserDept;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户部门关联 Mapper
 */
@Mapper
public interface SysUserDeptMapper extends BaseMapper<SysUserDept> {

    /**
     * 查询用户的所有部门ID
     */
    @Select("SELECT dept_id FROM sys_user_dept WHERE user_id = #{userId}")
    List<Long> findDeptIdsByUserId(@Param("userId") Long userId);
}
