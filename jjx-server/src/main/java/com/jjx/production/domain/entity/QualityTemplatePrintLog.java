package com.jjx.production.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("quality_template_print_log")
public class QualityTemplatePrintLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private String recordNo;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime printTime;
}
