package com.jjx.inventory;

import com.jjx.inventory.service.impl.InventoryInboundServiceImpl;
import com.jjx.inventory.service.impl.InventoryOutboundServiceImpl;
import com.jjx.inventory.service.impl.InventoryTransferServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryTransactionContractTest {

    @Test void inboundConfirmRollsBackForAnyException() throws Exception {
        assertRollbackForException(InventoryInboundServiceImpl.class.getMethod("confirm", Long.class, Long.class, String.class));
    }

    @Test void inboundProductionCreationRollsBackForAnyException() throws Exception {
        assertRollbackForException(InventoryInboundServiceImpl.class.getMethod("createFromProduction", Long.class));
    }

    @Test void outboundConfirmRollsBackForAnyException() throws Exception {
        assertRollbackForException(InventoryOutboundServiceImpl.class.getMethod("confirm", Long.class, Long.class, String.class));
    }

    @Test void transferOutRollsBackForAnyException() throws Exception {
        assertRollbackForException(InventoryTransferServiceImpl.class.getMethod("confirmOut", Long.class, Long.class, String.class));
    }

    @Test void transferInRollsBackForAnyException() throws Exception {
        assertRollbackForException(InventoryTransferServiceImpl.class.getMethod("confirmIn", Long.class, Long.class, String.class));
    }

    @Test void transferCancellationRollsBackForAnyException() throws Exception {
        assertRollbackForException(InventoryTransferServiceImpl.class.getMethod("cancel", Long.class, String.class));
    }

    private static void assertRollbackForException(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertNotNull(transactional, method + " must remain transactional");
        assertTrue(Arrays.asList(transactional.rollbackFor()).contains(Exception.class),
                method + " must roll back for checked and unchecked exceptions");
    }
}
