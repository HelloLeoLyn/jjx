package com.jjx.sales.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 打样不良原因及改善记录（dev-20261010-028）
 * 样品单级、多条；回填 QR-065 样品需求单的「印刷制样记录 / 加工冲型制样记录」两栏。
 */
@Data
@TableName("sales_sample_defect_record")
public class SalesSampleDefectRecord {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 样品单ID(sales_order.order_id) */
    private Long sampleOrderId;

    /** 轮次 */
    private Integer roundNo;

    /** 制样类别: PRINT印刷/PUNCH加工冲型 */
    private String craftType;

    /** 不良原因 */
    private String defectReason;

    /** 改善措施 */
    private String improvement;

    /** 记录人ID */
    private Long recorderId;

    /** 记录人姓名 */
    private String recorderName;

    /** 记录日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate recordDate;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}
