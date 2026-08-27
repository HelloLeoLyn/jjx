package com.jjx.purchase.converter;

import com.jjx.purchase.domain.dto.PurchaseOrderDTO;
import com.jjx.purchase.domain.dto.PurchaseOrderItemDTO;
import com.jjx.purchase.domain.entity.PurchaseOrder;
import com.jjx.purchase.domain.entity.PurchaseOrderItem;
import com.jjx.purchase.domain.vo.PurchaseOrderExportVO;
import com.jjx.purchase.domain.vo.PurchaseOrderItemExportVO;
import com.jjx.purchase.domain.vo.PurchaseOrderItemVO;
import com.jjx.purchase.domain.vo.PurchaseOrderVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
}
