package com.jjx.product.domain.converter;

import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.vo.ProductVo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProductConverter {
    @Mapping(target = "routeVersion", ignore = true)
    @Mapping(target = "routeName", ignore = true)
    @Mapping(target = "routeCode", ignore = true)
    @Mapping(target = "categoryName", ignore = true)
    @Mapping(target = "bomVersion", ignore = true)
    @Mapping(target = "bomName", ignore = true)
    @Mapping(target = "bomCode", ignore = true)
    ProductVo toVO(Product entity);

    List<ProductVo> toVOList(List<Product> list);
}
