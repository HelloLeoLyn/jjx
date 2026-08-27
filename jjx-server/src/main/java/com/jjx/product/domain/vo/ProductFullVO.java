package com.jjx.product.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class ProductFullVO {
    private ProductVo product;
    private EngineeringBomVO bom;
    private EngineeringRoutingVO routing;
    private ProductCategoryVO category;
    private List<EngineeringFilmVO> films;
}
