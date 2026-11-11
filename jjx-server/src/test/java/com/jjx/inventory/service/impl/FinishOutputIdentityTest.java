package com.jjx.inventory.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.enums.InventoryItemTypeEnum;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.production.domain.entity.ProductionOrder;
import org.junit.jupiter.api.Test;

/** 完工入库产出身份（dev-20261111-008）：半成品不得冒充成品入库。 */
class FinishOutputIdentityTest {

    private final InventoryMaterialMapper materialMapper = mock(InventoryMaterialMapper.class);

    private ProductionOrder order(String outputType, Long outputId) {
        ProductionOrder order = new ProductionOrder();
        order.setOrderNo("WO-TEST");
        order.setOutputType(outputType);
        order.setOutputId(outputId);
        order.setProductId(9L);
        order.setProductCode("P009");
        order.setProductName("成品");
        return order;
    }

    @Test
    void productOrderKeepsProductIdentity() {
        FinishOutputIdentity.Resolved r = FinishOutputIdentity.resolve(order(null, null), materialMapper);
        assertEquals(InventoryItemTypeEnum.PRODUCT, r.itemType());
        assertEquals(9L, r.sourceId());
        assertEquals("P009", r.code());
        assertEquals("PCS", r.unit());
    }

    @Test
    void materialOrderUsesMaterialIdentity() {
        InventoryMaterial material = new InventoryMaterial();
        material.setMaterialId(77L);
        material.setMaterialCode("SFG000001");
        material.setMaterialName("半成品A");
        material.setUnit("M");
        when(materialMapper.selectById(77L)).thenReturn(material);

        FinishOutputIdentity.Resolved r = FinishOutputIdentity.resolve(order("MATERIAL", 77L), materialMapper);
        assertEquals(InventoryItemTypeEnum.MATERIAL, r.itemType());
        assertEquals(77L, r.sourceId());
        assertEquals("SFG000001", r.code());
        assertEquals("M", r.unit());
    }

    @Test
    void materialOrderWithoutOutputIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> FinishOutputIdentity.resolve(order("MATERIAL", null), materialMapper));
        assertEquals(true, ex.getMessage().contains("缺少产出对象"));
    }

    @Test
    void unknownOutputTypeIsRejected() {
        assertThrows(BusinessException.class,
                () -> FinishOutputIdentity.resolve(order("FOO", 1L), materialMapper));
    }
}
