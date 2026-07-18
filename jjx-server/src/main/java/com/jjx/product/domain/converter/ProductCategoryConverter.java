package com.jjx.product.domain.converter;

import com.jjx.product.domain.entity.ProductCategory;
import com.jjx.product.domain.vo.ProductCategoryVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProductCategoryConverter {
    ProductCategoryVO toVO(ProductCategory entity);

    List<ProductCategoryVO> toVOList(List<ProductCategory> list);
}
