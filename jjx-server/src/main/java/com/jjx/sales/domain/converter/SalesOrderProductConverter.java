package com.jjx.sales.domain.converter;

import com.jjx.sales.domain.dto.SalesOrderProductDTO;
import com.jjx.sales.domain.entity.SalesOrderProduct;
import com.jjx.sales.domain.vo.SalesOrderProductVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * 订单产品明细转换器
 * 使用 MapStruct 实现 Entity、DTO、VO 之间的转换
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SalesOrderProductConverter {

    /**
     * AddDTO 转 Entity
     * 忽略需要自动生成的字段
     */
    @Mapping(target = "id", ignore = true)
    SalesOrderProduct toEntity(SalesOrderProductDTO addDTO);

    /**
     * Entity 转 VO
     */
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    SalesOrderProductVO toVO(SalesOrderProduct entity);

    /**
     * Entity 列表转 VO 列表
     */
    List<SalesOrderProductVO> toVOList(List<SalesOrderProduct> entityList);

    /**
     * 更新 Entity（从 EditDTO）
     * 使用 @MappingTarget 更新已有对象
     */
    @Mapping(target = "id", source = "orderId")
    void updateEntity(@MappingTarget SalesOrderProduct entity, SalesOrderProductDTO editDTO);


}
