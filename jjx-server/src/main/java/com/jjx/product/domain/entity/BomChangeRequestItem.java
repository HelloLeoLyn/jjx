package com.jjx.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** BOM 变更单明细：一张单打包多张 BOM。 */
@Data
@TableName("bom_change_request_item")
public class BomChangeRequestItem {

    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;
    private Long changeId;
    private Long bomId;
    private String bomVersion;
    private LocalDate targetEffectiveDate;
    /** 提交时逐张校验结果快照（文本）。 */
    private String validateResult;
    private LocalDateTime createdTime;
}
