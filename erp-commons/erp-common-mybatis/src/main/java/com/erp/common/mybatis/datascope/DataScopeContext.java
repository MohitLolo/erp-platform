package com.erp.common.mybatis.datascope;

import java.io.Serializable;
import java.util.Set;

/**
 * 数据权限上下文 DTO
 *
 * <p>承载当前请求用户的数据权限信息，由 {@link DataScopeFilter} 从 Redis 加载后
 * 存入 {@link DataScopeContextHolder}，供 {@link ErpDataPermissionHandler} 消费。
 *
 * <p>dataScope 含义（见 {@link DataScopeLevel}）：
 * <ul>
 *   <li>1 - 全部数据，不附加任何过滤条件</li>
 *   <li>2 - 本部门数据</li>
 *   <li>3 - 本部门及所有下级部门数据</li>
 *   <li>4 - 仅本人创建的数据</li>
 *   <li>5 - 自定义部门列表（sys_role_dept 配置）</li>
 * </ul>
 *
 * @author erp
 * @since 1.0.0
 */
public class DataScopeContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前用户有效权限档位（多角色取最小值，1 = 最宽松） */
    private Integer dataScope;

    /** 当前用户 ID */
    private Long userId;

    /** 用户主部门 ID */
    private Long primaryDeptId;

    /**
     * 有效部门 ID 集合：
     * <ul>
     *   <li>scope=2：仅主部门 ID</li>
     *   <li>scope=3：主部门 + 所有子孙部门 ID</li>
     *   <li>scope=5：角色指定的自定义部门 ID 列表</li>
     * </ul>
     */
    private Set<Long> deptIds;

    public DataScopeContext() {}

    public DataScopeContext(Integer dataScope, Long userId, Long primaryDeptId, Set<Long> deptIds) {
        this.dataScope = dataScope;
        this.userId = userId;
        this.primaryDeptId = primaryDeptId;
        this.deptIds = deptIds;
    }

    public Integer getDataScope() {
        return dataScope;
    }

    public void setDataScope(Integer dataScope) {
        this.dataScope = dataScope;
    }

    public DataScopeLevel getDataScopeLevel() {
        return DataScopeLevel.fromCode(dataScope);
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getPrimaryDeptId() {
        return primaryDeptId;
    }

    public void setPrimaryDeptId(Long primaryDeptId) {
        this.primaryDeptId = primaryDeptId;
    }

    public Set<Long> getDeptIds() {
        return deptIds;
    }

    public void setDeptIds(Set<Long> deptIds) {
        this.deptIds = deptIds;
    }
}
