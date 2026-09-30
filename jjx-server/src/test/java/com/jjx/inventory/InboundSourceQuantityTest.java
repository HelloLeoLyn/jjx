package com.jjx.inventory;

import com.jjx.inventory.domain.InventoryInboundOrder;
import com.jjx.inventory.dto.vo.InboundLotSummaryVO;
import com.jjx.inventory.dto.vo.InboundVO;
import com.jjx.inventory.mapper.InventoryInboundItemMapper;
import com.jjx.inventory.mapper.InventoryInboundOrderMapper;
import com.jjx.inventory.service.impl.InventoryInboundServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 入库单「来源批次数量 / 本单数量」只读投影（dev-20260930-015）。
 *
 * <p>口径：来源批次数量取来源成品检验批的原批数量，本单数量仍是入库单自身合计；
 * 来源缺失/不完整时只留空（null），**不用本单数量兜底**，也不修改任何单据数量。</p>
 */
@ExtendWith(MockitoExtension.class)
class InboundSourceQuantityTest {

    @Mock
    private InventoryInboundOrderMapper inboundOrderMapper;

    @Mock
    private InventoryInboundItemMapper inboundItemMapper;

    @InjectMocks
    private InventoryInboundServiceImpl service;

    /** WO260930001-FI01 场景：来源批 50（合格 45 / 不合格 5），本单入库 45。 */
    @Test
    void sourceLotTotalsAreSeparateFromDocumentQuantity() {
        when(inboundOrderMapper.selectById(1L)).thenReturn(order(1L, "45"));
        when(inboundItemMapper.selectByInboundId(1L)).thenReturn(List.of());
        when(inboundItemMapper.selectSourceLots(eq(List.of(1L)), eq("FQC")))
                .thenReturn(List.of(lot(1L, 9L, "QL260930003", "50", "45", "5")));
        when(inboundItemMapper.selectLotInboundDocuments(any())).thenReturn(List.of());
        when(inboundItemMapper.selectLotScrapDocuments(any())).thenReturn(List.of());

        InboundVO vo = service.getDetail(1L);

        assertNotNull(vo);
        // 来源批次数量 = 检验批原批数，不是本单数量
        assertEquals(0, new BigDecimal("50").compareTo(vo.getSourceLotQuantity()));
        assertEquals(0, new BigDecimal("5").compareTo(vo.getSourceRejectedQuantity()));
        // 本单数量保持入库单自身合计，未被来源批总量覆盖
        assertEquals(0, new BigDecimal("45").compareTo(vo.getTotalQuantity()));
        assertEquals(1, vo.getSourceLots().size());
        assertEquals("QL260930003", vo.getSourceLots().get(0).getLotNo());
    }

    /** 同一入库单多条明细挂同一检验批时，来源批总量只能算一次（按 lotId 去重）。 */
    @Test
    void duplicateLotRowsAreDeduplicatedWhenSumming() {
        when(inboundOrderMapper.selectById(1L)).thenReturn(order(1L, "90"));
        when(inboundItemMapper.selectByInboundId(1L)).thenReturn(List.of());
        when(inboundItemMapper.selectSourceLots(eq(List.of(1L)), eq("FQC")))
                .thenReturn(List.of(
                        lot(1L, 9L, "QL260930003", "50", "45", "5"),
                        lot(1L, 9L, "QL260930003", "50", "45", "5")));
        when(inboundItemMapper.selectLotInboundDocuments(any())).thenReturn(List.of());
        when(inboundItemMapper.selectLotScrapDocuments(any())).thenReturn(List.of());

        InboundVO vo = service.getDetail(1L);

        assertEquals(1, vo.getSourceLots().size());
        assertEquals(0, new BigDecimal("50").compareTo(vo.getSourceLotQuantity()));
        assertEquals(0, new BigDecimal("5").compareTo(vo.getSourceRejectedQuantity()));
    }

    /** 来源批数量缺失时不得伪造总数：列表/详情显示「—」，但仍能展示已知批次。 */
    @Test
    void incompleteSourceDoesNotFabricateTotals() {
        when(inboundOrderMapper.selectById(1L)).thenReturn(order(1L, "45"));
        when(inboundItemMapper.selectByInboundId(1L)).thenReturn(List.of());
        when(inboundItemMapper.selectSourceLots(eq(List.of(1L)), eq("FQC")))
                .thenReturn(List.of(lot(1L, 9L, "QL260930003", null, null, null)));
        when(inboundItemMapper.selectLotInboundDocuments(any())).thenReturn(List.of());
        when(inboundItemMapper.selectLotScrapDocuments(any())).thenReturn(List.of());

        InboundVO vo = service.getDetail(1L);

        assertNull(vo.getSourceLotQuantity());
        assertNull(vo.getSourceRejectedQuantity());
        assertEquals(0, new BigDecimal("45").compareTo(vo.getTotalQuantity()));
        assertEquals(1, vo.getSourceLots().size());
    }

    private static InventoryInboundOrder order(Long inboundId, String totalQuantity) {
        InventoryInboundOrder order = new InventoryInboundOrder();
        order.setInboundId(inboundId);
        order.setTotalQuantity(new BigDecimal(totalQuantity));
        return order;
    }

    private static InboundLotSummaryVO lot(Long inboundId, Long lotId, String lotNo,
                                           String lotQuantity, String qualifiedQuantity, String rejectedQuantity) {
        InboundLotSummaryVO row = new InboundLotSummaryVO();
        row.setInboundId(inboundId);
        row.setLotId(lotId);
        row.setLotNo(lotNo);
        row.setLotQuantity(lotQuantity == null ? null : new BigDecimal(lotQuantity));
        row.setQualifiedQuantity(qualifiedQuantity == null ? null : new BigDecimal(qualifiedQuantity));
        row.setRejectedQuantity(rejectedQuantity == null ? null : new BigDecimal(rejectedQuantity));
        return row;
    }
}
