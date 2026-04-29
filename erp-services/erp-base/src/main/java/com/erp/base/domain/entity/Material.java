package com.erp.base.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 物料档案
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bas_material")
public class Material extends BaseEntity {

    private String tenantId;
    private String materialCode;
    private String materialName;

    /**
     * 物料分类ID
     */
    private Long categoryId;

    /**
     * 物料类型：RAW-原材料，WIP-在制品，FG-产成品，PACKAGING-包材，CONSUMABLE-消耗品
     */
    private String materialType;

    private String spec;
    private String unit;

    /**
     * 含税采购价（参考）
     */
    private BigDecimal purchasePrice;

    /**
     * 含税销售价（参考）
     */
    private BigDecimal salesPrice;

    /**
     * 标准成本
     */
    private BigDecimal standardCost;

    /**
     * 安全库存
     */
    private BigDecimal safetyStock;

    /**
     * 采购提前期（天）
     */
    private Integer leadTimeDays;

    /**
     * 是否启用批次管理
     */
    private Boolean batchManaged;

    private Integer status;
    private String remark;
}
