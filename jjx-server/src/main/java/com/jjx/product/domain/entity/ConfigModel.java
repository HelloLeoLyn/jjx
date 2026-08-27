package com.jjx.product.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("product_config_model")
public class ConfigModel {
    private Long modelId;
    private String modelCode;
    private String modelName;
    private Long productId;
    private Integer isDefault;
    private Integer status;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String remark;
}
