package com.jjx.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_config")
public class SysConfig {
    private Long configId;
    private String configKey;
    private String configValue;
    private String configName;
    private String configGroup;
    private String remark;
    private Integer sortOrder;
    private Integer isActive;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
