package com.jjx.quality.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 不良件（件级追溯）—— dev-20260924-004。
 *
 * <p>口径（方案 design/defect-piece-trace-reason-code-dev-20260924-004.md v3）：</p>
 * <ul>
 *   <li>一物一号：判定出 N 件不良 → 发 N 个件号 {@code <不良单号>-D<序号>}（不限数量）</li>
 *   <li>件是**处置的最小单位**（一件一个决定）；一件可有多条缺陷记录（件×项目×分级）</li>
 *   <li>件表**只做身份与追溯**，不参与任何库存数量计算（库存仍只由 inventory_transaction 驱动）</li>
 * </ul>
 */
@Data
@TableName("quality_ncr_piece")
public class QualityNcrPiece {

    @TableId(type = IdType.AUTO)
    private Long pieceId;

    /** 件号：<不良单号>-D<序号，3 位补零，超 3 位进位> */
    private String pieceNo;

    private Long ncrId;

    /** 来源检验批 */
    private Long lotId;

    /** 序号（发号幂等键之一：uk_ncr_seq） */
    private Integer seqNo;

    /** 主缺陷检验项目（CR>MA>MI 取最严重，同级取第一个） */
    private String mainCheckItem;

    /** 主缺陷分级：CR/MA/MI */
    private String mainDefectLevel;

    /** 该件实测值（检验单 sample_values 按竖线拆出的第 sampleIndex 段） */
    private String actualValue;

    /** 对应 sample_values 的第几段（1 起） */
    private Integer sampleIndex;

    /** PENDING/REWORKING/RECOVERED/SCRAPPED/CONCEDED/RETURNED/VOID */
    private String status;

    /** 最近处置类型（REWORK/CONCESSION/SCRAP/RETURN，冗余展示） */
    private String disposeType;

    /** 关联处置单行 */
    private Long actionId;

    private Long orderId;

    private String workOrderNo;

    private String lotNo;

    private String remark;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;

    /** 缺陷记录（不落库；详情/列表展示用） */
    @TableField(exist = false)
    private List<QualityNcrPieceDefect> defects;
}
