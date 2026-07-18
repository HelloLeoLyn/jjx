package com.jjx.purchase.converter;

import com.jjx.purchase.domain.dto.PurchaseOrderDTO;
import com.jjx.purchase.domain.dto.PurchaseOrderItemDTO;
import com.jjx.purchase.domain.entity.PurchaseOrder;
import com.jjx.purchase.domain.entity.PurchaseOrderItem;
import com.jjx.purchase.domain.enums.ApprovalStatusEnum;
import com.jjx.purchase.domain.enums.PaymentStatusEnum;
import com.jjx.purchase.domain.enums.ReceiptStatusEnum;
import com.jjx.purchase.domain.vo.PurchaseOrderExportVO;
import com.jjx.purchase.domain.vo.PurchaseOrderItemExportVO;
import com.jjx.purchase.domain.vo.PurchaseOrderItemVO;
import com.jjx.purchase.domain.vo.PurchaseOrderVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * 采购订单转换器
 * 用于实体、DTO、VO之间的转换
 */
@Mapper(componentModel = "spring")
public interface PurchaseConverter {

    // ==================== DTO → Entity ====================

    /**
     * PurchaseOrderDTO → PurchaseOrder
     */
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    PurchaseOrder toEntity(PurchaseOrderDTO dto);

    /**
     * PurchaseOrderItemDTO → PurchaseOrderItem
     */
    @Mapping(target = "itemId", ignore = true)
    @Mapping(target = "orderId", ignore = true)
    @Mapping(target = "itemOrder", ignore = true)
    @Mapping(target = "receivedQuantity", ignore = true)
    @Mapping(target = "receiptStatus", ignore = true)
    @Mapping(target = "inquiryInfo", ignore = true)
    @Mapping(target = "inquiryStatus", ignore = true)
    @Mapping(target = "batchNo", ignore = true)
    @Mapping(target = "productionDate", ignore = true)
    @Mapping(target = "expiryDate", ignore = true)
    @Mapping(target = "inspectionResult", ignore = true)
    @Mapping(target = "inspectionRemark", ignore = true)
    PurchaseOrderItem toEntity(PurchaseOrderItemDTO dto);

    // ==================== Entity → VO ====================

    /**
     * PurchaseOrder → PurchaseOrderVO
     */
    @Mapping(target = "orderTypeName", source = "orderType", qualifiedByName = "orderTypeToName")
    @Mapping(target = "approvalStatusName", source = "approvalStatus", qualifiedByName = "approvalStatusToName")
    @Mapping(target = "receiptStatusName", source = "receiptStatus", qualifiedByName = "receiptStatusToName")
    @Mapping(target = "paymentStatusName", source = "paymentStatus", qualifiedByName = "paymentStatusToName")
    @Mapping(target = "items", ignore = true)
    PurchaseOrderVO toVO(PurchaseOrder entity);

    /**
     * List<PurchaseOrder> → List<PurchaseOrderVO>
     */
    List<PurchaseOrderVO> toVOList(List<PurchaseOrder> entities);

    /**
     * PurchaseOrderItem → PurchaseOrderItemVO
     */
    PurchaseOrderItemVO toItemVO(PurchaseOrderItem entity);

    /**
     * List<PurchaseOrderItem> → List<PurchaseOrderItemVO>
     */
    List<PurchaseOrderItemVO> toItemVOList(List<PurchaseOrderItem> entities);

    // ==================== Entity → ExportVO ====================

    /**
     * PurchaseOrder → PurchaseOrderExportVO
     */
    @Mapping(target = "orderTypeName", source = "orderType", qualifiedByName = "orderTypeToName")
    @Mapping(target = "approvalStatusName", source = "approvalStatus", qualifiedByName = "approvalStatusToName")
    @Mapping(target = "receiptStatusName", source = "receiptStatus", qualifiedByName = "receiptStatusToName")
    @Mapping(target = "paymentStatusName", source = "paymentStatus", qualifiedByName = "paymentStatusToName")
    PurchaseOrderExportVO toExportVO(PurchaseOrder entity);

    /**
     * List<PurchaseOrder> → List<PurchaseOrderExportVO>
     */
    List<PurchaseOrderExportVO> toExportVOList(List<PurchaseOrder> entities);

    /**
     * PurchaseOrderItem → PurchaseOrderItemExportVO
     */
    PurchaseOrderItemExportVO toItemExportVO(PurchaseOrderItem entity);

    /**
     * List<PurchaseOrderItem> → List<PurchaseOrderItemExportVO>
     */
    List<PurchaseOrderItemExportVO> toItemExportVOList(List<PurchaseOrderItem> entities);

    // ==================== 枚举转换方法 ====================

    /**
     * 订单类型转名称
     */
    @Named("orderTypeToName")
    default String orderTypeToName(Integer orderType) {
        if (orderType == null) {
            return "";
        }
        return switch (orderType) {
            case 0 -> "正常";
            case 1 -> "紧急";
            default -> "未知";
        };
    }

    /**
     * 审批状态转名称
     */
    @Named("approvalStatusToName")
    default String approvalStatusToName(Integer approvalStatus) {
        if (approvalStatus == null) {
            return "";
        }
        ApprovalStatusEnum status = ApprovalStatusEnum.getByCode(approvalStatus);
        return status != null ? status.getDescription() : "未知";
    }

    /**
     * 收货状态转名称
     */
    @Named("receiptStatusToName")
    default String receiptStatusToName(Integer receiptStatus) {
        if (receiptStatus == null) {
            return "";
        }
        ReceiptStatusEnum status = ReceiptStatusEnum.getByCode(receiptStatus);
        return status != null ? status.getDescription() : "未知";
    }

    /**
     * 付款状态转名称
     */
    @Named("paymentStatusToName")
    default String paymentStatusToName(Integer paymentStatus) {
        if (paymentStatus == null) {
            return "";
        }
        PaymentStatusEnum status = PaymentStatusEnum.getByCode(paymentStatus);
        return status != null ? status.getDescription() : "未知";
    }
}
