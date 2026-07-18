package com.jjx.product.domain.converter;

import com.jjx.product.domain.entity.ProductStandardProcess;
import com.jjx.product.domain.vo.ProductStandardProcessVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
       )
public interface ProductStandardProcessConverter {
    ProductStandardProcessVO toVO(ProductStandardProcess process);

    List<ProductStandardProcessVO> toVOList(List<ProductStandardProcess> list);
}
