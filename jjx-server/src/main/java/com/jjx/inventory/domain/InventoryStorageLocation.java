package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库位表实体类
 * 对应表：inventory_storage_location
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inventory_storage_location")
public class InventoryStorageLocation extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 库位ID */
    @TableId(type = IdType.AUTO)
    private Long locationId;

    /** 所属仓库ID */
    private Long warehouseId;

    /** 库位编码 */
    private String locationCode;

    /** 库位名称 */
    private String locationName;

    /** 库位类型：normal普通/frozen冷冻/flammable易燃/valuable贵重 */
    private String locationType;

    /** 最大容量（按基本单位） */
    private BigDecimal capacity;

    /** 已使用容量 */
    private BigDecimal usedCapacity;

    /** 宽度(cm) */
    private BigDecimal width;

    /** 高度(cm) */
    private BigDecimal height;

    /** 深度(cm) */
    private BigDecimal depth;

    /** 排序序号 */
    private Integer sortOrder;

    /** 状态：0正常 1停用 */
    private String status;

}
