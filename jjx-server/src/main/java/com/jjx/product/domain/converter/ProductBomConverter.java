package com.jjx.product.domain.converter;

import com.jjx.product.domain.dto.ProductBomDTO;
import com.jjx.product.domain.entity.ProductBom;
import com.jjx.product.domain.vo.ProductBomVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProductBomConverter {
    @Mapping(target = "productName", ignore = true)
    @Mapping(target = "productCode", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "bomType", ignore = true)
    ProductBomVO toVO(ProductBom entity);

    List<ProductBomVO> toVOList(List<ProductBom> list);

    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "approveRemark", ignore = true)
    @Mapping(target = "bomType", ignore = true)
    ProductBom toEntity(ProductBomDTO dto);
}
