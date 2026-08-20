package com.jjx.production;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.jjx.production.domain.entity.ProductionQualityInspection;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.domain.vo.QualityInspectionVO;
import com.jjx.production.enums.QualityInspectionResultEnum;
import com.jjx.production.enums.QualityInspectionTypeEnum;
import com.jjx.production.mapper.ProductionQualityInspectionItemMapper;
import com.jjx.production.mapper.ProductionQualityInspectionMapper;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.impl.QualityInspectionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P3-B 回归测试：QualityInspection 数据模型升级（execution_id/work_report_id + DECIMAL 数量）
 * 覆盖：
 *   1. Entity 字段类型（executionId/workReportId Long；数量 BigDecimal）——反射验证
 *   2. create 写入 executionId/workReportId（通过反射访问 service 验证 mapper insert 收到实体）
 *   3. 读取能力：listByExecutionId / listFqcHistory / hasPendingFqc / hasPassFqc
 *   4. 关联一致性校验 checkWorkReportLink（workReportId 非空时校验 execution/order 一致）
 */
@ExtendWith(MockitoExtension.class)
class QualityInspectionP3BTest {

    @Mock ProductionQualityInspectionMapper inspectionMapper;
    @Mock ProductionQualityInspectionItemMapper itemMapper;
    @Mock ProductionOrderMapper productionOrderMapper;
    @Mock ProductionWorkReportMapper workReportMapper;

