package com.jjx.inventory;

import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.inventory.service.impl.BomMaterialDemandService;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 006统一适配后：原始比例必须为50，缺依据须显式报告而不是伪造数量。 */
class LegacyBomUnitCompatibilityTest {
    @ParameterizedTest @ValueSource(booleans={true,false})
    void unconfirmedHistoricalUnitsDoNotIntroduceNewFailures(boolean alert) {
        var materials = mock(InventoryMaterialMapper.class);
        var archive = new InventoryMaterial(); archive.setUnit("PCS");
        when(materials.selectById(109L)).thenReturn(archive);
        var service = new BomMaterialDemandService(materials, mock(EngineeringBomMapper.class), mock(EngineeringBomItemMapper.class));
        var item = new EngineeringBomItem(); item.setMaterialId(109L); item.setUnit("PCS");
        item.setModuleQty(new BigDecimal("6")); item.setBaseQty(BigDecimal.ONE);
        item.setQuantity(new BigDecimal("0.1667")); item.setActualIssueQty(new BigDecimal("0.1667"));
        var demand = service.newDemand(alert); demand.add(java.util.List.of(item), new BigDecimal("300"));
        demand.requireComplete();
        assertEquals(0,new BigDecimal("50").compareTo(demand.rounded().get(109L)));
        archive.setUnit("M");
        var incomplete = service.newDemand(alert); incomplete.add(java.util.List.of(item), new BigDecimal("300"));
        assertFalse(incomplete.issues().isEmpty()); assertTrue(incomplete.rounded().isEmpty());
    }
}
