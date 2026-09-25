package com.jjx.quality.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 检验批（IQC/IPQC/FQC 统一模型）—— dev-20260917-001
 *
 * 业务恒等式（每次动作后必须成立）：
 *   lot_quantity = inspected_quantity + 待检余量
 *   inspected_quantity = pass_quantity + fail_quantity
 *   fail_quantity = disposed_quantity + 待处置
 *   stored_quantity <= lot_quantity（累计入库不得超批量）
 */
@Data
@TableName("quality_lot")
public class QualityLot {

    @TableId(type = IdType.AUTO)
    private Long lotId;

    /** 检验批号 */
    private String lotNo;

    /** IQC/IPQC/FQC */
    private String lotType;

    /** INBOUND_ITEM 收货行 / WORK_REPORT 报工批 / EXECUTION 工序 */
    private String sourceType;
    private Long sourceId;
    private Long sourceItemId;

    /** 工单（成品/过程） */
    private Long orderId;
    /** 工序执行 */
    private Long executionId;

    /** 来料：物料 */
    private Long materialId;
    private String materialCode;
    private String materialName;

    /** 成品：产品 */
    private Long productId;
    private String productCode;
    private String productName;

    private String batchNo;

    /** 批量（本批应检总量） */
    private BigDecimal lotQuantity;
    /** 已检数量 */
    private BigDecimal inspectedQuantity;
    /** 合格数量 */
    private BigDecimal passQuantity;
    /** 不良数量 */
    private BigDecimal failQuantity;
    /** 已入库/已放行（防超入校验） */
    private BigDecimal storedQuantity;
    /** 已处置不良数量 */
    private BigDecimal disposedQuantity;

    /** 抽样方案（来料） */
    private Long samplingPlanId;
    private BigDecimal sampleQuantity;
    private BigDecimal acceptNumber;
    private BigDecimal rejectNumber;

    /** pending/pass/fail/concession */
    private String result;
    /** PENDING/INSPECTING/JUDGED/CLOSED */
    private String status;
    /** IQC 独立复核状态：DRAFT/PENDING/APPROVED/REJECTED */
    private String reviewStatus;

    /** 复检来源批 + 版本 */
    private Long parentLotId;
    private Integer version;
    /**
     * 列表展示用的非持久化字段（dev-20260922-012 G5）：
     * parentLotNo = 复检来源批号；superseded = 本批已有后继复检版本（已失效，不可再录入/判定）。
     */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String parentLotNo;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Boolean superseded;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Boolean reworkInspectionBlocked;

    /** 面向用户的业务关联字段（列表/详情展示，不落库）。 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String sourceNo;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String orderNo;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String salesOrderNo;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String processName;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String upstreamSourceNo;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Boolean stockProduction;

    /**
     * 允许动作（唯一出处下发，不落库）—— dev-20260923-039：
     * 由 {@code AllowedActionResolver.forLot(状态, 是否失效, 是否有未处置不良)} 算出，
     * 接口下发后前端只按它渲染按钮，页面不再写状态条件。
     */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private java.util.List<String> allowedActions;

    private String inspector;
    private Long reviewerId;
    private String reviewerName;
    private LocalDateTime reviewTime;
    private String reviewRemark;
    private String defectReason;
    private LocalDateTime inspectTime;
    private String remark;

    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;

    /** 待检余量（不落库，供页面/校验用） */
    public BigDecimal remainingQuantity() {
        BigDecimal lot = lotQuantity == null ? BigDecimal.ZERO : lotQuantity;
        BigDecimal inspected = inspectedQuantity == null ? BigDecimal.ZERO : inspectedQuantity;
        BigDecimal remaining = lot.subtract(inspected);
        return remaining.signum() < 0 ? BigDecimal.ZERO : remaining;
    }
}
