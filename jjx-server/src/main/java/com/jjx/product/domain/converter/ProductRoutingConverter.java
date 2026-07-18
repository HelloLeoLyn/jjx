package com.jjx.product.domain.converter;

import com.jjx.common.enums.ApproveStatusEnum;
import com.jjx.product.domain.dto.ProductRoutingDTO;
import com.jjx.product.domain.entity.ProductRouting;
import com.jjx.product.domain.vo.ProductRoutingVO;
import com.jjx.product.enums.ProcessCategoryEnum;
import com.jjx.product.enums.ProcessTypeEnum;
import com.jjx.product.enums.ProductEnums;
import org.mapstruct.*;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 产品工艺路线转换器
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {LocalDateTime.class, ApproveStatusEnum.class, ProcessTypeEnum.class, ProcessCategoryEnum.class, ProductEnums.class})
@Component
public interface ProductRoutingConverter {

    // ==================== Entity ↔ DTO ====================

    /**
     * DTO 转 Entity
     */
    @Mapping(target = "routingId", ignore = true)
    @Mapping(target = "isCurrent", ignore = true)
    @Mapping(target = "approveStatus", ignore = true)
    @Mapping(target = "totalLaborHours", ignore = true)
    @Mapping(target = "totalMachineHours", ignore = true)
    @Mapping(target = "processCount", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "items", ignore = true)
    ProductRouting toEntity(ProductRoutingDTO dto);

    /**
     * Entity 转 DTO
     */
    ProductRoutingDTO toDTO(ProductRouting entity);

    /**
     * 更新 Entity（从 DTO）
     */
    @Mapping(target = "routingId", ignore = true)
    @Mapping(target = "isCurrent", ignore = true)
    @Mapping(target = "approveStatus", ignore = true)
    @Mapping(target = "totalLaborHours", ignore = true)
    @Mapping(target = "totalMachineHours", ignore = true)
    @Mapping(target = "processCount", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateEntity(ProductRoutingDTO dto, @MappingTarget ProductRouting entity);

    // ==================== Entity ↔ VO ====================

    /**
     * Entity 转 VO
     */
    @Mapping(target = "isCurrentName", expression = "java(entity.getIsCurrent() != null && entity.getIsCurrent() == 1 ? \"是\" : \"否\")")
    ProductRoutingVO toVO(ProductRouting entity);

    /**
     * VO 转 Entity
     */
    ProductRouting toEntity(ProductRoutingVO vo);

    /**
     * Entity 列表转 VO 列表
     */
    List<ProductRoutingVO> toVOList(List<ProductRouting> entities);

}
