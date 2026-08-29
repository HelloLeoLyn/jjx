package com.jjx.product.domain.vo;

import lombok.Data;

/**
 * 产品编辑结果 VO：承载变更明细（供 @Log detail 取值）
 */
@Data
public class ProductEditVO {
    private boolean success;
    private String detailMessage;
}
