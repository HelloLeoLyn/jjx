package com.jjx.inventory.converter;

import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.dto.vo.MaterialVO;
import org.mapstruct.Mapper;
import org.mapstruct.Context;
import java.util.Map;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MaterialConverter {
    @Mapping(target = "updateByName", ignore = true)
    @Mapping(target = "reservedStock", ignore = true)
    @Mapping(target = "inTransitStock", ignore = true)
    @Mapping(target = "defaultWarehouseName", ignore = true)
    @Mapping(target = "defaultLocationName", ignore = true)
    @Mapping(target = "currentStock", ignore = true)
    @Mapping(target = "createByName", ignore = true)
    @Mapping(target = "categoryName", expression = "java(categoryNames.get(entity.getCategoryId()))")
    @Mapping(target = "availableStock", ignore = true)
    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "templateId", source = "templateId")
    @Mapping(target = "thickness", source = "thickness")
    @Mapping(target = "widthSize", source = "widthSize")
    MaterialVO toVO(InventoryMaterial entity, @Context Map<Long, String> categoryNames);

    List<MaterialVO> toVOList(List<InventoryMaterial> list, @Context Map<Long, String> categoryNames);
}
