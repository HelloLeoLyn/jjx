package com.jjx.purchase.converter;

import com.jjx.purchase.domain.dto.PurchaseSupplierDTO;
import com.jjx.purchase.domain.dto.SupplierEvaluationDTO;
import com.jjx.purchase.domain.entity.PurchaseSupplier;
import com.jjx.purchase.domain.vo.PurchaseSupplierVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SupplierConverter {
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "taxNumber", ignore = true)
    @Mapping(target = "supplierType", ignore = true)
    @Mapping(target = "supplierName", ignore = true)
    @Mapping(target = "supplierCode", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "remark", ignore = true)
    @Mapping(target = "phone", ignore = true)
    @Mapping(target = "paymentTerms", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "delFlag", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "contactPerson", ignore = true)
    @Mapping(target = "bankAccount", ignore = true)
    @Mapping(target = "address", ignore = true)
    PurchaseSupplier toEntity(SupplierEvaluationDTO dto);

    @Mapping(target = "supplierTypeName", ignore = true)
    @Mapping(target = "statusName", ignore = true)
    PurchaseSupplierVO toVO(PurchaseSupplier entity);

    List<PurchaseSupplierVO> toVOList(List<PurchaseSupplier> list);

    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "delFlag", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    PurchaseSupplier toEntity(PurchaseSupplierDTO dto);
}
