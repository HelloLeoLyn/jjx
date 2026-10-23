package com.jjx.inventory.master;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("inventory_attribute_template")
public class InventoryAttributeTemplate {
    @TableId(type = IdType.AUTO)
    private Long templateId;
    private String templateCode;
    private String templateName;
    private Long categoryId;
    private String attributeSchema;
    private Integer status;
    private String createBy;
    private String updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
