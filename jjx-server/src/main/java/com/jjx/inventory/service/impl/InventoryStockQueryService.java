package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.*;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.dto.query.*;
import com.jjx.inventory.dto.vo.*;
import com.jjx.inventory.enums.*;
import com.jjx.inventory.mapper.InventoryStockReadMapper;
import com.jjx.inventory.service.InventoryMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.apache.commons.lang3.StringUtils;

@Service
@RequiredArgsConstructor
public class InventoryStockQueryService {
    private final InventoryStockReadMapper mapper;
    private final InventoryMaterialService materials;
    public record Filters(QueryWrapper<StockVO> stock,LambdaQueryWrapper<InventoryMaterial> material) { }

    public Filters filters(StockQueryDTO q) {
        var f=new QueryWrapper<StockVO>();
        if(StringUtils.isNotBlank(q.getItemType())) {
            try {InventoryItemTypeEnum.valueOf(q.getItemType());}
            catch(IllegalArgumentException ex) {throw new BusinessException("非法库存类型");}
            f.eq("v.item_type",q.getItemType());
        }
        f.eq(q.getStockId()!=null,"v.stock_id",q.getStockId())
         .eq(q.getMaterialId()!=null,"v.material_id",q.getMaterialId())
         .like(StringUtils.isNotBlank(q.getMaterialCode()),"v.material_code",q.getMaterialCode())
         .like(StringUtils.isNotBlank(q.getMaterialName()),"v.material_name",q.getMaterialName())
         .ge(q.getMinQuantity()!=null,"v.total_quantity",q.getMinQuantity())
         .le(q.getMaxQuantity()!=null,"v.total_quantity",q.getMaxQuantity());
        if(q.getMinQuantity()!=null && q.getMaxQuantity()!=null && q.getMinQuantity().compareTo(q.getMaxQuantity())>0)
            throw new BusinessException("最小数量不能大于最大数量");
        if(q.getOnlyHasStock()!=null) {
            if(q.getOnlyHasStock()) f.gt("v.total_quantity",BigDecimal.ZERO);else f.eq("v.total_quantity",BigDecimal.ZERO);
        }
        if(q.getLowStock()!=null) f.eq("v.low_stock",q.getLowStock());
        if(q.getExpiring()!=null) f.eq("v.expiring",q.getExpiring());
        if(q.getObsolete()!=null) f.eq("v.obsolete",q.getObsolete());
        if(StringUtils.isNotBlank(q.getCreateTimeStart()))f.ge("v.create_time",date(q.getCreateTimeStart()).atStartOfDay());
        if(StringUtils.isNotBlank(q.getCreateTimeEnd()))f.lt("v.create_time",date(q.getCreateTimeEnd()).plusDays(1).atStartOfDay());
        boolean materialOnly=q.getCategoryId()!=null || StringUtils.isNotBlank(q.getMaterialMeta())
            ||StringUtils.isNotBlank(q.getMaterialForm())||StringUtils.isNotBlank(q.getColor())
            ||StringUtils.isNotBlank(q.getBrand())||StringUtils.isNotBlank(q.getAttributeFilters());
        LambdaQueryWrapper<InventoryMaterial> mf=null;
        if(materialOnly || StringUtils.isNotBlank(q.getKeyword())) {
            var mq=new MaterialQueryDTO(); mq.setKeyword(q.getKeyword());mq.setCategoryId(q.getCategoryId());
            mq.setMaterialMeta(q.getMaterialMeta());mq.setMaterialForm(q.getMaterialForm());
            mq.setColor(q.getColor());mq.setBrand(q.getBrand());mq.setAttributeFilters(q.getAttributeFilters());
            mf=materials.inventorySearchFilter(mq);
            String materialSql="v.item_type={0} AND v.source_id IN (SELECT inventory_material.material_id FROM inventory_material "
                +mf.getCustomSqlSegment()+")";
            if(materialOnly) f.apply(materialSql,InventoryItemTypeEnum.MATERIAL.getCode());
            else f.and(w -> {
                w.apply(materialSql,InventoryItemTypeEnum.MATERIAL.getCode()).or(p -> {
                    p.eq("v.item_type",InventoryItemTypeEnum.PRODUCT.getCode());
                    for(String term:q.getKeyword().trim().split("\\s+"))
                        p.and(t -> t.like("v.material_code",term).or().like("v.material_name",term).or().like("v.specification",term));
                });
            });
        }
        return new Filters(f,mf);
    }
    private static LocalDate date(String text) {
        try{return LocalDate.parse(text.trim().substring(0,10));}
        catch(RuntimeException ex){throw new BusinessException("创建时间格式应为YYYY-MM-DD");}
    }
    public IPage<StockVO> page(StockQueryDTO q) {
        var f=filters(q);f.stock().orderByDesc("v.update_time","v.stock_id");
        return mapper.page(new Page<>(q.getPageNum(),q.getPageSize()),q,f.stock(),f.material(),StockItemStatusEnum.ACTIVE.getValue(),InventoryItemTypeEnum.MATERIAL.getCode());
    }
    public StockSummaryVO summary(StockQueryDTO q) {
        var f=filters(q);var m=mapper.summary(q,f.stock(),f.material(),StockItemStatusEnum.ACTIVE.getValue(),InventoryItemTypeEnum.MATERIAL.getCode());
        var s=new StockSummaryVO();s.setMaterialCount(number(m,"material_count").intValue());
        s.setTotalQuantity(number(m,"total_quantity"));s.setTotalReservedQuantity(number(m,"total_reserved"));
        s.setTotalAvailableQuantity(s.getTotalQuantity().subtract(s.getTotalReservedQuantity()));
        s.setTotalCost(number(m,"total_cost"));s.setMissingCostBatches(number(m,"missing_cost_batches").intValue());
        s.setLowStockMaterialCount(number(m,"low_count").intValue());
        var units=new LinkedHashMap<String,BigDecimal>();
        for(var row:mapper.quantities(q,f.stock(),f.material(),StockItemStatusEnum.ACTIVE.getValue(),InventoryItemTypeEnum.MATERIAL.getCode()))
            units.put(String.valueOf(row.get("unit")),number(row,"quantity"));
        s.setQuantityByUnit(units);return s;
    }
    private static BigDecimal number(Map<String,Object> m,String key) {return new BigDecimal(Objects.toString(m.get(key),"0"));}
}
