package com.erp.production.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * 物料清单（BOM）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pro_bom")
public class Bom extends BaseEntity {

    private String tenantId;
    private String bomCode;

    /**
     * 产成品物料ID
     */
    private Long productMaterialId;
    private String productMaterialCode;
    private String productMaterialName;

    /**
     * BOM版本号
     */
    private String version;

    /**
     * 基准产量
     */
    private BigDecimal baseQty;
    private String unit;

    /**
     * 状态：DRAFT-草稿, ACTIVE-启用, OBSOLETE-废弃
     */
    private String status;
    private String remark;

    @TableField(exist = false)
    private List<BomItem> items;
}
