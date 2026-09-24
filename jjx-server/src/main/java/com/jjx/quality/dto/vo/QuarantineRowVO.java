package com.jjx.quality.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 隔离台账行（dev-20260924-007 一期）——「在隔离的货」的可查视图。
 *
 * <p>口径：一期只做**标识**，不动库存（库存仍只由 inventory_transaction 驱动，且只装良品）。
 * 隔离量 = 未处置不良（不良单 defect_quantity − disposed_quantity）；
 * 件级：不良件状态 PENDING（待处置）= 隔离中。真隔离库位/隔离仓另议。</p>
 */
@Data
@Schema(description = "隔离台账行：未处置不良（隔离中）")
public class QuarantineRowVO {

    private Long ncrId;

    private String ncrNo;

    /** IQC / FQC */
    private String lotType;

    private Long lotId;

    private String lotNo;

    private Long orderId;

    private String orderNo;

    private String materialCode;

    private String materialName;

    private String productCode;

    private String productName;

    private String batchNo;

    /** 不良数量 */
    private BigDecimal defectQuantity;

    /** 已处置数量 */
    private BigDecimal disposedQuantity;

    /** 隔离量（= 不良 − 已处置） */
    private BigDecimal quarantineQuantity;

    /** 首因检验项目 */
    private String mainCheckItem;

    /** 首因分级 CR/MA/MI */
    private String mainDefectLevel;

    /** 不良单状态（PENDING/DISPOSING） */
    private String status;

    /** 不良件总数（件级追溯，dev-20260924-004） */
    private Integer pieceTotal;

    /** 待处置（隔离中）件数 */
    private Integer piecePending;
}
