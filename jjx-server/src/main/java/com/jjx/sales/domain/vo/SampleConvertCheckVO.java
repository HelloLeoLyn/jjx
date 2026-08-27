package com.jjx.sales.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 转量产就绪检查VO（校验清单：产品/BOM/工艺路线/菲林/资料转移）
 */
@Data
public class SampleConvertCheckVO {

    private Long orderId;
    private String orderNo;
    /** 是否全部就绪（必需项全过） */
    private Boolean allPass;
    /** 校验项列表 */
    private List<CheckItem> items;

    @Data
    public static class CheckItem {
        /** 校验项编码：product/bom/routing/film/transfer */
        private String code;
        /** 展示名 */
        private String name;
        /** 强度：required 必需 / suggest 建议 / info 信息 */
        private String level;
        /** 是否通过 */
        private Boolean pass;
        /** 状态文本：ready/missing/not-published/info */
        private String status;
        /** 说明/缺失原因 */
        private String message;
        /** 关联产品ID（处置跳转用，可空） */
        private Long productId;
        /** 处置动作：edit-product / list-product / transfer / none */
        private String action;
    }
}
