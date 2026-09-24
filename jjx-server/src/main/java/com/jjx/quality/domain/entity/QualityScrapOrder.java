package com.jjx.quality.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成品报废单 —— dev-20260924-006（报废线三期）。
 *
 * <p>形态对齐来料侧 {@code inventory_iqc_scrap_order}（单号/数量/原因/申请人/审批人/状态），
 * 补齐成品报废的**实物凭据**：谁报废、报废多少、哪几件（件号区间）、谁批的。</p>
 *
 * <p>口径：报废单是凭证，**库存不受影响**（口径A：不良品从未进良品库）；一笔报废处置只出一张单
 * （uk_scrap_action 幂等）；报废被受控撤销时单据置 VOID。</p>
 */
@Data
@TableName("quality_scrap_order")
public class QualityScrapOrder {

    @TableId(type = IdType.AUTO)
    private Long scrapId;

    /** 报废单号：SCR + yyMMdd + 3 位 */
    private String scrapNo;

    /** 来源处置单（幂等键） */
    private Long actionId;

    private Long ncrId;

    private String ncrNo;

    private Long lotId;

    private String lotNo;

    private Long orderId;

    private String orderNo;

    private String productCode;

    private String productName;

    private String batchNo;

    /** 报废数量 */
    private BigDecimal quantity;

    /** 主缺陷检验项目（原因口径：检验项目 + 分级） */
    private String defectItem;

    /** 主缺陷分级 CR/MA/MI */
    private String defectLevel;

    /** 件号区间（件级追溯，如 NCR260924001-D001~D002） */
    private String pieceRange;

    /** 报废原因说明 */
    private String reason;

    /** DONE 已生效 / VOID 已撤销 */
    private String status;

    /** 提交人 */
    private String applicant;

    /** 审批人（超阈值报废才有） */
    private String approver;

    private LocalDateTime approveTime;

    private String remark;

    // ==================== 损失金额（dev-20260924-010：材料/工时分开 + 口径快照） ====================

    /** 材料损失金额 = 报废数量 × 单位材料标准成本 */
    private BigDecimal lossMaterial;

    /** 工时损失金额 = 报废数量 × 单位工时标准成本 */
    private BigDecimal lossLabor;

    /** 损失合计 = 材料 + 工时 */
    private BigDecimal lossTotal;

    /** 损失口径快照：单价来源/工价/无价与工时缺失项（可审计） */
    private String lossBasis;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}
