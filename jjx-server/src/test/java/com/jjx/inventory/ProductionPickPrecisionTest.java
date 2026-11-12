package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.engineering.domain.entity.*;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.impl.InventoryOutboundServiceImpl;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.service.ProductionBomResolver;
import com.jjx.common.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 真实生产领料预览/缺口/补产入口，仓储替身，不冒充真实库过账。 */
@ExtendWith(MockitoExtension.class)
class ProductionPickPrecisionTest {
    @Mock InventoryOutboundOrderMapper outboundOrderMapper;
    @Mock InventoryOutboundItemMapper outboundItemMapper;
    @Mock InventoryStockItemMapper stockItemMapper;
    @Mock InventoryMaterialMapper materialMapper;
    @Mock ProductionOrderMapper productionOrderMapper;
    @Mock ProductionBomResolver productionBomResolver;
    @Mock EngineeringBomItemMapper productBomItemMapper;
    @InjectMocks InventoryOutboundServiceImpl service;
    EngineeringBomItem item;
    InventoryMaterial material;
    @BeforeEach void setup() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (var type : List.of(EngineeringBomItem.class, com.jjx.inventory.domain.InventoryOutboundOrder.class))
            TableInfoHelper.initTableInfo(assistant, type);
        var order = new ProductionOrder(); order.setOrderId(20L); order.setOrderNo("PRECISION");
        order.setOrderType("WORK_ORDER"); order.setPlannedQuantity(new BigDecimal("300"));
        when(productionOrderMapper.selectById(20L)).thenReturn(order);
        var bom = new EngineeringBom(); bom.setBomId(2L);
        when(productionBomResolver.resolve(order)).thenReturn(bom);
        item = new EngineeringBomItem(); item.setItemId(11L); item.setMaterialId(109L);
        item.setMaterialCode("RM000105"); item.setSourceType("buy"); item.setUnit("M");
        item.setModuleQty(new BigDecimal("6")); item.setBaseQty(BigDecimal.ONE);
        item.setQuantity(new BigDecimal("0.1667")); item.setActualIssueQty(new BigDecimal("0.1667"));
        when(productBomItemMapper.selectList(any())).thenReturn(List.of(item));
        material = new InventoryMaterial(); material.setMaterialId(109L); material.setUnit("M");
        when(materialMapper.selectById(109L)).thenReturn(material);
    }
    private void equal(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal) actual));
    }
    @Test void sixToOneUsesFiftyAcrossPreviewRemainingAndSupplement() {
        equal("50", service.previewPick(20L).get(0).get("demand"));
        equal("50", service.getPickRemaining(20L).get(0).get("remaining"));
        equal("50", service.previewPick(20L, new BigDecimal("300")).get(0).get("demand"));
    }
    @Test void continuousAreaKeepsDecimalsAndConvertsToStockUnit() {
        material.setUnit("M2"); item.setModuleQty(BigDecimal.ONE);
        item.setBaseQty(new BigDecimal("0.08")); item.setUnitFactor(new BigDecimal("0.195"));
        item.setUnitFactorBasis("已确认卷宽195mm，1M=0.195M2");
        var row = service.previewPick(20L).get(0);
        equal("4.68", row.get("demand")); assertEquals("M2", row.get("unit"));
    }
    @Test void missingConversionRejectsInsteadOfComparingMetresAndArea() {
        material.setUnit("M2");
        assertTrue(assertThrows(BusinessException.class, () -> service.previewPick(20L))
                .getMessage().contains("UNIT_CONVERSION_MISSING"));
    }
    @Test void lossOnlyOnceAndMinimumIssueInBomUnits() {
        item.setLossRate(new BigDecimal("10")); equal("55", service.previewPick(20L).get(0).get("demand"));
        item.setMinIssueQty(new BigDecimal("60"));
        equal("60", service.previewPick(20L).get(0).get("demand"));
    }
    @Test void pieceRoundingAtBatchEndDoesNotRoundUnitDemandFirst() {
        item.setUnit("PCS"); material.setUnit("PCS"); item.setModuleQty(new BigDecimal("7"));
        equal("43", service.previewPick(20L).get(0).get("demand"));
    }
    @Test void fractionalLossIsNotTruncatedOrAppliedTwice() {
        item.setLossRate(new BigDecimal("0.50"));
        equal("50.25", service.previewPick(20L).get(0).get("demand"));
    }
    @Test void repeatedMaterialRoundsOnceAndSharesAvailableStock() {
        item.setUnit("PCS"); material.setUnit("PCS");
        item.setModuleQty(new BigDecimal("600"));
        var second = new EngineeringBomItem();
        org.springframework.beans.BeanUtils.copyProperties(item, second); second.setItemId(12L);
        when(productBomItemMapper.selectList(any())).thenReturn(List.of(item, second));
        var batch = new com.jjx.inventory.domain.InventoryStockItem(); batch.setQuantity(BigDecimal.ONE); batch.setReservedQuantity(BigDecimal.ZERO);
        when(stockItemMapper.selectFIFOAvailable(109L)).thenReturn(List.of(batch));
        var rows = service.previewPick(20L);
        equal("1", rows.stream().map(row -> (BigDecimal) row.get("demand")).reduce(BigDecimal.ZERO, BigDecimal::add));
        equal("1", rows.stream().map(row -> (BigDecimal) row.get("qtyPick")).reduce(BigDecimal.ZERO, BigDecimal::add));
        equal("1", service.previewPick(20L, new BigDecimal("300")).get(0).get("demand"));
    }
    @Test void sameMaterialMultipleRowsCannotEachUseTheSameStock() {
        var second = new EngineeringBomItem();
        org.springframework.beans.BeanUtils.copyProperties(item, second); second.setItemId(12L);
        when(productBomItemMapper.selectList(any())).thenReturn(List.of(item, second));
        var batch = new com.jjx.inventory.domain.InventoryStockItem(); batch.setQuantity(new BigDecimal("60")); batch.setReservedQuantity(BigDecimal.ZERO);
        when(stockItemMapper.selectFIFOAvailable(109L)).thenReturn(List.of(batch));
        var rows = service.previewPick(20L);
        equal("100", rows.stream().map(row -> (BigDecimal) row.get("demand")).reduce(BigDecimal.ZERO, BigDecimal::add));
        equal("60", rows.stream().map(row -> (BigDecimal) row.get("qtyPick")).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

}
