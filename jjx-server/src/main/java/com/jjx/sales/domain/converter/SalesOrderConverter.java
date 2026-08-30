package com.jjx.sales.domain.converter;

import com.jjx.common.enums.YesNoEnum;
import com.jjx.sales.domain.dto.SalesOrderAddDTO;
import com.jjx.sales.domain.dto.SalesOrderEditDTO;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.vo.SalesOrderVO;
import com.jjx.sales.enums.SalesOrderStatusEnum;
import com.jjx.sales.enums.SalesOrderTypeEnum;
import com.jjx.sales.enums.SalesPaymentStatusEnum;
import com.jjx.sales.enums.ProdStatusEnum;
import org.mapstruct.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 销售订单转换器
 * 使用 MapStruct 实现 Entity、DTO、VO 之间的转换
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
        )
public interface SalesOrderConverter {

    /**
     * AddDTO 转 Entity
     * 忽略需要自动生成的字段
     */
    @Mapping(target = "orderId", ignore = true)
    @Mapping(target = "orderNo", ignore = true)
    @Mapping(target = "orderStatus", ignore = true)
    @Mapping(target = "prodStatus", ignore = true)
    @Mapping(target = "paymentStatus", ignore = true)
    @Mapping(target = "shippedQuantity", ignore = true)
    @Mapping(target = "producedQuantity", ignore = true)
    @Mapping(target = "paidAmount", ignore = true)
    @Mapping(target = "unpaidAmount", ignore = true)
    @Mapping(target = "totalAmountWithTax", ignore = true)
    @Mapping(target = "finalAmount", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "remark", source = "remark")
    SalesOrder toEntity(SalesOrderAddDTO addDTO);

    /**
     * EditDTO 转 Entity
     * 忽略不需要更新的字段
     */
    @Mapping(target = "orderNo", ignore = true)
    @Mapping(target = "orderStatus", ignore = true)
    @Mapping(target = "prodStatus", ignore = true)
    @Mapping(target = "paymentStatus", ignore = true)
    @Mapping(target = "shippedQuantity", ignore = true)
    @Mapping(target = "producedQuantity", ignore = true)
    @Mapping(target = "paidAmount", ignore = true)
    @Mapping(target = "unpaidAmount", ignore = true)
    @Mapping(target = "taxAmount", ignore = true)
    @Mapping(target = "totalAmountWithTax", ignore = true)
    @Mapping(target = "discountAmount", ignore = true)
    @Mapping(target = "finalAmount", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    SalesOrder toEntity(SalesOrderEditDTO editDTO);

    /**
     * Entity 转 VO
     * 添加枚举描述字段
     */
    @Mapping(target = "orderTypeDesc", expression = "java(getOrderTypeDesc(entity.getOrderType()))")
    @Mapping(target = "orderStatusDesc", expression = "java(getOrderStatusDesc(entity.getOrderStatus()))")
    @Mapping(target = "prodStatusDesc", expression = "java(getProdStatusDesc(entity.getProdStatus()))")
    @Mapping(target = "paymentStatusDesc", expression = "java(getPaymentStatusDesc(entity.getPaymentStatus()))")
    @Mapping(target = "isUrgentDesc", expression = "java(getYesNoDesc(entity.getIsUrgent()))")
    SalesOrderVO toVO(SalesOrder entity);

    /**
     * Entity 列表转 VO 列表
     */
    List<SalesOrderVO> toVOList(List<SalesOrder> entityList);

    /**
     * 更新 Entity（从 EditDTO）
     * 使用 @MappingTarget 更新已有对象
     */
    @Mapping(target = "orderId", ignore = true)
    @Mapping(target = "orderNo", ignore = true)
    @Mapping(target = "orderStatus", ignore = true)
    @Mapping(target = "prodStatus", ignore = true)
    @Mapping(target = "paymentStatus", ignore = true)
    @Mapping(target = "shippedQuantity", ignore = true)
    @Mapping(target = "producedQuantity", ignore = true)
    @Mapping(target = "paidAmount", ignore = true)
    @Mapping(target = "unpaidAmount", ignore = true)
    @Mapping(target = "taxAmount", ignore = true)
    @Mapping(target = "totalAmountWithTax", ignore = true)
    @Mapping(target = "discountAmount", ignore = true)
    @Mapping(target = "finalAmount", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    void updateEntity(@MappingTarget SalesOrder entity, SalesOrderEditDTO editDTO);

    /**
     * 批量更新金额字段后的 Entity（用于计算完成后）
     */
    @AfterMapping
    default void afterToEntity(@MappingTarget SalesOrder entity) {
        if (entity != null) {
            // 设置默认状态
            if (entity.getOrderStatus() == null) {
                entity.setOrderStatus(SalesOrderStatusEnum.DRAFT.getValue());
            }
            if (entity.getProdStatus() == null) {
                entity.setProdStatus(ProdStatusEnum.NONE.getValue());
            }
            if (entity.getPaymentStatus() == null) {
                entity.setPaymentStatus(SalesPaymentStatusEnum.UNPAID.getValue());
            }
            if (entity.getShippedQuantity() == null) {
                entity.setShippedQuantity(0);
            }
            if (entity.getProducedQuantity() == null) {
                entity.setProducedQuantity(0);
            }
            if (entity.getPaidAmount() == null) {
                entity.setPaidAmount(BigDecimal.ZERO);
            }
            
            // 计算金额字段
            SalesOrderCalculator.fillOrderAmounts(entity);
            
            // 设置未付金额
            if (entity.getUnpaidAmount() == null && entity.getFinalAmount() != null) {
                entity.setUnpaidAmount(entity.getFinalAmount());
            }
        }
    }

    /**
     * 更新后处理
     */
    @AfterMapping
    default void afterUpdateEntity(@MappingTarget SalesOrder entity, SalesOrderEditDTO editDTO) {
        if (entity != null) {
            // 重新计算金额
            SalesOrderCalculator.fillOrderAmounts(entity);
            
            // 重新计算未付金额
            if (entity.getFinalAmount() != null && entity.getPaidAmount() != null) {
                entity.setUnpaidAmount(entity.getFinalAmount().subtract(entity.getPaidAmount()));
            }
        }
    }

    /**
     * 获取订单类型描述
     */
    default String getOrderTypeDesc(Integer orderType) {
        SalesOrderTypeEnum enumValue = SalesOrderTypeEnum.getByCode(orderType);
        return enumValue != null ? enumValue.getDesc() : "";
    }

    /**
     * 获取订单状态描述
     */
    default String getOrderStatusDesc(Integer orderStatus) {
        SalesOrderStatusEnum enumValue = SalesOrderStatusEnum.getByValue(orderStatus);
        return enumValue.getDescription();
    }

    /**
     * 获取生产状态描述
     */
    default String getProdStatusDesc(Integer prodStatus) {
        ProdStatusEnum enumValue = ProdStatusEnum.getByValue(prodStatus);
        return enumValue != null ? enumValue.getLabel() : "";
    }

    /**
     * 获取支付状态描述
     */
    default String getPaymentStatusDesc(Integer paymentStatus) {
        SalesPaymentStatusEnum enumValue = SalesPaymentStatusEnum.getByValue(paymentStatus);
        return enumValue != null ? enumValue.getLabel() : "";
    }

    /**
     * 获取是否描述
     */
    default String getYesNoDesc(Integer isUrgent) {
        YesNoEnum enumValue = YesNoEnum.getByCode(isUrgent);
        return enumValue != null ? enumValue.getDesc() : "";
    }
}