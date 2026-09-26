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

    /** 展示用：检验批号（不落库）—— dev-20260923-036（台账不再显示「批 #11」这种裸 ID） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String lotNo;

    /** 展示用：工单号（不落库）—— dev-20260923-036 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String orderNo;

    /** 展示用：来源批是否已被后继复检版本取代（不落库）—— dev-20260923-036 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Boolean lotSuperseded;

    /** 允许动作（唯一出处下发，不落库）—— dev-20260923-039：前端只按它渲染按钮 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private java.util.List<String> allowedActions;

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

    /** 不良原因（文本补充说明；结构化原因见 mainCheckItem + mainDefectLevel 与件级缺陷记录） */
    private String defectReason;

    /** dev-20260924-004：首因检验项目（来自检验单项目，台账按 项目×分级 筛选统计） */
    private String mainCheckItem;

    /** dev-20260924-004：首因分级 CR/MA/MI（CR 致命 > MA 严重 > MI 轻微） */
    private String mainDefectLevel;

    /** PENDING/DISPOSING/CLOSED */
    private String status;
    /** 已处置数量 */
    private BigDecimal disposedQuantity;
    /** 报废金额（预留，口径A暂不启用） */
    private BigDecimal scrappedAmount;

    /** Non-persistent replacement accounting for the production supplement picker. */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private BigDecimal completedScrapQuantity;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private BigDecimal requestedReplacementQuantity;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private BigDecimal remainingReplacementQuantity;

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
