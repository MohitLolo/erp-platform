package com.erp.system.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.system.domain.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户Mapper
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 根据tenantId+username查询用户（用于登录验证）
     */
    @Select("SELECT * FROM sys_user WHERE tenant_id = #{tenantId} AND username = #{username} AND deleted = 0")
    SysUser findByTenantAndUsername(@Param("tenantId") String tenantId,
                                    @Param("username") String username);
}
