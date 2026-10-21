package com.jjx.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 全公司工序使用量，按 BOM 创建日分桶，可由 BOM 明细重算。 */
@Data
@TableName("engineering_standard_process_usage")
public class StandardProcessUsage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long processId;
    private LocalDate periodDate;
    private Long useCount;
    private LocalDateTime updateTime;
}
