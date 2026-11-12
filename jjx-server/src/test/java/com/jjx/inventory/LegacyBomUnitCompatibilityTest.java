package com.jjx.inventory;

import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.inventory.service.impl.InventoryAlertServiceImpl;
import com.jjx.inventory.service.impl.OrderMaterialReserveServiceImpl;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** dev-20261112-006核清单位前，旧预警/预占只做损耗类型兼容，不擅自接入严格库存换算。
 * 此处保留的51是已登记的旧算法债务；不是016精确领料的预期（后者必须50）。 */
class LegacyBomUnitCompatibilityTest {
    @ParameterizedTest @ValueSource(booleans={true,false})
    void unconfirmedHistoricalUnitsDoNotIntroduceNewFailures(boolean alert) {
        Object service = alert ? mock(InventoryAlertServiceImpl.class, CALLS_REAL_METHODS)
                : mock(OrderMaterialReserveServiceImpl.class, CALLS_REAL_METHODS);
        var item = new EngineeringBomItem(); item.setMaterialId(109L); item.setUnit("PCS");
        item.setModuleQty(new BigDecimal("6")); item.setBaseQty(BigDecimal.ONE);
        item.setQuantity(new BigDecimal("0.1667")); item.setActualIssueQty(new BigDecimal("0.1667"));
        BigDecimal demand = assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service,
                "batchDemand",item,new BigDecimal("300")));
        assertEquals(0,new BigDecimal("51").compareTo(demand));
        item.setActualIssueQty(null); item.setLossRate(new BigDecimal("0.5"));
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service,"batchDemand",item,new BigDecimal("300")));
    }
}
