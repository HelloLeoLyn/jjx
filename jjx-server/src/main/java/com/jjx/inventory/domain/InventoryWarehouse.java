package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 仓库表实体类
 * 对应表：inventory_warehouse
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inventory_warehouse")
public class InventoryWarehouse extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 仓库ID */
    @TableId(type = IdType.AUTO)
    private Long warehouseId;

    /** 仓库编码 */
    private String warehouseCode;

    /** 仓库名称 */
    private String warehouseName;

    /** 仓库类型：normal普通仓库/quality质检仓库/finished成品仓库/scrap废品仓库 */
    private String warehouseType;

    /** 仓库位置描述 */
    private String location;

    /** 仓库负责人 */
    private String manager;

    /** 联系电话 */
    private String contactPhone;

    /** 排序序号 */
    private Integer sortOrder;

    /** 状态：0正常 1停用 */
    private String status;

}
