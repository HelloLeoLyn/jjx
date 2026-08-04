package com.jjx.product.domain.converter;

import com.jjx.product.domain.dto.EngineeringBomDTO;
import com.jjx.product.domain.entity.EngineeringBom;
import com.jjx.product.domain.vo.EngineeringBomVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface EngineeringBomConverter {
    @Mapping(target = "productName", ignore = true)
    @Mapping(target = "productCode", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "bomType", ignore = true)
    EngineeringBomVO toVO(EngineeringBom entity);

    List<EngineeringBomVO> toVOList(List<EngineeringBom> list);

    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "approveRemark", ignore = true)
    @Mapping(target = "bomType", ignore = true)
    EngineeringBom toEntity(EngineeringBomDTO dto);
}
