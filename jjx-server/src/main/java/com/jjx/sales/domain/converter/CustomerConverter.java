package com.jjx.sales.domain.converter;

import com.jjx.sales.domain.dto.CustomerAddDTO;
import com.jjx.sales.domain.dto.CustomerEditDTO;
import com.jjx.sales.domain.entity.SalesCustomer;
import com.jjx.sales.domain.vo.CustomerVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.WARN)
public interface CustomerConverter {

    CustomerVO toVO(SalesCustomer entity);

    List<CustomerVO> toVOList(List<SalesCustomer> list);


    @Mapping(target = "website", ignore = true)
    @Mapping(target = "usedCreditLimit", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "unifiedSocialCreditCode", ignore = true)
    @Mapping(target = "taxpayerId", ignore = true)
    @Mapping(target = "specialRequirements", ignore = true)
    @Mapping(target = "salesManagerName", ignore = true)
    @Mapping(target = "salesManagerId", ignore = true)
    @Mapping(target = "paymentTerms", ignore = true)
    @Mapping(target = "params", ignore = true)
    @Mapping(target = "mainProductDemand", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "customerTags", ignore = true)
    @Mapping(target = "customerStatus", ignore = true)
    @Mapping(target = "customerId", ignore = true)
    @Mapping(target = "customerCode", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "cooperationStartDate", ignore = true)
    @Mapping(target = "cooperationEndDate", ignore = true)
    @Mapping(target = "bankName", ignore = true)
    @Mapping(target = "bankAccount", ignore = true)
    @Mapping(target = "attachments", ignore = true)
    @Mapping(target = "annualPurchaseAmount", ignore = true)
    SalesCustomer toEntity(CustomerAddDTO dto);


    @Mapping(target = "website", ignore = true)
    @Mapping(target = "usedCreditLimit", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "unifiedSocialCreditCode", ignore = true)
    @Mapping(target = "taxpayerId", ignore = true)
    @Mapping(target = "specialRequirements", ignore = true)
    @Mapping(target = "salesManagerName", ignore = true)
    @Mapping(target = "salesManagerId", ignore = true)
    @Mapping(target = "paymentTerms", ignore = true)
    @Mapping(target = "params", ignore = true)
    @Mapping(target = "mainProductDemand", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "customerTags", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "cooperationStartDate", ignore = true)
    @Mapping(target = "cooperationEndDate", ignore = true)
    @Mapping(target = "bankName", ignore = true)
    @Mapping(target = "bankAccount", ignore = true)
    @Mapping(target = "attachments", ignore = true)
    @Mapping(target = "annualPurchaseAmount", ignore = true)
    SalesCustomer toEntity(CustomerEditDTO dto);
}