    private QualityInspectionServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        var ctor = QualityInspectionServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        // 构造参数顺序：inspectionMapper, itemMapper, pdfConfigLoader(null), productionOrderMapper, workReportMapper, executionMapper
        service = (QualityInspectionServiceImpl) ctor.newInstance(inspectionMapper, itemMapper, null, productionOrderMapper, workReportMapper, mock(com.jjx.production.mapper.ProductionOperationExecutionMapper.class));
    }

    // ---------- 1. Entity 字段类型（P3-B 核心：无 Integer/DECIMAL 错配） ----------

    @Test
    void entityHasExecutionAndWorkReportLinkFields() throws Exception {
        Field ex = ProductionQualityInspection.class.getDeclaredField("executionId");
        assertEquals(Long.class, ex.getType());
        Field wr = ProductionQualityInspection.class.getDeclaredField("workReportId");
        assertEquals(Long.class, wr.getType());
    }

    @Test
    void entityQuantityFieldsAreBigDecimal() throws Exception {
        assertEquals(BigDecimal.class, ProductionQualityInspection.class.getDeclaredField("totalQty").getType());
        assertEquals(BigDecimal.class, ProductionQualityInspection.class.getDeclaredField("passQty").getType());
        assertEquals(BigDecimal.class, ProductionQualityInspection.class.getDeclaredField("failQty").getType());
    }

    // ---------- 2. create 写入 executionId/workReportId ----------

    @Test
    void createPersistsExecutionAndWorkReportLink() throws Exception {
        com.jjx.production.domain.dto.QualityInspectionCreateDTO dto = new com.jjx.production.domain.dto.QualityInspectionCreateDTO();
        dto.setInspectionType(QualityInspectionTypeEnum.IPQC.getCode());
        dto.setOrderId(2L);
        dto.setExecutionId(3L);
        dto.setWorkReportId(5L);
        dto.setInspector("质检员A");
        // P3-C：create 会反查 WorkReport 校验关联一致性（workReport.executionId==3 && orderId==2）
        when(workReportMapper.selectById(5L)).thenReturn(sampleWorkReport(5L, 2L, 3L));
        when(inspectionMapper.insert(any(ProductionQualityInspection.class))).thenAnswer(inv -> {
            ProductionQualityInspection e = inv.getArgument(0);
            e.setInspectionId(99L);
            return 1;
        });
        Long id = service.create(dto);
        assertEquals(99L, id);
        org.mockito.ArgumentCaptor<ProductionQualityInspection> cap =
                org.mockito.ArgumentCaptor.forClass(ProductionQualityInspection.class);
        org.mockito.Mockito.verify(inspectionMapper).insert(cap.capture());
        ProductionQualityInspection saved = cap.getValue();
        assertEquals(3L, saved.getExecutionId());
        assertEquals(5L, saved.getWorkReportId());
        assertEquals(2L, saved.getOrderId());
        assertEquals(QualityInspectionResultEnum.PENDING.getCode(), saved.getResult());
    }

    // ---------- 3. 读取能力 ----------

    @Test
    void listByExecutionIdQueriesExecutionFilter() {
        ProductionQualityInspection e = sampleInspection(1L, 2L, 3L, null, QualityInspectionTypeEnum.FQC.getCode(), QualityInspectionResultEnum.PENDING.getCode());
        when(inspectionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(e));
        List<QualityInspectionVO> vos = service.listByExecutionId(3L);
        assertEquals(1, vos.size());
        assertEquals(3L, vos.get(0).getExecutionId());
        assertNull(vos.get(0).getWorkReportId());
    }

    @Test
    void listFqcHistoryOnlyFqcType() {
        ProductionQualityInspection e = sampleInspection(1L, 2L, 3L, null, QualityInspectionTypeEnum.FQC.getCode(), QualityInspectionResultEnum.PASS.getCode());
        when(inspectionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(e));
        List<QualityInspectionVO> vos = service.listFqcHistory(3L);
        assertEquals(1, vos.size());
        assertEquals(QualityInspectionTypeEnum.FQC.getCode(), vos.get(0).getInspectionType());
    }

    @Test
    void hasPendingFqcAndHasPassFqc() {
        when(inspectionMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        assertTrue(service.hasPendingFqc(3L));
        assertTrue(service.hasPassFqc(3L));
        when(inspectionMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        assertFalse(service.hasPendingFqc(3L));
        assertFalse(service.hasPassFqc(3L));
    }

    // ---------- 4. 关联一致性校验 checkWorkReportLink ----------

    private ProductionWorkReport sampleWorkReport(Long reportId, Long orderId, Long executionId) {
        ProductionWorkReport r = new ProductionWorkReport();
        r.setReportId(reportId);
        r.setOrderId(orderId);
        r.setExecutionId(executionId);
        return r;
    }

    @Test
    void checkWorkReportLinkPassesWhenConsistent() {
        when(workReportMapper.selectById(5L)).thenReturn(sampleWorkReport(5L, 2L, 3L));
        assertTrue(service.checkWorkReportLink(5L, 3L, 2L));
    }

    @Test
    void checkWorkReportLinkFailsWhenExecutionMismatch() {
        when(workReportMapper.selectById(5L)).thenReturn(sampleWorkReport(5L, 2L, 3L));
        assertFalse(service.checkWorkReportLink(5L, 99L, 2L));
    }

    @Test
    void checkWorkReportLinkFailsWhenOrderMismatch() {
        when(workReportMapper.selectById(5L)).thenReturn(sampleWorkReport(5L, 2L, 3L));
        assertFalse(service.checkWorkReportLink(5L, 3L, 99L));
    }

    @Test
    void checkWorkReportLinkFailsWhenWorkReportMissingOrParamsNull() {
        when(workReportMapper.selectById(999L)).thenReturn(null);
        assertFalse(service.checkWorkReportLink(999L, 3L, 2L));
        assertFalse(service.checkWorkReportLink(null, 3L, 2L));
        assertFalse(service.checkWorkReportLink(5L, null, 2L));
        assertFalse(service.checkWorkReportLink(5L, 3L, null));
    }

    private ProductionQualityInspection sampleInspection(Long id, Long orderId, Long executionId, Long workReportId,
                                                         String type, String result) {
        ProductionQualityInspection e = new ProductionQualityInspection();
        e.setInspectionId(id);
        e.setInspectionNo("QCI202608190001");
        e.setInspectionType(type);
        e.setOrderId(orderId);
        e.setExecutionId(executionId);
        e.setWorkReportId(workReportId);
        e.setResult(result);
        e.setTotalQty(new BigDecimal("100.0000"));
        e.setPassQty(new BigDecimal("98.0000"));
        e.setFailQty(new BigDecimal("2.0000"));
        return e;
    }
}
