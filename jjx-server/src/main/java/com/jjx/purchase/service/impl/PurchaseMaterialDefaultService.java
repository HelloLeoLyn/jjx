package com.jjx.purchase.service.impl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.util.UnitCodes;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.purchase.domain.dto.PurchaseMaterialDefaultDTO;
import com.jjx.purchase.domain.model.PurchaseQuantityPricing;
import com.jjx.purchase.mapper.PurchaseMaterialDefaultMapper;
import com.jjx.system.mapper.SysDictItemMapper;
import com.jjx.system.domain.entity.SysDictItem;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PurchaseMaterialDefaultService {
    private final PurchaseMaterialDefaultMapper defaults;
    private final InventoryMaterialMapper materials;
    private final SysDictItemMapper dictionary;
    private final ObjectMapper json;
    @Transactional(readOnly=true)
    public List<Map<String,String>> units() {
        var result = new LinkedHashMap<String,Map<String,String>>();
        for (var row : dictionary.selectList(Wrappers.<SysDictItem>lambdaQuery()
                .eq(SysDictItem::getDictCode,"unit").eq(SysDictItem::getIsActive,1).orderByAsc(SysDictItem::getSortOrder))) {
            String code=UnitCodes.normalize(row.getItemValue());
            if (UnitCodes.isDictUnit(code)) result.putIfAbsent(code,Map.of("value",code,"label",UnitCodes.SQM.equals(code)?UnitCodes.SQM:row.getLabel()==null?code:row.getLabel()));
        }
        return new ArrayList<>(result.values());
    }
    @Transactional(readOnly=true)
    public Map<String,Object> read(Long supplierId, Long materialId) {
        var m=material(supplierId,materialId);
        String base=UnitCodes.requireDictUnit(m.getUnit(),"物料基本单位");
        var stored=parse(defaults.read(supplierId)).get(String.valueOf(materialId));
        var r=new LinkedHashMap<String,Object>();
        r.put("configured",stored!=null); r.put("unit",base);
        r.put("allowedUnits",PurchaseQuantityPricing.availableUnits(base));
        r.put("purchaseUnit",stored==null?base:UnitCodes.requireDictUnit(stored.path("purchaseUnit").asText(),"默认采购单位"));
        r.put("priceUnit",stored==null?base:UnitCodes.requireDictUnit(stored.path("priceUnit").asText(),"默认报价单位"));
        PurchaseQuantityPricing.validateCombination(base,(String)r.get("purchaseUnit"),(String)r.get("priceUnit"));
        r.put("rollWidthMm",m.getWidthMm());
        r.put("rollLengthM",m.getLengthMm()==null?null:m.getLengthMm().divide(BigDecimal.valueOf(1000)));
        r.put("pieceWidthMm",m.getWidthMm()); r.put("pieceLengthMm",m.getLengthMm());
        return r;
    }
    @Transactional(rollbackFor=Exception.class)
    public void save(PurchaseMaterialDefaultDTO dto,String operator) {
        var m=material(dto.getSupplierId(),dto.getMaterialId());
        String base=UnitCodes.requireDictUnit(m.getUnit(),"物料基本单位");
        String purchase=UnitCodes.requireDictUnit(dto.getPurchaseUnit(),"默认采购单位");
        String price=UnitCodes.requireDictUnit(dto.getPriceUnit(),"默认报价单位");
        PurchaseQuantityPricing.validateCombination(base,purchase,price);
        // 锁住供应商行后再合并，两个客户端配置不同物料不会相互覆盖。
        ObjectNode all=parse(defaults.readForUpdate(dto.getSupplierId()));
        ObjectNode pair=json.createObjectNode(); pair.put("purchaseUnit",purchase);pair.put("priceUnit",price);
        all.set(String.valueOf(dto.getMaterialId()),pair);
        if (defaults.write(dto.getSupplierId(),all.toString(),operator)!=1)
            throw new BusinessException("供应商采购约定保存失败，请刷新后重试");
    }
    private com.jjx.inventory.domain.InventoryMaterial material(Long supplierId,Long materialId) {
        if (supplierId==null || supplierId<=0 || defaults.supplierExists(supplierId)!=1) throw new BusinessException("请选择有效供应商");
        var m=materialId==null?null:materials.selectById(materialId);
        if (m==null) throw new BusinessException("请选择有效物料");
        return m;
    }
    private ObjectNode parse(String text) {
        if (text==null || text.isBlank()) return json.createObjectNode();
        try { var n=json.readTree(text); if (n instanceof ObjectNode obj) return obj; }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new BusinessException("供应商采购约定格式错误，请联系管理员核对"); }
        throw new BusinessException("供应商采购约定格式错误，请联系管理员核对");
    }
}
