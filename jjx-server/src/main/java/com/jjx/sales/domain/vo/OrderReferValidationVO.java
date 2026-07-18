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
}
