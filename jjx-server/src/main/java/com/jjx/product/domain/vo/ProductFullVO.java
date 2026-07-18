package com.jjx.product.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class ProductFullVO {
    private ProductVo product;
    private ProductBomVO bom;
    private ProductRoutingVO routing;
    private ProductCategoryVO category;
    private List<ProductFilmVO> films;
}
