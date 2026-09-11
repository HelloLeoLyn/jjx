package com.jjx.engineering.archive;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("engineering_process_icon_sample")
public class ProcessIconSample {
    @TableId(type = IdType.AUTO)
    private Long sampleId;
    private Long processId;
    private Long archiveId;
    private String workflowType;
    private Integer stepNo;
    private String originalPath;
    private String normalizedPath;
    private String perceptualHash;
    private BigDecimal matchScore;
    private Integer confirmStatus;
    private Integer usageCount;
    private Integer useAsSystemIcon;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    @TableField(exist = false)
    private String previewBase64;
}
