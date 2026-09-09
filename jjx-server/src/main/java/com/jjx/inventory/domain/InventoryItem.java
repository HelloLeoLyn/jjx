package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 材料、产品进入库存域后的统一身份。 */
@Data
@TableName("inventory_item")
public class InventoryItem implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long inventoryItemId;
    private String itemType;
    private Long sourceId;
    private String itemCode;
    private String itemName;
    private String specification;
    private String unit;
    private Integer batchManaged;
    private Integer locationManaged;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
