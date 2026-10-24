package com.jjx.product.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.product.domain.dto.BomMaterialBatchLinkDTO;
import com.jjx.product.domain.vo.BomMaterialBatchResultVO;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.mapper.ProductStandardProcessMapper;
import com.jjx.product.domain.converter.EngineeringBomConverter;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.system.service.OperLogChangeRecorder;
import com.jjx.system.service.ReviewFlowService;
import com.jjx.event.EventPublisher;

/** BOM 批量关联物料：逐行校验与部分失败（dev-20261024-020）。 */
class BomMaterialBatchLinkTest {

    private EngineeringBomMapper bomMapper;
    private EngineeringBomItemMapper itemMapper;
    private InventoryMaterialMapper materialMapper;
    private ReviewFlowService reviewFlowService;
    private EngineeringBomServiceImpl service;

    @BeforeEach
    void setUp() {
        bomMapper = mock(EngineeringBomMapper.class);
        itemMapper = mock(EngineeringBomItemMapper.class);
        materialMapper = mock(InventoryMaterialMapper.class);
        reviewFlowService = mock(ReviewFlowService.class);
        service = new EngineeringBomServiceImpl(
                bomMapper, itemMapper, mock(ProductMapper.class), mock(EngineeringBomConverter.class),
                mock(ProductionOrderMapper.class), materialMapper, reviewFlowService,
                mock(EventPublisher.class), mock(OperLogChangeRecorder.class),
                mock(ProductStandardProcessMapper.class));
    }

    private EngineeringBom bom(int status) {
        EngineeringBom bom = new EngineeringBom();
        bom.setBomId(1L);
        bom.setApproveStatus(status);
        return bom;
    }

    private EngineeringBomItem item(long id, long bomId, int reviewStatus, Long materialId) {
        EngineeringBomItem item = new EngineeringBomItem();
        item.setItemId(id);
        item.setBomId(bomId);
        item.setReviewStatus(reviewStatus);
        item.setMaterialId(materialId);
        item.setMaterialName("PET");
        item.setSpecification("0.1");
        return item;
    }

    private BomMaterialBatchLinkDTO dto(long bomId, long itemId, long materialId) {
        BomMaterialBatchLinkDTO dto = new BomMaterialBatchLinkDTO();
        dto.setBomId(bomId);
        BomMaterialBatchLinkDTO.Link link = new BomMaterialBatchLinkDTO.Link();
        link.setItemId(itemId);
        link.setMaterialId(materialId);
        dto.setLinks(List.of(link));
        return dto;
    }

    @Test
    void rejectsWhenBomNotReviewing() {
        when(bomMapper.selectById(1L)).thenReturn(bom(ProductEnums.BomStatus.DRAFT.getValue()));
        assertThrows(BusinessException.class, () -> service.batchLinkMaterials(dto(1L, 10L, 100L)));
        verifyNoInteractions(itemMapper);
    }

    @Test
    void rejectsItemFromAnotherBom() {
        when(bomMapper.selectById(1L)).thenReturn(bom(ProductEnums.BomStatus.REVIEWING.getValue()));
        when(itemMapper.selectById(10L)).thenReturn(item(10L, 2L, ProductEnums.BomItemReviewStatus.PENDING.getValue(), null));
        BomMaterialBatchResultVO result = service.batchLinkMaterials(dto(1L, 10L, 100L));
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailedCount());
        assertTrue(result.getRows().get(0).getMessage().contains("不属于"));
    }

    @Test
    void rejectsPendingRemoveRow() {
        when(bomMapper.selectById(1L)).thenReturn(bom(ProductEnums.BomStatus.REVIEWING.getValue()));
        when(itemMapper.selectById(10L)).thenReturn(item(10L, 1L,
                ProductEnums.BomItemReviewStatus.PENDING_REMOVE.getValue(), null));
        BomMaterialBatchResultVO result = service.batchLinkMaterials(dto(1L, 10L, 100L));
        assertEquals(0, result.getSuccessCount());
        assertTrue(result.getRows().get(0).getMessage().contains("待移除"));
    }

    @Test
    void partialSuccessKeepsSuccessfulRow() {
        when(bomMapper.selectById(1L)).thenReturn(bom(ProductEnums.BomStatus.REVIEWING.getValue()));
        when(itemMapper.selectById(10L)).thenReturn(item(10L, 1L, ProductEnums.BomItemReviewStatus.PENDING.getValue(), null));
        when(itemMapper.selectById(11L)).thenReturn(null);
        InventoryMaterial material = new InventoryMaterial();
        material.setMaterialId(100L);
        material.setMaterialCode("M100");
        material.setMaterialName("PET");
        material.setUnit("KG");
        when(materialMapper.selectById(100L)).thenReturn(material);

        BomMaterialBatchLinkDTO dto = dto(1L, 10L, 100L);
        BomMaterialBatchLinkDTO.Link second = new BomMaterialBatchLinkDTO.Link();
        second.setItemId(11L);
        second.setMaterialId(100L);
        dto.setLinks(List.of(dto.getLinks().get(0), second));

        BomMaterialBatchResultVO result = service.batchLinkMaterials(dto);
        assertEquals(2, result.getTotal());
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailedCount());
        verify(itemMapper).updateById(any(EngineeringBomItem.class));
        verify(reviewFlowService).record(eq("engineering_bom_item"), eq(10L), eq("LINK_MATERIAL"),
                anyString(), anyString(), anyString(), anyString(), isNull());
    }
}
