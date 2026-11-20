package com.jjx.inventory;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.inventory.converter.StockConverter;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.dto.query.StockQueryDTO;
import com.jjx.inventory.dto.vo.StockVO;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.InventoryStockMutationService;
import com.jjx.inventory.service.impl.InventoryStockServiceImpl;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryStockPageMeasurementTest {
    @Test void pageLoadsOnlyCurrentPageIdentifiersAndUsesNoPerRowArchiveQueries() {
        var summaries=mock(InventoryStockMapper.class);var batches=mock(InventoryStockItemMapper.class);
        var materials=mock(InventoryMaterialMapper.class);var items=mock(InventoryItemMapper.class);
        var locations=mock(InventoryStorageLocationMapper.class);var converter=mock(StockConverter.class);
        var service=new InventoryStockServiceImpl(summaries,batches,materials,mock(InventoryWarehouseMapper.class),locations,items,converter,mock(InventoryStockMutationService.class));
        var reader=mock(com.jjx.inventory.service.impl.InventoryStockQueryService.class);
        service.setQueryService(reader);
        var vo=new StockVO();vo.setInventoryItemId(31L);vo.setMaterialId(354L);vo.setTotalQuantity(new BigDecimal("244"));
        var page=new Page<StockVO>(2,1,100);page.setRecords(List.of(vo));
        when(reader.page(any())).thenReturn(page);
        var item=new InventoryItem();item.setInventoryItemId(31L);item.setItemType("MATERIAL");item.setSourceId(354L);item.setUnit("㎡");
        when(items.selectBatchIds(eq(List.of(31L)))).thenReturn(List.of(item));
        var material=new InventoryMaterial();material.setMaterialId(354L);material.setMaterialForm("FILM");material.setUnit("KG");
        when(materials.selectBatchIds(eq(List.of(354L)))).thenReturn(List.of(material));
        var first=new InventoryStockItem();first.setInventoryItemId(31L);first.setQuantity(new BigDecimal("122"));first.setRollNo("R2");
        var second=new InventoryStockItem();second.setInventoryItemId(31L);second.setQuantity(new BigDecimal("122"));second.setRollNo("R3");
        when(batches.selectList(any(Wrapper.class))).thenReturn(List.of(first,second));
        var query=new StockQueryDTO();query.setPageNum(2);query.setPageSize(1);
        var result=service.page(query);
        assertEquals(100,result.getTotal());assertEquals(2,result.getCurrent());assertEquals(1,result.getRecords().size());
        assertEquals("㎡",vo.getUnit(),"当前档案单位不能覆盖历史库存物品单位");
        assertEquals(0,new BigDecimal("2").compareTo(vo.getPhysicalQuantity()));
        assertEquals(0,new BigDecimal("244").compareTo(vo.getInventoryArea()));
        verify(items,times(1)).selectBatchIds(List.of(31L));verify(materials,times(1)).selectBatchIds(List.of(354L));
        verify(batches,times(1)).selectList(any(Wrapper.class));
        verify(items,never()).selectById(any());verify(materials,never()).selectById(any());verifyNoInteractions(locations);
    }
}
