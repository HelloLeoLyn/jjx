package com.jjx.inventory.converter;

import com.jjx.inventory.domain.InventoryStock;
import com.jjx.inventory.dto.vo.StockVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface StockConverter {

    @Mapping(target = "specification", ignore = true)
    @Mapping(target = "unit", ignore = true)
    @Mapping(target = "totalCost", ignore = true)
    @Mapping(target = "avgUnitCost", ignore = true)
    @Mapping(target = "locationCode", ignore = true)
    @Mapping(target = "locationName", ignore = true)
    @Mapping(target = "safeStock", ignore = true)
    @Mapping(target = "maxStock", ignore = true)
    @Mapping(target = "lowStock", ignore = true)
    @Mapping(target = "expiring", ignore = true)
    @Mapping(target = "obsolete", ignore = true)
    @Mapping(target = "daysToExpiry", ignore = true)
    @Mapping(target = "availableQuantity", expression = "java(entity.getTotalQuantity().subtract(entity.getTotalReserved() != null ? entity.getTotalReserved() : java.math.BigDecimal.ZERO))")
    StockVO toVO(InventoryStock entity);

    List<StockVO> toVOList(List<InventoryStock> list);
}
