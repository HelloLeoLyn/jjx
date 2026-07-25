package com.jjx.production.domain.converter;

import com.jjx.production.domain.dto.ProductionOrderCreateDTO;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.domain.vo.ProductionOrderVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProductionOrderConverter {

    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "remainingQuantity", ignore = true)
    @Mapping(target = "orderStatus", ignore = true)
    @Mapping(target = "orderId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "completedQuantity", ignore = true)
    @Mapping(target = "approverName", ignore = true)
    @Mapping(target = "approverId", ignore = true)
    @Mapping(target = "approvalTime", ignore = true)
    @Mapping(target = "approvalStatus", ignore = true)
    @Mapping(target = "approvalRemark", ignore = true)
    @Mapping(target = "actualStartTime", ignore = true)
    @Mapping(target = "actualEndTime", ignore = true)
    ProductionOrder toEntity(ProductionOrderCreateDTO createDTO);

    @Mapping(target = "approvalStatusDesc", ignore = true)
    @Mapping(target = "canComplete", ignore = true)
    @Mapping(target = "canStart", ignore = true)
    @Mapping(target = "delayDays", ignore = true)
    @Mapping(target = "estimatedCompletionDate", ignore = true)
    @Mapping(target = "hasEnded", ignore = true)
    @Mapping(target = "hasStarted", ignore = true)
    @Mapping(target = "isOverdue", ignore = true)
    @Mapping(target = "isPlan", ignore = true)
    @Mapping(target = "isWorkOrder", ignore = true)
    @Mapping(target = "operationExecutions", ignore = true)
    @Mapping(target = "operationRecords", ignore = true)
    @Mapping(target = "orderStatusDesc", ignore = true)
    @Mapping(target = "orderTypeDesc", ignore = true)
    @Mapping(target = "parentOrderNo", ignore = true)
    @Mapping(target = "priorityDesc", ignore = true)
    @Mapping(target = "productionEfficiency", ignore = true)
    @Mapping(target = "qualityQualifiedRate", ignore = true)
    @Mapping(target = "routingName", ignore = true)
    ProductionOrderVO toVO(ProductionOrder order);

    List<ProductionOrderVO> toVOList(List<ProductionOrder> list);
}
