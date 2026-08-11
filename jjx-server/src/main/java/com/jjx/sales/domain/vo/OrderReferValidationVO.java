package com.jjx.sales.domain.vo;

import com.jjx.product.domain.vo.ProductValidationVO;
import lombok.Data;

import java.util.List;

@Data
public class OrderReferValidationVO {
    private Long orderId;
    private String orderNo;
    private CustomerVO customerVO;
    private List<ProductValidationVO> items;

    /** 是否可以提交审核（2026-08-11 新增） */
    private Boolean canSubmit;

    /** 错误数 */
    private Integer errorCount;

    /** 警告数 */
    private Integer warningCount;

    /** 提示数 */
    private Integer infoCount;
}
