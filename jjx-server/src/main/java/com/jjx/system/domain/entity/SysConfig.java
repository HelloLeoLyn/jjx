package com.jjx.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_config")
public class SysConfig {
    /** 配置ID */
    @TableId(type = IdType.AUTO)
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
