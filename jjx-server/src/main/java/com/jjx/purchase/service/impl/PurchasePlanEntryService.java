package com.jjx.purchase.service.impl;

import com.jjx.common.enums.ApproveStatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.util.UnitCodes;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.purchase.domain.dto.PurchaseOrderDTO;
import com.jjx.purchase.domain.dto.PurchaseOrderItemDTO;
import com.jjx.purchase.domain.enums.ReceiptStatusEnum;
import com.jjx.purchase.domain.model.PurchaseQuantityPricing;
import com.jjx.purchase.mapper.PurchasePlanContextMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PurchasePlanEntryService {
    private final InventoryMaterialMapper materials;
    private final PurchasePlanContextMapper context;
    private final PurchaseOrderServiceImpl orders;

    @Transactional(readOnly=true)
    public Map<String,Object> material(Long id) {
        var m=requireMaterial(id);
        String unit=UnitCodes.requireDictUnit(m.getUnit(),"物料基本单位");
        var r=new LinkedHashMap<String,Object>();
        r.put("materialId",m.getMaterialId());r.put("materialCode",m.getMaterialCode());
        r.put("materialName",m.getMaterialName());r.put("supplierMaterialName",m.getSupplierMaterialName());
        r.put("materialSpec",m.getSpecification());r.put("unit",unit);
        r.put("supplierId",m.getSupplierId());
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
    private com.jjx.inventory.domain.InventoryMaterial requireMaterial(Long id) {
        var m=id==null?null:materials.selectById(id);
        if(m==null)throw new BusinessException("请选择有效物料");
        return m;
    }
}
