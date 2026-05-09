package com.erp.common.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户操作日志实体（对应 {@code erp_log.operation_log} 表）。
 *
 * <p>不继承 {@code BaseEntity}：日志表本身不审计自己，不需要 {@code createBy/updateBy/deleted}。
 * 字段命名采用 camelCase，与 SQL 列 snake_case 通过 MyBatis-Plus 默认下划线策略自动映射。</p>
 */
@Data
@TableName("operation_log")
public class OperationLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;
    private String serviceName;
    private String traceId;

    private Long userId;
    private String userName;
    private String clientIp;
    private String userAgent;

    private String permissionCode;
    private String permissionName;
    private String permissionModule;
    private String permissionType;

    private String httpMethod;
    private String requestUri;
    private String methodSignature;

    /** 入参 JSON（脱敏后）。 */
    private String requestParams;
    /** 返回值 JSON（脱敏后），仅 {@code @LogResult} 标记时记录。 */
    private String responseData;

    /** {@link com.erp.common.log.enums.OperationStatus} 字符串形式。 */
    private String status;
    private String errorMessage;
    private Integer durationMs;

    /** 操作发生时间（毫秒精度）。 */
    private LocalDateTime occurredAt;

    /** 入库时间（DB 默认填充，可不在 Java 侧赋值）。 */
    @TableField(insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NOT_NULL)
    private LocalDateTime createTime;
}
