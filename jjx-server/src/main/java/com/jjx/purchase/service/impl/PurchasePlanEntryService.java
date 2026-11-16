package com.jjx.purchase.service.impl;

import com.jjx.common.enums.ApproveStatusEnum;
import com.jjx.common.enums.StatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.util.UnitCodes;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.purchase.domain.dto.PurchaseOrderDTO;
import com.jjx.purchase.domain.dto.PurchaseOrderItemDTO;
import com.jjx.purchase.domain.enums.ReceiptStatusEnum;
import com.jjx.purchase.domain.model.PurchaseQuantityPricing;
import com.jjx.purchase.mapper.PurchasePlanContextMapper;
import com.jjx.purchase.mapper.PurchaseSupplierMapper;
import com.jjx.purchase.domain.enums.PurchasePlanSchemeStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PurchasePlanEntryService {
    private final InventoryMaterialMapper materials;
    private final PurchasePlanContextMapper context;
    private final PurchaseOrderServiceImpl orders;
    private final PurchaseSupplierMapper suppliers;

    @Transactional(readOnly=true)
    public Map<String,Object> material(Long id) {
        var m=requireMaterial(id);
        String unit=UnitCodes.requireDictUnit(m.getUnit(),"物料基本单位");
        var r=new LinkedHashMap<String,Object>();
        r.put("materialId",m.getMaterialId());r.put("materialCode",m.getMaterialCode());
        r.put("materialName",m.getMaterialName());r.put("supplierMaterialName",m.getSupplierMaterialName());
        r.put("materialSpec",m.getSpecification());r.put("unit",unit);
        r.put("supplierId",m.getSupplierId());
        r.put("supplierName",m.getSupplierName());
        r.put("allowedUnits",PurchaseQuantityPricing.availableUnits(unit));
        r.put("rollWidthMm",m.getWidthMm());r.put("pieceWidthMm",m.getWidthMm());r.put("pieceLengthMm",m.getLengthMm());
        r.put("rollLengthM",m.getLengthMm()==null?null:m.getLengthMm().divide(BigDecimal.valueOf(1000)));
        r.put("archiveWidthMm",m.getWidthMm());r.put("archiveLengthMm",m.getLengthMm());
        // 查询失败直接向上传递，前端显示未读取；只有成功的空聚合才表示零。
        r.put("currentStock",context.stock(id));r.put("availableStock",context.available(id));
        r.put("inTransit",context.inTransit(id,
            List.of(ApproveStatusEnum.DRAFT.getValue(),ApproveStatusEnum.PENDING.getValue(),ApproveStatusEnum.APPROVED.getValue()),
            List.of(ReceiptStatusEnum.PENDING.getValue(),ReceiptStatusEnum.PARTIALLY_RECEIVED.getValue())));
        return r;
    }

    /** 不新增换算公式，计划试算委托订单保存使用的唯一入口；不接受计划客户端定价。 */
    @Transactional(readOnly=true)
    public PurchaseOrderItemDTO quantity(PurchaseOrderItemDTO item) {
        if(item==null) throw new BusinessException("请选择有效物料");
        var m=requireMaterial(item.getMaterialId());
        item.setUnit(UnitCodes.requireDictUnit(m.getUnit(),"物料基本单位"));
        item.setPriceUnit(item.getUnit());item.setUnitPrice(BigDecimal.ZERO);item.setTaxRate(BigDecimal.ZERO);
        var dto=new PurchaseOrderDTO();dto.setItems(List.of(item));
        return orders.previewPricing(dto).getItems().get(0);
    }

    public record SchemeRequest(Long sourceMaterialId, BigDecimal demandQuantity, Long supplierId,
                                LocalDate needDate, LocalDate expectedDeliveryDate, PurchaseOrderItemDTO item) {}
    public record SchemePreview(PurchaseOrderItemDTO item, String supplierName,
                                PurchasePlanSchemeStatus status, BigDecimal coveredQuantity,
                                BigDecimal remainingQuantity, String message) {}

    /** 只读校核：数量/金额沿用采购订单规则，不从名称、面积相同或客户端状态推定跨物料可替代。 */
    @Transactional(readOnly=true)
    public SchemePreview scheme(SchemeRequest request) {
        if (request == null || request.item() == null) throw new BusinessException("请选择采购物料");
        var source = requireMaterial(request.sourceMaterialId());
        var actual = requireMaterial(request.item().getMaterialId());
        if (!StatusEnum.NORMAL.getCode().equals(source.getStatus()) || !StatusEnum.NORMAL.getCode().equals(actual.getStatus())) {
            throw new BusinessException("原需求或采购物料已停用，请重新选择");
        }
        if (request.demandQuantity() != null && request.demandQuantity().signum() < 0) {
            throw new BusinessException("原始需求不能为负数");
        }
        String supplierName = null;
        if (request.supplierId() != null) {
            var supplier = suppliers.selectById(request.supplierId());
            if (supplier == null || !StatusEnum.NORMAL.getCode().equals(supplier.getStatus())) {
                throw new BusinessException("请选择有效且正常的供应商");
            }
            supplierName = supplier.getSupplierName();
        }
        var item = request.item();
        item.setMaterialCode(actual.getMaterialCode());
        item.setMaterialName(actual.getMaterialName());
        item.setMaterialSpec(actual.getSpecification());
        item.setSupplierMaterialName(actual.getSupplierMaterialName());
        item.setUnit(UnitCodes.requireDictUnit(actual.getUnit(), "采购物料基本单位"));
        var order = new PurchaseOrderDTO();
        order.setSupplierId(request.supplierId());
        order.setItems(List.of(item));
        var priced = orders.previewPricing(order).getItems().get(0);
        if (!Objects.equals(source.getMaterialId(), actual.getMaterialId())) {
            return new SchemePreview(priced, supplierName, PurchasePlanSchemeStatus.NEEDS_CONFIRMATION,
                    null, null, "采购物料与原需求不同，尚无本次需求的已确认替代/分切规则；待技术确认，不能生成替代采购单或抵扣原缺口");
        }
        BigDecimal demand = request.demandQuantity();
        BigDecimal covered = demand == null ? null : priced.getQuantity().min(demand);
        BigDecimal remaining = demand == null ? null : demand.subtract(covered).max(BigDecimal.ZERO);
        String message = demand == null ? "未提供原始需求，本次只校核采购数量与金额" : "同一物料，覆盖量按物料基本单位计算；不表示已经到货";
        if (request.needDate() != null && request.expectedDeliveryDate() != null && request.expectedDeliveryDate().isAfter(request.needDate())) {
            message += "；预计到货晚于需求日期，请核对交期";
        }
        if (request.supplierId() == null) {
            return new SchemePreview(priced, null, PurchasePlanSchemeStatus.UNARRANGED, covered, remaining,
                    "请选择本次采购供应商");
        }
        return new SchemePreview(priced, supplierName, PurchasePlanSchemeStatus.READY, covered, remaining, message);
    }
    private com.jjx.inventory.domain.InventoryMaterial requireMaterial(Long id) {
        var m=id==null?null:materials.selectById(id);
        if(m==null)throw new BusinessException("请选择有效物料");
        return m;
    }
}
