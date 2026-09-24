package com.jjx.quality;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.service.InventoryInboundService;
import com.jjx.quality.domain.entity.QualityLot;
import com.jjx.quality.dto.QualityLotCreateDTO;
import com.jjx.quality.enums.QualityLotStatusEnum;
import com.jjx.quality.mapper.QualityLotMapper;
import com.jjx.quality.service.QualityLotService;
import com.jjx.quality.service.QualityNcrService;
import com.jjx.quality.service.impl.QualityFinishServiceImpl;
import com.jjx.quality.service.impl.QualityLotServiceImpl;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 并发回归（dev-20260923-013）：
 * ① 同一批并发复检 → 只产生一个 v(n+1) 新版本；
 * ② 同一批并发重开 → 只有第一次生效（CLOSED → 已判定）。
 *
 * 守卫口径（dev-20260921-030 / dev-20260922-030）：
 *   两个入口都先 lockLot（SELECT ... FOR UPDATE，行锁串行化「读-判-写」），
 *   再校验状态 + isLatestVersion；并发第二个请求在行锁释放后重读，状态/版本已变 → 拒绝。
 * 本测试固化这层语义：把「第二个请求看到的行」喂给入口，断言其被拒绝且不产生副作用。
 */
class QualityLotConcurrencyGuardTest {

    private static final Long LOT_ID = 7L;

    private final QualityLotService qualityLotService = mock(QualityLotService.class);
    private final QualityNcrService qualityNcrService = mock(QualityNcrService.class);
    private final QualityLotMapper lotMapper = mock(QualityLotMapper.class);
    private final InventoryInboundService inventoryInboundService = mock(InventoryInboundService.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    /** dev-20260924-004：不良件级服务（联动校验用；并发护栏测试不涉及件级写入） */
    private final com.jjx.quality.service.QualityNcrPieceService qualityNcrPieceService =
            mock(com.jjx.quality.service.QualityNcrPieceService.class);
    private final QualityFinishServiceImpl finishService = new QualityFinishServiceImpl(
            qualityLotService, qualityNcrService, lotMapper, inventoryInboundService, jdbcTemplate,
            qualityNcrPieceService);

    private final com.jjx.quality.mapper.QualityLotItemMapper lotItemMapper =
            mock(com.jjx.quality.mapper.QualityLotItemMapper.class);
    private final com.jjx.quality.mapper.QualityNcrMapper ncrMapper =
            mock(com.jjx.quality.mapper.QualityNcrMapper.class);
    private final com.jjx.quality.mapper.QualityNcrActionMapper ncrActionMapper =
            mock(com.jjx.quality.mapper.QualityNcrActionMapper.class);
    private final com.jjx.framework.common.RedisSequenceService redisSequenceService =
            mock(com.jjx.framework.common.RedisSequenceService.class);
    private final QualityLotServiceImpl lotService = new QualityLotServiceImpl(
            lotMapper, lotItemMapper, ncrMapper, ncrActionMapper, redisSequenceService);

    // ==================== ① 同批并发复检 ====================

    @Test
    void firstReinspectCreatesSingleNextVersion() {
        QualityLot judged = judgedLot();
        when(qualityLotService.lockLot(LOT_ID)).thenReturn(judged);
        when(qualityLotService.isLatestVersion(LOT_ID)).thenReturn(true);
        when(qualityLotService.listItems(LOT_ID)).thenReturn(Collections.emptyList());
        QualityLot created = new QualityLot();
        created.setLotId(9L);
        created.setLotNo("QL260923009");
        created.setVersion(2);
        when(qualityLotService.createLot(any(QualityLotCreateDTO.class))).thenReturn(created);

        QualityLot result = finishService.reinspectLot(LOT_ID);

        assertEquals(9L, result.getLotId());
        org.mockito.ArgumentCaptor<QualityLotCreateDTO> captor =
                org.mockito.ArgumentCaptor.forClass(QualityLotCreateDTO.class);
        verify(qualityLotService, times(1)).createLot(captor.capture());
        assertEquals(LOT_ID, captor.getValue().getParentLotId(), "新版本必须挂在原批下");
        assertEquals(2, captor.getValue().getVersion(), "复检版本号 = 原版本 + 1");
        assertEquals("FQC", captor.getValue().getLotType(), "复检延续原批类型");
        // 换代时按 022 口径处理原批那张入库单（未过账作废 / 已过账红冲）
        verify(inventoryInboundService).handleSupersededLotInbound(LOT_ID, "复检换代：" + judged.getLotNo());
        // 守卫必须经由行锁读取（并发串行化的唯一支点）
        verify(qualityLotService).lockLot(LOT_ID);
    }

    @Test
    void secondReinspectOnSupersededLotIsRejectedWithoutSecondCopy() {
        // 第二个并发请求在行锁释放后重读：该批已有后继版本（第一次请求刚建的 v2）
        when(qualityLotService.lockLot(LOT_ID)).thenReturn(judgedLot());
        when(qualityLotService.isLatestVersion(LOT_ID)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> finishService.reinspectLot(LOT_ID));

        assertTrue(ex.getMessage().contains("该批已有复检新版本"), ex.getMessage());
        // 不产生第二个新版本，也不重复处理原批入库单
        verify(qualityLotService, never()).createLot(any(QualityLotCreateDTO.class));
        verifyNoInteractions(inventoryInboundService);
        verify(qualityLotService).lockLot(LOT_ID);
    }

    @Test
    void reinspectOnNotJudgedLotStaysRejected() {
        QualityLot inspecting = judgedLot();
        inspecting.setStatus(QualityLotStatusEnum.INSPECTING.getCode());
        when(qualityLotService.lockLot(LOT_ID)).thenReturn(inspecting);

        BusinessException ex = assertThrows(BusinessException.class, () -> finishService.reinspectLot(LOT_ID));

        assertTrue(ex.getMessage().contains("只有已判定的检验批可以复检"), ex.getMessage());
        verify(qualityLotService, never()).createLot(any(QualityLotCreateDTO.class));
    }

    // ==================== ② 并发重开 ====================

    @Test
    void firstReopenMovesClosedLotBackToJudged() {
        QualityLot closed = judgedLot();
        closed.setStatus(QualityLotStatusEnum.CLOSED.getCode());
        closed.setStoredQuantity(new BigDecimal("98"));
        when(lotMapper.selectForUpdate(LOT_ID)).thenReturn(closed);
        when(lotMapper.selectCount(any())).thenReturn(0L);
        when(lotMapper.updateById(any(QualityLot.class))).thenReturn(1);

        QualityLot reopened = lotService.reopenLot(LOT_ID, "入库后发现外观异常");

        assertEquals(QualityLotStatusEnum.JUDGED.getCode(), reopened.getStatus());
        assertTrue(reopened.getRemark().contains("【重开】"), reopened.getRemark());
        assertTrue(reopened.getRemark().contains("入库后发现外观异常"), reopened.getRemark());
        verify(lotMapper).updateById(closed);
    }

    @Test
    void secondReopenSeesAlreadyReopenedLotAndIsRejected() {
        // 第二个并发请求重读：第一次请求已把状态改成「已判定」，不再是 CLOSED
        QualityLot alreadyReopened = judgedLot();
        alreadyReopened.setStatus(QualityLotStatusEnum.JUDGED.getCode());
        when(lotMapper.selectForUpdate(LOT_ID)).thenReturn(alreadyReopened);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> lotService.reopenLot(LOT_ID, "重复点击"));

        assertTrue(ex.getMessage().contains("只有已关闭的检验批可以重开"), ex.getMessage());
        verify(lotMapper, never()).updateById(any(QualityLot.class));
    }

