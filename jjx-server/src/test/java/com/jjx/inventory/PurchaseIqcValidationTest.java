package com.jjx.inventory;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryInboundItem;
import com.jjx.production.domain.dto.InspectionItemDTO;
import com.jjx.purchase.domain.entity.PurchaseOrderItem;
import com.jjx.purchase.service.impl.PurchaseOrderServiceImpl;
import com.jjx.inventory.service.impl.InventoryInboundServiceImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PurchaseIqcValidationTest {

    @Test
    void purchasePriceMustBePositive() throws Exception {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setMaterialCode("M-001");
        item.setUnitPrice(BigDecimal.ZERO);

        InvocationTargetException error = assertThrows(InvocationTargetException.class,
                () -> purchasePriceValidator().invoke(null, List.of(item)));
        assertInstanceOf(BusinessException.class, error.getCause());

        item.setUnitPrice(new BigDecimal("0.01"));
        assertDoesNotThrow(() -> purchasePriceValidator().invoke(null, List.of(item)));
    }


    @Test
    void iqcChecksCannotBeEmpty() throws Exception {
        InventoryInboundItem inboundItem = new InventoryInboundItem();
        inboundItem.setMaterialCode("M-003");
        InvocationTargetException error = assertThrows(InvocationTargetException.class,
                () -> iqcCheckValidator().invoke(null, inboundItem, List.of()));
        assertInstanceOf(BusinessException.class, error.getCause());
    }

    @Test
    void iqcFailItemWithoutDefectNumbersIsRejected() throws Exception {
        InventoryInboundItem inboundItem = new InventoryInboundItem();
        inboundItem.setMaterialCode("M-004");
        InspectionItemDTO check = new InspectionItemDTO();
        check.setCheckItem("外观");
        check.setResult("fail");

        InvocationTargetException error = assertThrows(InvocationTargetException.class,
                () -> iqcCheckValidator().invoke(null, inboundItem, List.of(check)));
        assertInstanceOf(BusinessException.class, error.getCause());
        org.junit.jupiter.api.Assertions.assertTrue(error.getCause().getMessage().contains("CR/MA/MI"));
    }

    @Test
    void iqcPassItemWithDefectNumbersIsRejected() throws Exception {
        InventoryInboundItem inboundItem = new InventoryInboundItem();
        inboundItem.setMaterialCode("M-005");
        InspectionItemDTO check = new InspectionItemDTO();
        check.setCheckItem("外观");
        check.setResult("pass");
        check.setMaQuantity(BigDecimal.ONE);

        InvocationTargetException error = assertThrows(InvocationTargetException.class,
                () -> iqcCheckValidator().invoke(null, inboundItem, List.of(check)));
        assertInstanceOf(BusinessException.class, error.getCause());
        org.junit.jupiter.api.Assertions.assertTrue(error.getCause().getMessage().contains("必须为不合格"));
    }

    @Test
    void iqcConsistentItemsPass() throws Exception {
        InventoryInboundItem inboundItem = new InventoryInboundItem();
        inboundItem.setMaterialCode("M-006");
        InspectionItemDTO pass = new InspectionItemDTO();
        pass.setCheckItem("外观");
        pass.setResult("pass");
        InspectionItemDTO fail = new InspectionItemDTO();
        fail.setCheckItem("尺寸");
        fail.setResult("fail");
        fail.setCrQuantity(BigDecimal.ONE);

        assertDoesNotThrow(() -> iqcCheckValidator().invoke(null, inboundItem, List.of(pass, fail)));
    }

    private Method purchasePriceValidator() throws Exception {
        Method method = PurchaseOrderServiceImpl.class.getDeclaredMethod("validateOrderItemPrices", List.class);
        method.setAccessible(true);
        return method;
    }

    private Method iqcCheckValidator() throws Exception {
        Method method = InventoryInboundServiceImpl.class.getDeclaredMethod(
                "validateIqcInspectionItems", InventoryInboundItem.class, List.class);
        method.setAccessible(true);
        return method;
    }
}
