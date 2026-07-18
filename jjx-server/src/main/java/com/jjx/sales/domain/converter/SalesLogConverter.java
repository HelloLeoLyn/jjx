package com.jjx.sales.domain.converter;

import com.jjx.sales.domain.entity.SalesLog;
import com.jjx.sales.domain.vo.SalesLogVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface SalesLogConverter {
    SalesLogVO toVO(SalesLog salesLog);
    List<SalesLogVO> toVOList(List<SalesLog> list);
}
