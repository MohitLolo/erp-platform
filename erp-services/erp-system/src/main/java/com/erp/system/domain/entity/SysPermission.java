package com.erp.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统权限/菜单
 * 支持树形结构（parent_id）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    /**
     * 父节点ID（0表示根节点）
     */
    private Long parentId;

    /**
     * 权限编码（如 system:user:list, purchase:order:create）
     */
    private String permCode;

    /**
     * 权限名称
     */
    private String permName;

    /**
     * 类型：1-菜单，2-按钮，3-接口
     */
    private Integer permType;

    /**
     * 路由地址（菜单类型）
     */
    private String routePath;

    /**
     * 组件路径（菜单类型）
     */
    private String component;

    /**
     * 接口路径（接口类型）
     */
    private String apiPath;

    /**
     * 接口方法（GET/POST/PUT/DELETE）
     */
    private String apiMethod;

    /**
     * 排序号
     */
    private Integer sortOrder;

    /**
     * 图标
     */
    private String icon;

    /**
     * 状态：1-启用，0-禁用
     */
    private Integer status;
}
