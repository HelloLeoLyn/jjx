package com.jjx.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.vo.BomReviewPackageVO;
import com.jjx.product.enums.ManufacturingOutputType;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.system.service.ReviewFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 整套范围预览：递归、去重、防环、复用与阻断（mapper 替身，不代表生产MySQL实跑）。 */
class BomReviewPackageTest {

    EngineeringBomServiceImpl service;
    EngineeringBomMapper boms;
    EngineeringBomItemMapper items;
    ProductMapper products;
    InventoryMaterialMapper materials;
    Map<Long, EngineeringBom> bomMap = new HashMap<>();
    Map<Long, List<EngineeringBomItem>> rowMap = new HashMap<>();

    @BeforeEach
    void setup() {
        boms = mock(EngineeringBomMapper.class);
        items = mock(EngineeringBomItemMapper.class);
        products = mock(ProductMapper.class);
        materials = mock(InventoryMaterialMapper.class);
        service = new EngineeringBomServiceImpl(boms, items, products, null, null, materials,
                mock(ReviewFlowService.class), null, null, null);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"),
                EngineeringBomItem.class);
        Product product = new Product();
        product.setProductId(100L);
        product.setProductCode("YL1025PWOO");
        product.setProductName("YL-208NDW");
        when(products.selectById(anyLong())).thenReturn(product);
        when(materials.selectById(anyLong())).thenAnswer(inv -> {
            InventoryMaterial material = new InventoryMaterial();
            material.setMaterialId(inv.getArgument(0));
            material.setMaterialCode("M" + inv.getArgument(0));
            material.setMaterialName("材料" + inv.getArgument(0));
            material.setUnit("PCS");
            material.setStatus(1);
            return material;
        });
        when(boms.selectById(anyLong())).thenAnswer(inv -> bomMap.get((Long) inv.getArgument(0)));
        when(boms.selectCount(any())).thenReturn(0L);
        when(boms.selectBatchIds(anyCollection())).thenAnswer(inv -> {
            java.util.Collection<Long> ids = inv.getArgument(0);
            List<EngineeringBom> list = new ArrayList<>();
            for (Long id : ids) {
                if (bomMap.containsKey(id)) list.add(bomMap.get(id));
            }
            return list;
        });
        when(items.selectList(any())).thenAnswer(inv -> {
            LambdaQueryWrapper<?> wrapper = inv.getArgument(0);
            wrapper.getSqlSegment();
            Long bomId = (Long) wrapper.getParamNameValuePairs().values().iterator().next();
            return rowMap.getOrDefault(bomId, List.of());
        });
    }

    EngineeringBom bom(long id, String type, long outputId, Integer status, String code) {
        EngineeringBom bom = new EngineeringBom();
        bom.setBomId(id);
        bom.setBomCode(code);
        bom.setBomName(code);
        bom.setBomVersion("V1.0");
        bom.setOutputType(type);
        bom.setOutputId(outputId);
        bom.setProductId(ManufacturingOutputType.PRODUCT.name().equals(type) ? null : null);
        bom.setApproveStatus(status);
        bom.setIsCurrent(false);
        bomMap.put(id, bom);
        return bom;
    }

    EngineeringBomItem item(long bomId, long itemId, Long materialId, Long subBomId, String source,
                            ProductEnums.BomItemReviewStatus status) {
        EngineeringBomItem row = new EngineeringBomItem();
        row.setItemId(itemId);
        row.setBomId(bomId);
        row.setMaterialId(materialId);
        row.setSubBomId(subBomId);
        row.setSourceType(source);
        row.setReviewStatus(status.getValue());
        row.setMaterialName("材料" + materialId);
        row.setMaterialCode("M" + materialId);
        row.setSpecification("S");
        row.setUnit("PCS");
        row.setQuantity(BigDecimal.ONE);
        row.setModuleQty(BigDecimal.ONE);
        row.setBaseQty(BigDecimal.ONE);
        row.setLossRate(BigDecimal.ZERO);
        row.setItemOrder((int) itemId);
        rowMap.computeIfAbsent(bomId, k -> new ArrayList<>()).add(row);
        return row;
    }

    void standardProductChain(ProductEnums.BomStatus rootStatus, ProductEnums.BomStatus childStatus,
                              ProductEnums.BomItemReviewStatus rowStatus) {
        bom(1L, "PRODUCT", 100L, rootStatus.getValue(), "P-BOM");
        bom(2L, "MATERIAL", 5L, childStatus.getValue(), "M1-MFG");
        bom(3L, "MATERIAL", 6L, childStatus.getValue(), "M2-MFG");
        item(1L, 11L, 5L, 2L, "make", rowStatus);
        item(2L, 21L, 6L, 3L, "make", rowStatus);
        item(3L, 31L, 5L, null, "buy", rowStatus);
    }

    @Test
    void submitPlanExpandsActualSubBomTreeAndAllowsSameBatchPendingNodes() {
        standardProductChain(ProductEnums.BomStatus.DRAFT, ProductEnums.BomStatus.DRAFT,
                ProductEnums.BomItemReviewStatus.DRAFT);
        BomReviewPackageVO plan = service.planReviewPackage(1L, "SUBMIT");
        assertTrue(plan.isExecutable(), plan.getReason());
        assertEquals(3, plan.getActionCount());
        assertEquals(List.of(3L, 2L, 1L), plan.getNodes().stream().map(BomReviewPackageVO.Node::getBomId).toList());
        assertEquals("SUBMIT", plan.getNodes().get(2).getPlannedAction());
        assertEquals("YL1025PWOO", plan.getProductCode());
    }

    @Test
    void approvePlanAllowsPendingRowsAndReusesApprovedNodes() {
        standardProductChain(ProductEnums.BomStatus.REVIEWING, ProductEnums.BomStatus.REVIEWING,
                ProductEnums.BomItemReviewStatus.PENDING);
        BomReviewPackageVO plan = service.planReviewPackage(1L, "APPROVE");
        assertTrue(plan.isExecutable(), plan.getReason());
        assertEquals(3, plan.getActionCount());
        assertEquals(0, plan.getReuseCount());

        bomMap.get(2L).setApproveStatus(ProductEnums.BomStatus.APPROVED.getValue());
        BomReviewPackageVO reusePlan = service.planReviewPackage(1L, "APPROVE");
        assertEquals(1, reusePlan.getReuseCount());
        assertEquals(2, reusePlan.getActionCount());
        assertTrue(reusePlan.getNodes().stream()
                .anyMatch(node -> node.getBomId() == 2L && node.isReuse()));
    }

    @Test
    void sharedDagIsDeduplicated() {
        standardProductChain(ProductEnums.BomStatus.DRAFT, ProductEnums.BomStatus.DRAFT,
                ProductEnums.BomItemReviewStatus.DRAFT);
        item(1L, 12L, 5L, 2L, "make", ProductEnums.BomItemReviewStatus.DRAFT);
        BomReviewPackageVO plan = service.planReviewPackage(1L, "SUBMIT");
        assertTrue(plan.isExecutable(), plan.getReason());
        assertEquals(3, plan.getNodes().size());
        assertEquals(1, plan.getNodes().stream().filter(node -> node.getBomId() == 2L).count());
    }

    @Test
    void cycleBlocksWholePackageInsteadOfTruncating() {
        standardProductChain(ProductEnums.BomStatus.DRAFT, ProductEnums.BomStatus.DRAFT,
                ProductEnums.BomItemReviewStatus.DRAFT);
        rowMap.get(2L).get(0).setSubBomId(1L);
        rowMap.get(2L).add(item(2L, 22L, 5L, 1L, "make", ProductEnums.BomItemReviewStatus.DRAFT));
        BomReviewPackageVO plan = service.planReviewPackage(1L, "SUBMIT");
        assertFalse(plan.isExecutable());
        assertTrue(plan.getReason().contains("BOM_CYCLE"), plan.getReason());
    }

    @Test
    void missingChildAndObsoleteChildBlock() {
        bom(1L, "PRODUCT", 100L, ProductEnums.BomStatus.DRAFT.getValue(), "P-BOM");
        item(1L, 11L, 5L, 99L, "make", ProductEnums.BomItemReviewStatus.DRAFT);
        BomReviewPackageVO missing = service.planReviewPackage(1L, "SUBMIT");
        assertFalse(missing.isExecutable());
        assertTrue(missing.getReason().contains("SUB_BOM_NOT_FOUND"), missing.getReason());

        rowMap.clear();
        standardProductChain(ProductEnums.BomStatus.DRAFT, ProductEnums.BomStatus.OBSOLETE,
                ProductEnums.BomItemReviewStatus.DRAFT);
        BomReviewPackageVO obsolete = service.planReviewPackage(1L, "SUBMIT");
        assertFalse(obsolete.isExecutable());
        assertTrue(obsolete.getBlockingIssues().stream()
                .anyMatch(issue -> issue.getMessage().contains("SUB_BOM_NOT_APPROVED")));
    }

    @Test
    void rejectPlanOnlyContainsRootAndRequiresReviewing() {
        standardProductChain(ProductEnums.BomStatus.REVIEWING, ProductEnums.BomStatus.REVIEWING,
                ProductEnums.BomItemReviewStatus.PENDING);
        BomReviewPackageVO plan = service.planReviewPackage(1L, "REJECT");
        assertTrue(plan.isExecutable());
        assertEquals(1, plan.getNodes().size());
        assertEquals(1L, plan.getNodes().get(0).getBomId());
        bomMap.get(1L).setApproveStatus(ProductEnums.BomStatus.DRAFT.getValue());
        assertFalse(service.planReviewPackage(1L, "REJECT").isExecutable());
    }

    @Test
    void invalidActionIsRejected() {
        standardProductChain(ProductEnums.BomStatus.DRAFT, ProductEnums.BomStatus.DRAFT,
                ProductEnums.BomItemReviewStatus.DRAFT);
        assertThrows(com.jjx.common.exception.BusinessException.class,
                () -> service.planReviewPackage(1L, "APPROVE_ALL"));
    }
}
