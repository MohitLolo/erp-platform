package com.erp.base.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 仓库档案
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bas_warehouse")
public class Warehouse extends BaseEntity {

    private String tenantId;
    private String warehouseCode;
    private String warehouseName;

    /**
     * 仓库类型：NORMAL-普通仓，RAW-原材料仓，FG-成品仓，TRANSIT-在途仓
     */
    private String warehouseType;

    private String address;
    private String contactPerson;
    private String contactPhone;
    private Long managerId;

    /**
     * 是否启用库位管理
     */
    private Boolean locationManaged;

    private Integer status;
    private String remark;
}
