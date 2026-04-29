package com.erp.common.core.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 所有业务实体的基类
 *
 * <p>提供：主键（雪花ID）、创建/更新时间、操作人、逻辑删除字段
 * 子类使用 {@code @EqualsAndHashCode(callSuper = true)} 正确实现 equals/hashCode
 *
 * @author erp
 * @since 1.0.0
 */
@Data
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花 ID，由 MyBatis-Plus 自动生成）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 最后更新时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 创建人 ID（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /**
     * 最后更新人 ID（自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /**
     * 逻辑删除标志（0=未删除，1=已删除）
     */
    @TableLogic
    private Integer deleted;
}
