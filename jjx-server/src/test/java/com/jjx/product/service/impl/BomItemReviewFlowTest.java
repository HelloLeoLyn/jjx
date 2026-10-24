package com.jjx.product.service.impl;

import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.product.domain.converter.EngineeringBomConverter;
import com.jjx.product.domain.dto.BomItemReviewDTO;
import com.jjx.product.domain.dto.EngineeringBomDTO;
import com.jjx.product.domain.dto.EngineeringBomItemDTO;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.vo.BomCheckIssueVO;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.mapper.ProductStandardProcessMapper;
import com.jjx.system.service.ReviewFlowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * BOM 快速模式 + 行级审核：保存按 itemId 合并、已通过行指纹、批准条件、逐行审核流水。
 * dev-20261020-029（隔离 Mockito 单测，不代表生产库事务已运行）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BomItemReviewFlowTest {

    @InjectMocks EngineeringBomServiceImpl service;
    @Mock BomSpecificationService bomSpecificationService;

    @BeforeEach
    void injectSpecificationService() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "bomSpecificationService", bomSpecificationService);
    }
    @Mock EngineeringBomMapper bomMapper;
    @Mock EngineeringBomItemMapper itemMapper;
    @Mock ProductMapper productMapper;
    @Mock EngineeringBomConverter converter;
    @Mock com.jjx.production.mapper.ProductionOrderMapper productionOrderMapper;
    @Mock com.jjx.inventory.mapper.InventoryMaterialMapper inventoryMaterialMapper;
    @Mock ReviewFlowService reviewFlowService;
    @Mock com.jjx.event.EventPublisher eventPublisher;
    @Mock com.jjx.system.service.OperLogChangeRecorder changeRecorder;
    @Mock ProductStandardProcessMapper standardProcessMapper;

    private EngineeringBomItem row(long id, String name, String spec, long materialId,
                                   ProductEnums.BomItemReviewStatus status) {
        EngineeringBomItem item = new EngineeringBomItem();
        item.setItemId(id);
        item.setBomId(1L);
        item.setMaterialId(materialId);
        item.setMaterialCode("M" + materialId);
        item.setMaterialName(name);
        item.setSpecification(spec);
        item.setUnit("PCS");
        item.setBaseQty(BigDecimal.ONE);
        item.setModuleQty(BigDecimal.ONE);
        item.setQuantity(BigDecimal.ONE);
        item.setLossRate(0);
        item.setReviewStatus(status.getValue());
        if (status == ProductEnums.BomItemReviewStatus.APPROVED) {
            item.setApprovedFingerprint(EngineeringBomServiceImpl.fingerprint(item));
        }
        return item;
    }

    private EngineeringBomItemDTO dto(Long itemId, String name, String spec, long materialId) {
        EngineeringBomItemDTO dto = new EngineeringBomItemDTO();
        dto.setItemId(itemId);
        dto.setMaterialId(materialId);
        dto.setMaterialCode("M" + materialId);
        dto.setMaterialName(name);
        dto.setSpecification(spec);
        dto.setUnit("PCS");
        dto.setBaseQty(BigDecimal.ONE);
        dto.setModuleQty(BigDecimal.ONE);
        dto.setLossRate(0);
        return dto;
    }

    private EngineeringBom bom() {
        EngineeringBom bom = new EngineeringBom();
        bom.setBomId(1L);
        bom.setProductId(1L);
        bom.setBomCode("B1");
        bom.setApproveStatus(ProductEnums.BomStatus.DRAFT.getValue());
        bom.setIsCurrent(false);
        bom.setBomVersion("V1.0");
        return bom;
    }

    private void stubUpdate(EngineeringBomItem... stored) {
        EngineeringBom existing = bom();
        when(bomMapper.selectById(1L)).thenReturn(existing);
        when(converter.toEntity(any(EngineeringBomDTO.class))).thenReturn(existing);
        when(bomMapper.updateById(any(EngineeringBom.class))).thenReturn(1);
        when(itemMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(stored)));
        when(itemMapper.updateById(any(EngineeringBomItem.class))).thenReturn(1);
        when(itemMapper.insert(any(EngineeringBomItem.class))).thenReturn(1);
        Product product = new Product();
        product.setProductId(1L);
        when(productMapper.selectById(1L)).thenReturn(product);
    }

    @Test
    void saveKeepsItemIdAndNeverDeletesWholeBom() {
        EngineeringBomItem approved = row(10L, "白卡纸", "300g", 5L,
                ProductEnums.BomItemReviewStatus.APPROVED);
        stubUpdate(approved);
        EngineeringBomDTO payload = new EngineeringBomDTO();
        payload.setBomId(1L);
        payload.setProductId(1L);
        payload.setBomCode("B1");
        payload.setItems(List.of(dto(10L, "白卡纸", "300g", 5L), dto(null, "瓦楞纸", "500g", 6L)));

        service.updateBomWithDetail(payload);

        verify(itemMapper, never()).deleteByBomId(anyLong());
        ArgumentCaptor<EngineeringBomItem> updated = ArgumentCaptor.forClass(EngineeringBomItem.class);
        verify(itemMapper, atLeastOnce()).updateById(updated.capture());
        assertTrue(updated.getAllValues().stream().anyMatch(it -> Long.valueOf(10L).equals(it.getItemId())));
        verify(itemMapper, times(1)).insert(any(EngineeringBomItem.class));
        // 已通过行内容未变：保持审核通过
        assertEquals(ProductEnums.BomItemReviewStatus.APPROVED.getValue(), approved.getReviewStatus());
    }

    @Test
    void changingApprovedRowReturnsItToPending() {
        EngineeringBomItem approved = row(10L, "白卡纸", "300g", 5L,
                ProductEnums.BomItemReviewStatus.APPROVED);
        stubUpdate(approved);
        EngineeringBomDTO payload = new EngineeringBomDTO();
        payload.setBomId(1L);
        payload.setProductId(1L);
        payload.setItems(List.of(dto(10L, "白卡纸", "350g", 5L)));

        service.updateBomWithDetail(payload);

        assertEquals(ProductEnums.BomItemReviewStatus.PENDING.getValue(), approved.getReviewStatus());
        assertNull(approved.getApprovedFingerprint());
    }

    @Test
    void removingApprovedRowMarksPendingRemoveInsteadOfDelete() {
        EngineeringBomItem approved = row(10L, "白卡纸", "300g", 5L,
                ProductEnums.BomItemReviewStatus.APPROVED);
        stubUpdate(approved);
        EngineeringBomDTO payload = new EngineeringBomDTO();
        payload.setBomId(1L);
        payload.setProductId(1L);
        payload.setItems(List.of());

        service.updateBomWithDetail(payload);

        assertEquals(ProductEnums.BomItemReviewStatus.PENDING_REMOVE.getValue(), approved.getReviewStatus());
        assertEquals(Boolean.FALSE, approved.getRemoveConfirmed());
        verify(itemMapper, never()).deleteById(anyLong());
    }

    @Test
    void approveCheckBlocksUnresolvedRowsAndUnlinkedMaterial() {
        EngineeringBom bom = bom();
        bom.setApproveStatus(ProductEnums.BomStatus.REVIEWING.getValue());
        when(bomMapper.selectById(1L)).thenReturn(bom);
        Product product = new Product();
        product.setProductId(1L);
        when(productMapper.selectById(1L)).thenReturn(product);

        EngineeringBomItem pending = row(11L, "瓦楞纸", "500g", 0L,
                ProductEnums.BomItemReviewStatus.PENDING);
        pending.setMaterialId(null);
        when(itemMapper.selectList(any())).thenReturn(List.of(pending));

        List<BomCheckIssueVO> issues = service.checkBomForApprove(1L);
        assertTrue(issues.stream().anyMatch(i -> "reviewStatus".equals(i.getField())));
        assertTrue(issues.stream().anyMatch(i -> "materialId".equals(i.getField())));

        EngineeringBomItem approved = row(12L, "白卡纸", "300g", 5L,
                ProductEnums.BomItemReviewStatus.APPROVED);
        InventoryMaterial material = new InventoryMaterial();
        material.setMaterialId(5L);
        material.setMaterialCode("M5");
        when(inventoryMaterialMapper.selectById(5L)).thenReturn(material);
        when(itemMapper.selectList(any())).thenReturn(List.of(approved));
        assertTrue(service.checkBomForApprove(1L).isEmpty());
    }

    @Test
    void approveCheckRequiresRemovalConfirmation() {
        EngineeringBom bom = bom();
        bom.setApproveStatus(ProductEnums.BomStatus.REVIEWING.getValue());
        when(bomMapper.selectById(1L)).thenReturn(bom);
        Product product = new Product();
        product.setProductId(1L);
        when(productMapper.selectById(1L)).thenReturn(product);

        EngineeringBomItem removing = row(13L, "旧料", "X", 5L,
                ProductEnums.BomItemReviewStatus.PENDING_REMOVE);
        removing.setRemoveConfirmed(false);
        EngineeringBomItem approved = row(12L, "白卡纸", "300g", 5L,
                ProductEnums.BomItemReviewStatus.APPROVED);
        InventoryMaterial material = new InventoryMaterial();
        material.setMaterialId(5L);
        material.setMaterialCode("M5");
        when(inventoryMaterialMapper.selectById(5L)).thenReturn(material);
        when(itemMapper.selectList(any())).thenReturn(List.of(approved, removing));
        assertTrue(service.checkBomForApprove(1L).stream()
                .anyMatch(i -> "removeConfirmed".equals(i.getField())));

        removing.setRemoveConfirmed(true);
        assertTrue(service.checkBomForApprove(1L).isEmpty());
    }

    @Test
    void rowApproveRequiresMaterialAndWritesFlow() {
        EngineeringBom bom = bom();
        bom.setApproveStatus(ProductEnums.BomStatus.REVIEWING.getValue());
        when(bomMapper.selectById(1L)).thenReturn(bom);
        EngineeringBomItem pending = row(14L, "白卡纸", "300g", 5L,
                ProductEnums.BomItemReviewStatus.PENDING);
        when(itemMapper.selectById(14L)).thenReturn(pending);
        InventoryMaterial material = new InventoryMaterial();
        material.setMaterialId(5L);
        material.setMaterialCode("M5");
        when(inventoryMaterialMapper.selectById(5L)).thenReturn(material);
        when(itemMapper.updateById(any(EngineeringBomItem.class))).thenReturn(1);

        BomItemReviewDTO dto = new BomItemReviewDTO();
        dto.setItemId(14L);
        dto.setAction("APPROVE");
        service.reviewItem(dto);

        assertEquals(ProductEnums.BomItemReviewStatus.APPROVED.getValue(), pending.getReviewStatus());
        assertNotNull(pending.getApprovedFingerprint());
        verify(reviewFlowService).record(eq("engineering_bom_item"), eq(14L), eq("APPROVE"),
                anyString(), eq("待审核"), eq("审核通过"), anyString(), isNull());
    }
}
