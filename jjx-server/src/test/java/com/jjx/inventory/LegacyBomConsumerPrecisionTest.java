package com.jjx.inventory;

import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.service.impl.InventoryAlertServiceImpl;
import com.jjx.inventory.service.impl.OrderMaterialReserveServiceImpl;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 旧预警/预占消费者的实际适配方法必须摆脱展示快照；不冒充完整业务API。 */
class LegacyBomConsumerPrecisionTest {
    @ParameterizedTest @ValueSource(booleans={true,false})
    void consumersUseRawRatioAndFractionalLoss(boolean alert) {
        Object service = alert ? mock(InventoryAlertServiceImpl.class, CALLS_REAL_METHODS)
                : mock(OrderMaterialReserveServiceImpl.class, CALLS_REAL_METHODS);
        var mapper = mock(InventoryMaterialMapper.class);
        ReflectionTestUtils.setField(service, "materialMapper", mapper);
        var material = new InventoryMaterial(); material.setUnit("M");
        when(mapper.selectById(109L)).thenReturn(material);
        var item = new EngineeringBomItem(); item.setMaterialId(109L); item.setUnit("M");
        item.setModuleQty(new BigDecimal("6")); item.setBaseQty(BigDecimal.ONE);
        item.setQuantity(new BigDecimal("0.1667")); item.setActualIssueQty(new BigDecimal("0.1667"));
        BigDecimal demand = ReflectionTestUtils.invokeMethod(service,"batchDemand",item,new BigDecimal("300"));
        assertEquals(0,new BigDecimal("50").compareTo(demand));
        item.setLossRate(new BigDecimal("0.5"));
        demand = ReflectionTestUtils.invokeMethod(service,"batchDemand",item,new BigDecimal("300"));
        assertEquals(0,new BigDecimal("50.25").compareTo(demand));
        material.setUnit("M2"); item.setUnitFactor(new BigDecimal("0.195")); item.setUnitFactorBasis("确认宽195mm");
        demand = ReflectionTestUtils.invokeMethod(service,"batchDemand",item,new BigDecimal("300"));
        assertEquals(0,new BigDecimal("9.7988").compareTo(demand));
    }
}
