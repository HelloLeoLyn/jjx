package com.jjx.sales.domain.converter;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import com.jjx.sales.domain.entity.SalesQuotation;
import com.jjx.sales.domain.dto.SalesQuotationAddDTO;

/**
 * 销售报价单转换器
 * 使用 MapStruct 实现 Entity、DTO、VO 之间的转换
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
        )
public interface SalesQuotationConverter {
    SalesQuotation toEntity(SalesQuotationAddDTO addDTO);
}
