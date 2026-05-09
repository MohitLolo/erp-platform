package com.erp.common.log.resolver;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 权限元信息（缓存值对象）。
 *
 * <p>L1/L2 缓存承载的统一结构：从 {@code sys_permission} 表浓缩出供日志展示用的必要字段，
 * 而不缓存整行（减少 Redis 内存占用）。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionMeta implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 权限编码（唯一键）。 */
    private String code;
    /** 权限名称（日志展示用）。 */
    private String name;
    /** 所属模块。 */
    private String module;
    /** 权限类型（menu/button/api 等）。 */
    private String type;
}
