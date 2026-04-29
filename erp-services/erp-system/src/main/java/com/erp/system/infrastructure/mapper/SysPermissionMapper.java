package com.erp.system.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.system.domain.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限Mapper
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /**
     * 查询用户所有权限编码（通过角色关联）
     */
    @Select("""
            SELECT DISTINCT p.perm_code
            FROM sys_permission p
            INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
            INNER JOIN sys_role r ON r.id = rp.role_id
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
              AND r.status = 1
              AND p.status = 1
              AND p.deleted = 0
            """)
    List<String> findPermCodesByUserId(@Param("userId") Long userId);

    /**
     * 查询用户所有角色编码
     */
    @Select("""
            SELECT DISTINCT r.role_code
            FROM sys_role r
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
              AND r.status = 1
              AND r.deleted = 0
            """)
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);
}
