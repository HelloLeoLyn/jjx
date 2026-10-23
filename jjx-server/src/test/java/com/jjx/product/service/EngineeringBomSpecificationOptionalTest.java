package com.jjx.product.service;

import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.impl.EngineeringBomServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 空规格的提交/批准体检回归；Mapper 隔离，不访问业务库。 */
@ExtendWith(MockitoExtension.class)
class EngineeringBomSpecificationOptionalTest {
    @InjectMocks EngineeringBomServiceImpl service;
    @Mock EngineeringBomMapper bomMapper;
    @Mock EngineeringBomItemMapper itemMapper;
    @Mock ProductMapper productMapper;
    @Mock InventoryMaterialMapper materialMapper;

    private EngineeringBomItem item;

    @BeforeEach
    void setUp() {
        EngineeringBom bom = new EngineeringBom();
        bom.setBomId(1L);
        bom.setProductId(2L);
        when(bomMapper.selectById(1L)).thenReturn(bom);
        when(productMapper.selectById(2L)).thenReturn(new Product());
        item = new EngineeringBomItem();
        item.setItemId(3L);
        item.setBomId(1L);
        item.setMaterialName("通用材料");
        item.setUnit("PCS");
        item.setQuantity(BigDecimal.ONE);
        item.setBaseQty(BigDecimal.ONE);
        item.setModuleQty(BigDecimal.ONE);
        item.setReviewStatus(ProductEnums.BomItemReviewStatus.PENDING.getValue());
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "300g"})
    void submitAcceptsOptionalSpecificationWithoutMaterialMatch(String specification) {
        item.setSpecification(specification);
        assertTrue(service.checkBomForSubmit(1L).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "300g"})
    void approveAcceptsOptionalSpecificationForReviewedMatchedMaterial(String specification) {
        item.setSpecification(specification);
        item.setReviewStatus(ProductEnums.BomItemReviewStatus.APPROVED.getValue());
        item.setMaterialId(4L);
        when(materialMapper.selectById(4L)).thenReturn(new InventoryMaterial());
        assertTrue(service.checkBomForApprove(1L).isEmpty());
    }

    @Test
    void submitStillRejectsMissingRequiredContent() {
        item.setMaterialName(" ");
        item.setUnit(null);
        item.setQuantity(BigDecimal.ZERO);
        item.setModuleQty(null);
        item.setBaseQty(null);
        var fields = service.checkBomForSubmit(1L).stream()
                .map(issue -> issue.getField()).collect(Collectors.toSet());
        assertEquals(java.util.Set.of("materialName", "unit", "quantity", "moduleQty", "baseQty"), fields);
    }

    @Test
    void approveStillRequiresReviewedAndMatchedMaterial() {
        var fields = service.checkBomForApprove(1L).stream()
                .map(issue -> issue.getField()).collect(Collectors.toSet());
        assertEquals(java.util.Set.of("reviewStatus", "materialId"), fields);
    }
}
