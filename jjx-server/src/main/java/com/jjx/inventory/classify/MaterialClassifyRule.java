package com.jjx.inventory.classify;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("inventory_material_classify_rule")
public class MaterialClassifyRule {
    public enum MatchField { NAME, SPEC, ANY }
    @TableId(type = IdType.AUTO)
    private Long id;
    private String keyword;
    private MatchField matchField;
    private String categoryCode;
    private Integer priority;
    private Boolean enabled;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