    @Test
    void reopenRequiresReasonForAuditTrail() {
        QualityLot closed = judgedLot();
        closed.setStatus(QualityLotStatusEnum.CLOSED.getCode());
        when(lotMapper.selectForUpdate(LOT_ID)).thenReturn(closed);
        when(lotMapper.selectCount(any())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class, () -> lotService.reopenLot(LOT_ID, "   "));

        assertTrue(ex.getMessage().contains("重开必须填写原因"), ex.getMessage());
        verify(lotMapper, never()).updateById(any(QualityLot.class));
    }

    @Test
    void reopenOnSupersededVersionIsRejected() {
        QualityLot closed = judgedLot();
        closed.setStatus(QualityLotStatusEnum.CLOSED.getCode());
        when(lotMapper.selectForUpdate(LOT_ID)).thenReturn(closed);
        when(lotMapper.selectCount(any())).thenReturn(1L); // 已有后继版本

        BusinessException ex = assertThrows(BusinessException.class,
                () -> lotService.reopenLot(LOT_ID, "重开旧版本"));

        assertTrue(ex.getMessage().contains("请对最新版本操作"), ex.getMessage());
        verify(lotMapper, never()).updateById(any(QualityLot.class));
    }

    // ==================== 行锁与事务契约 ====================

    @Test
    void lockingReadsAndTransactionsRemainInPlace() throws Exception {
        // 行锁 SQL 不许退化：去掉 FOR UPDATE 就是并发穿透
        Select lotLock = QualityLotMapper.class.getMethod("selectForUpdate", Long.class)
                .getAnnotation(Select.class);
        assertNotNull(lotLock, "QualityLotMapper.selectForUpdate 必须仍是行锁查询");
        assertTrue(String.join(" ", Arrays.asList(lotLock.value())).toUpperCase().contains("FOR UPDATE"),
                "复检/重开的行锁查询必须带 FOR UPDATE");

        assertRollbackForException(QualityFinishServiceImpl.class
                .getMethod("reinspectLot", Long.class));
        assertRollbackForException(QualityLotServiceImpl.class
                .getMethod("reopenLot", Long.class, String.class));
    }

    private static void assertRollbackForException(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertNotNull(transactional, method + " 必须在事务内执行");
        assertTrue(Arrays.asList(transactional.rollbackFor()).contains(Exception.class),
                method + " 必须对受检/非受检异常都回滚");
    }

    private static QualityLot judgedLot() {
        QualityLot lot = new QualityLot();
        lot.setLotId(LOT_ID);
        lot.setLotNo("QL260923007");
        lot.setLotType("FQC");
        lot.setOrderId(2L);
        lot.setStatus(QualityLotStatusEnum.JUDGED.getCode());
        lot.setLotQuantity(new BigDecimal("98"));
        lot.setPassQuantity(new BigDecimal("98"));
        lot.setStoredQuantity(BigDecimal.ZERO);
        lot.setVersion(1);
        lot.setDelFlag(0);
        return lot;
    }
}
