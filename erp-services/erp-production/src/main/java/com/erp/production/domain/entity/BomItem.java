package com.erp.production.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * BOM明细（子物料）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pro_bom_item")
public class BomItem extends BaseEntity {

    private Long bomId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String unit;
    private BigDecimal quantity;

    /**
     * 损耗率（如 0.05 表示5%损耗）
     */
    private BigDecimal wastageRate;

    /**
     * 子件类型：MATERIAL-物料，PHANTOM-虚拟件
     */
    private String itemType;

    private String remark;
}
