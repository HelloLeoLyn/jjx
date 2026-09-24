package com.jjx.quality.dto.vo;

import com.jjx.quality.domain.entity.QualityNcrPieceDefect;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 不良件（含缺陷记录）展示对象 —— dev-20260924-004。
 */
@Data
@Schema(description = "不良件（件级追溯）")
public class QualityNcrPieceVO {

    private Long pieceId;

    /** 件号：<不良单号>-D<序号> */
    private String pieceNo;

    private Integer seqNo;

    /** 主缺陷检验项目 */
    private String mainCheckItem;

    /** 主缺陷分级 CR/MA/MI */
    private String mainDefectLevel;

    /** 该件实测值 */
    private String actualValue;

    private Integer sampleIndex;

    /** PENDING/REWORKING/RECOVERED/SCRAPPED/CONCEDED/RETURNED/VOID */
    private String status;

    private String statusLabel;

    private String disposeType;

    private Long actionId;

    private String workOrderNo;

    private String lotNo;

    private String remark;

    /** 全部缺陷记录（件×项目×分级，一件可多条） */
    private List<QualityNcrPieceDefect> defects;
}
