package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.*;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.service.impl.BomMaterialDemandService;
import com.jjx.product.mapper.*;
import com.jjx.product.enums.ProductEnums;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BomMaterialDemandServiceTest {
    InventoryMaterialMapper materials;
    EngineeringBomMapper boms;
    EngineeringBomItemMapper items;
    BomMaterialDemandService service;
    static BigDecimal bd(String n) { return new BigDecimal(n); }
    static void eq(String n, BigDecimal value) { assertNotNull(value); assertEquals(0, bd(n).compareTo(value)); }
    @BeforeEach void setup() {
        materials=mock(InventoryMaterialMapper.class);boms=mock(EngineeringBomMapper.class);items=mock(EngineeringBomItemMapper.class);
        service=new BomMaterialDemandService(materials,boms,items);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),""),EngineeringBomItem.class);
    }
    InventoryMaterial material(long id,String unit) {
        var m=new InventoryMaterial();m.setMaterialId(id);m.setUnit(unit);m.setWidthMm(bd("195"));m.setLengthMm(bd("50000"));
        when(materials.selectById(id)).thenReturn(m);return m;
    }
    EngineeringBomItem ratio(long id,String unit,String base,String module) {
        var r=new EngineeringBomItem();r.setItemId(id);r.setMaterialId(id);r.setMaterialCode("M"+id);r.setUnit(unit);
        r.setBaseQty(bd(base));r.setModuleQty(bd(module));r.setActualIssueQty(bd("0.1667"));return r;
    }
    @Test void sixHundredWithThreePercentLossIs103Not104() {
        material(1,"PCS");var r=ratio(1,"PCS","1","6");r.setLossRate(bd("3"));r.setActualIssueQty(bd("0.1717"));
        var d=service.newDemand(false);d.add(List.of(r),bd("600"));d.requireComplete();eq("103",d.rounded().get(1L));
    }
    @Test void sameMaterialAcrossProductsAndRowsRoundsAfterSumming() {
        material(1,"PCS");var r=ratio(1,"PCS","1","6");var d=service.newDemand(false);
        d.add(List.of(r),bd("3"));d.add(List.of(r),bd("3"));eq("1",d.rounded().get(1L));
    }
    @Test void continuousMetresAndKilogramsKeepFractions() {
        material(1,"M");material(2,"KG");var d=service.newDemand(false);
        d.add(List.of(ratio(1,"M","0.08","1"),ratio(2,"KG","0.01","1")),bd("3"));
        d.requireComplete();eq("0.24",d.rounded().get(1L));eq("0.03",d.rounded().get(2L));
    }
    @Test void pitchUsesOriginalFractionArchiveWidthAndLossOnce() {
        material(1,"㎡");var r=ratio(1,"㎡","1","6");r.setBaseQty(null);r.setModuleQty(null);
        r.setPitchMm(bd("80.55"));r.setPcsPerPitch(3);r.setLossRate(bd("0.5"));r.setActualIssueQty(bd("99"));
        var d=service.newDemand(false);d.add(List.of(r),bd("300"));d.requireComplete();eq("1.5786",d.rounded().get(1L));
    }
    @Test void missingConversionNeverUsesNominalRollGeometry() {
        material(1,"㎡");var d=service.newDemand(false);d.add(List.of(ratio(1,"PCS","1","1")),bd("1"));
        assertEquals(1,d.issues().size());assertTrue(d.rounded().isEmpty());
        assertTrue(assertThrows(BusinessException.class,d::requireComplete).getMessage().contains("UNIT_CONVERSION_MISSING"));
    }
    @Test void savedConversionWithEvidenceIsAppliedBeforeRounding() {
        material(1,"M");var r=ratio(1,"PCS","1","6");r.setUnitFactor(bd("0.48"));r.setUnitFactorBasis("实测定额");
        var d=service.newDemand(false);d.add(List.of(r),bd("3"));d.requireComplete();eq("0.24",d.rounded().get(1L));
    }
    @Test void invalidRawParametersAndMissingQuantityNeverFallBackToSnapshotOrZero() {
        material(1,"PCS");var r=ratio(1,"PCS","1","0");var d=service.newDemand(false);d.add(List.of(r),bd("1"));
        assertThrows(BusinessException.class,d::requireComplete);assertTrue(d.rounded().isEmpty());
        r.setBaseQty(null);r.setModuleQty(null);r.setActualIssueQty(null);r.setQuantity(null);
        var absent=service.newDemand(false);absent.add(List.of(r),bd("1"));assertThrows(BusinessException.class,absent::requireComplete);
    }
    @Test void invalidContributionRemovesPartialTotalForSameMaterial() {
        material(1,"M");var good=ratio(1,"M","1","1");var bad=ratio(1,"PCS","1","1");var d=service.newDemand(false);
        d.add(List.of(good,bad,good),bd("1"));assertTrue(d.rounded().isEmpty());assertFalse(d.issues().isEmpty());
    }
    @Test void minimumIsPreservedAndLegacyAppliedLossNotAppliedTwice() {
        material(1,"M");var r=ratio(1,"M","0.08","1");r.setMinIssueQty(bd("0.5"));var d=service.newDemand(false);
        d.add(List.of(r),bd("3"));eq("0.5",d.rounded().get(1L));
        r.setBaseQty(null);r.setModuleQty(null);r.setActualIssueQty(bd("0.5"));r.setLossRate(bd("10"));
        var legacy=service.newDemand(false);legacy.add(List.of(r),bd("3"));eq("1.5",legacy.rounded().get(1L));
    }
    @Test void absentArchiveNeverReinterpretsHistoricalId() {
        var d=service.newDemand(false);d.add(List.of(ratio(109,"PCS","1","1")),bd("3"));
        assertTrue(assertThrows(BusinessException.class,d::requireComplete).getMessage().contains("物料档案不存在"));
    }
    EngineeringBomItem shared() {
        material(1,"PCS");material(2,"PCS");var r=ratio(1,"PCS","1","6");r.setSourceType("make");r.setSubBomId(10L);
        var sub=new EngineeringBom();sub.setOutputType("MATERIAL");sub.setOutputId(1L);sub.setApproveStatus(ProductEnums.BomStatus.APPROVED.getValue());
        when(boms.selectById(10L)).thenReturn(sub);return r;
    }
    @Test void sharedSubBomExpandsBothPathsBeforeLeafRounding() {
        var parent=shared();when(items.selectList(any())).thenReturn(List.of(ratio(2,"PCS","1","1")));
        var d=service.newDemand(true);d.add(List.of(parent,parent),bd("3"));d.requireComplete();eq("1",d.rounded().get(2L));
    }
    @Test void cycleReportsIssueInsteadOfProcurementDemand() {
        var parent=shared();when(items.selectList(any())).thenReturn(List.of(parent));var d=service.newDemand(true);
        d.add(List.of(parent),bd("3"));assertThrows(BusinessException.class,d::requireComplete);assertTrue(d.rounded().isEmpty());
    }
}
