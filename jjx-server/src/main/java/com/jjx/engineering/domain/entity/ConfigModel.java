package com.jjx.engineering.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("engineering_config_model")
public class ConfigModel {
    @TableId(type = IdType.AUTO)
    private Long modelId;
    private String modelCode;
    private String modelName;
    private Long productId;
    private Integer isDefault;
    private String status;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
