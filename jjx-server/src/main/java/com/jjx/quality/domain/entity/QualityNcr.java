package com.jjx.quality.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 不良台账（NCR）—— dev-20260917-003
 * 不良数量一律进台账；台账必须能关联 工单 + 工序 + 检验批；
 * 结案口径：不良数量 = 各处置数量之和（返工 + 让步接收 + 报废）。
 */
@Data
@TableName("quality_ncr")
public class QualityNcr {

    @TableId(type = IdType.AUTO)
    private Long ncrId;

    private String ncrNo;
    private Long lotId;
    /** IQC/IPQC/FQC */
    private String lotType;

    private Long orderId;
    private Long executionId;

    private Long materialId;
    private String materialCode;
    private String materialName;

    private Long productId;
    private String productCode;
    private String productName;

    private String batchNo;

    /** 不良数量 */
    private BigDecimal defectQuantity;
    private BigDecimal crQuantity;
    private BigDecimal maQuantity;
    private BigDecimal miQuantity;

    private String defectReason;
    /** PENDING/DISPOSING/CLOSED */
    private String status;
    /** 已处置数量 */
    private BigDecimal disposedQuantity;
    /** 报废金额（预留，口径A暂不启用） */
    private BigDecimal scrappedAmount;

    private String inspector;
    private String remark;

    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;

    /** 待处置数量（不落库） */
    public BigDecimal pendingQuantity() {
        BigDecimal defect = defectQuantity == null ? BigDecimal.ZERO : defectQuantity;
        BigDecimal disposed = disposedQuantity == null ? BigDecimal.ZERO : disposedQuantity;
        BigDecimal pending = defect.subtract(disposed);
        return pending.signum() < 0 ? BigDecimal.ZERO : pending;
    }
}
