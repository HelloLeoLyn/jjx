package com.jjx.quality.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 不良件缺陷记录（件 × 检验项目 × 不合格分级）—— dev-20260924-004。
 *
 * <p>一件可有多条（一件同时外观+尺寸不合格 → 2 条）；处置仍按件（一件一个决定），
 * 统计按缺陷记录数（Pareto 不被"只记首因"埋掉）。对齐 SAP QM「缺陷按检验特性分别记录」。</p>
 */
@Data
@TableName("quality_ncr_piece_defect")
public class QualityNcrPieceDefect {

    @TableId(type = IdType.AUTO)
    private Long defectId;

    private Long pieceId;

    private Long ncrId;

    private Long lotId;

    /** 不合格检验项目（来自 quality_lot_item.check_item） */
    private String checkItem;

    /** 不合格分级：CR/MA/MI（兜底「其他」时可为空） */
    private String defectLevel;

    /** 是否该件主缺陷（每件恰有一条 1） */
    private Integer isMain;

    private Integer sortOrder;

    private String remark;

    private LocalDateTime createTime;

    /** 分级排序用（不落库）：CR=3 / MA=2 / MI=1 / 空=0 */
    public static int severity(String level) {
        if (level == null) {
            return 0;
        }
        return switch (level.trim().toUpperCase()) {
            case "CR" -> 3;
            case "MA" -> 2;
            case "MI" -> 1;
            default -> 0;
        };
    }
}
