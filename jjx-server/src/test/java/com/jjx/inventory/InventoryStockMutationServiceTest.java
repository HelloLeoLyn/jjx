package com.jjx.inventory;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.domain.InventoryTransaction;
import com.jjx.inventory.mapper.InventoryStockItemMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.mapper.InventoryTransactionMapper;
import com.jjx.inventory.service.impl.InventoryStockMutationServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryStockMutationServiceTest {

    private final InventoryStockItemMapper itemMapper = mock(InventoryStockItemMapper.class);
    private final InventoryStockMapper stockMapper = mock(InventoryStockMapper.class);
    private final InventoryTransactionMapper transactionMapper = mock(InventoryTransactionMapper.class);
    private final InventoryStockMutationServiceImpl service =
            new InventoryStockMutationServiceImpl(itemMapper, stockMapper, transactionMapper);

    @Test
    void writesBatchSummaryAndTransactionAsOneMutation() {
        InventoryStockItem stock = stock("10");
        when(itemMapper.selectByIdForUpdate(1L)).thenReturn(stock);
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTransactionType("INBOUND");

        service.applyDelta(stock, new BigDecimal("2.5"), transaction);

        assertEquals(new BigDecimal("12.5"), stock.getQuantity());
        assertEquals(new BigDecimal("10"), transaction.getBeforeQuantity());
        assertEquals(new BigDecimal("12.5"), transaction.getAfterQuantity());
        verify(itemMapper).updateById(stock);
        verify(stockMapper).refreshSummaryByInventoryItemId(100L);
        verify(transactionMapper).insert(transaction);
    }

    @Test
    void rejectsNegativeInventoryBeforeWriting() {
        InventoryStockItem stock = stock("1");
        when(itemMapper.selectByIdForUpdate(1L)).thenReturn(stock);
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTransactionType("OUTBOUND");

        assertThrows(BusinessException.class,
                () -> service.applyDelta(stock, new BigDecimal("-2"), transaction));
    }

    @Test
    void backfillsLegacyBatchInventoryIdentityInsideBoundary() {
        InventoryStockItem requested = stock("1");
        InventoryStockItem locked = stock("1");
        locked.setInventoryItemId(null);
        when(itemMapper.selectByIdForUpdate(1L)).thenReturn(locked);
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTransactionType("ADJUST");

        service.applyDelta(requested, BigDecimal.ONE, transaction);

        assertEquals(100L, locked.getInventoryItemId());
        verify(stockMapper).refreshSummaryByInventoryItemId(100L);
    }

    @Test
    void mutationBoundaryRollsBackForAnyException() throws Exception {
        Transactional transactional = InventoryStockMutationServiceImpl.class
                .getMethod("applyDelta", InventoryStockItem.class, BigDecimal.class, InventoryTransaction.class)
                .getAnnotation(Transactional.class);
        assertTrue(Arrays.asList(transactional.rollbackFor()).contains(Exception.class));
    }

    private InventoryStockItem stock(String quantity) {
        InventoryStockItem stock = new InventoryStockItem();
        stock.setItemId(1L);
        stock.setInventoryItemId(100L);
        stock.setBatchNo("B-1");
        stock.setQuantity(new BigDecimal(quantity));
        return stock;
    }
}
