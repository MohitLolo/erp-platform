package com.erp.api.base.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 物料信息 DTO
 *
 * @author erp
 * @since 1.0.0
 */
@Data
public class MaterialDTO {

    /** 物料 ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 规格型号 */
    private String specification;

    /** 计量单位（kg / 件 / 箱 等） */
    private String unit;

    /** 物料类别（RAW=原材料, SEMI=半成品, FINISHED=成品） */
    private String category;

    /** 标准成本 */
    private BigDecimal standardCost;

    /** 是否启用（true=启用） */
    private Boolean enabled;
}
