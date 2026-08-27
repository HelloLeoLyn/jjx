package com.jjx.sales.domain.dto;

import lombok.Data;

/**
 * 转量产产品标准化项（DEV-xxx 标准化窗口）
 * 样品阶段产品为临时/描述数据，转量产时允许逐条标准化为正式产品
 */
@Data
public class SampleConvertItemDTO {

    /** 样品单明细ID（sales_order_product.id） */
    private Long orderProductId;

    /** 标准化后的正式产品ID（须已建档且已发布） */
    private Long productId;
}
