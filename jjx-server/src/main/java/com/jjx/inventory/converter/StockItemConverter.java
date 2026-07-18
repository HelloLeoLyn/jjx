package com.jjx.inventory.converter;

import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.dto.vo.StockItemVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface StockItemConverter {

    @Mapping(target = "specification", ignore = true)
    @Mapping(target = "unit", ignore = true)
    @Mapping(target = "warehouseCode", ignore = true)
    @Mapping(target = "warehouseName", ignore = true)
    @Mapping(target = "locationCode", ignore = true)
    @Mapping(target = "locationName", ignore = true)
    @Mapping(target = "availableQuantity", expression = "java(entity.getQuantity().subtract(entity.getReservedQuantity() != null ? entity.getReservedQuantity() : java.math.BigDecimal.ZERO))")
    @Mapping(target = "statusName", ignore = true)
    StockItemVO toVO(InventoryStockItem entity);

    List<StockItemVO> toVOList(List<InventoryStockItem> list);
}
