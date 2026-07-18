package com.jjx.inventory.converter;

import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.dto.vo.MaterialVO;
import org.mapstruct.Mapper;
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
    @Mapping(target = "categoryName", ignore = true)
    @Mapping(target = "availableStock", ignore = true)
    MaterialVO toVO(InventoryMaterial entity);

    List<MaterialVO> toVOList(List<InventoryMaterial> list);
}
