package com.jjx.production;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.QualityInspectionCreateDTO;
import com.jjx.production.domain.dto.QualityJudgeDTO;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.domain.entity.ProductionQualityInspection;
import com.jjx.production.domain.vo.QualityInspectionVO;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.enums.QualityInspectionResultEnum;
import com.jjx.production.enums.QualityInspectionTypeEnum;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.mapper.ProductionQualityInspectionMapper;
import com.jjx.production.service.QualityInspectionService;
import com.jjx.production.service.impl.QualityActionServiceImpl;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3-C 回归测试：Quality Action（判定/复检/FQC 自动创建）+ 生产 Gate 联动
 * 覆盖：
 *   1. judge PASS：数量校验 + 写入质量事实 + FQC PASS → order.finishedQuantity=passQty
 *   2. judge FAIL：FQC FAIL → 最后有效 Execution 恢复 EXECUTING（可继续报工/可再次完成）
 *   3. 不可变：已判定(PASS/FAIL) 禁止再次 judge
 *   4. 数量规则：负数拒绝；pass+fail > total 拒绝；PASS 时 passQty=0 拒绝
 *   5. reinspect：新建 PENDING 复检单（不覆盖历史）
 *   6. createFqcForExecution：幂等（已有 PENDING 不重复）；FQC 形态（orderId+executionId+workReportId=null）
 *   7. createInspection：workReportId 非空 → 后端反查校验关联一致性
 */
@ExtendWith(MockitoExtension.class)
class QualityActionP3CTest {

    @Mock ProductionQualityInspectionMapper inspectionMapper;
    @Mock ProductionOperationExecutionMapper executionMapper;
    @Mock ProductionOrderMapper productionOrderMapper;
    @Mock QualityInspectionService qualityInspectionService;

    private QualityActionServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        var ctor = QualityActionServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        service = (QualityActionServiceImpl) ctor.newInstance(inspectionMapper, executionMapper,
                productionOrderMapper, qualityInspectionService);
    }

    private ProductionQualityInspection pendingFqc(Long id, Long orderId, Long executionId) {
        ProductionQualityInspection q = new ProductionQualityInspection();
        q.setInspectionId(id);
        q.setInspectionNo("QCI202608190001");
        q.setInspectionType(QualityInspectionTypeEnum.FQC.getCode());
        q.setOrderId(orderId);
        q.setExecutionId(executionId);
        q.setResult(QualityInspectionResultEnum.PENDING.getCode());
        return q;
    }

    private QualityJudgeDTO passDto(String total, String pass, String fail) {
        QualityJudgeDTO dto = new QualityJudgeDTO();
        dto.setResult(QualityInspectionResultEnum.PASS.getCode());
        dto.setTotalQty(new BigDecimal(total));
        dto.setPassQty(new BigDecimal(pass));
        dto.setFailQty(new BigDecimal(fail));
        return dto;
    }

    // ---------- 1. judge PASS：质量事实写入 + FQC 联动 finishedQuantity ----------

    @Test
    void judgePassWritesFactAndUpdatesFqcFinishedQuantity() {
        ProductionQualityInspection q = pendingFqc(1L, 2L, 3L);
        when(inspectionMapper.selectById(1L)).thenReturn(q);
        when(productionOrderMapper.selectById(2L)).thenReturn(order(2L, BigDecimal.ZERO, 0));
        when(qualityInspectionService.getById(1L)).thenReturn(new QualityInspectionVO());

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUsername).thenReturn("测试员");
            service.judge(1L, passDto("100", "98", "2"));
        }

        // 质量事实写入
        assertEquals(QualityInspectionResultEnum.PASS.getCode(), q.getResult());
        assertEquals(0, new BigDecimal("98.0000").compareTo(q.getPassQty()));
        verify(inspectionMapper).updateById(q);
        // FQC PASS → finishedQuantity = passQty
        org.mockito.ArgumentCaptor<ProductionOrder> cap = org.mockito.ArgumentCaptor.forClass(ProductionOrder.class);
        verify(productionOrderMapper).updateById(cap.capture());
        assertEquals(0, new BigDecimal("98").compareTo(cap.getValue().getFinishedQuantity()));
    }

    private ProductionOrder order(Long id, BigDecimal finished, int reworkFlag) {
        ProductionOrder o = new ProductionOrder();
        o.setOrderId(id);
        o.setFinishedQuantity(finished);
        o.setReworkFlag(reworkFlag);
        return o;
    }

    // ---------- 2. judge FAIL：FQC FAIL → 最后有效 Execution 恢复 EXECUTING ----------

    @Test
    void judgeFailRestoresExecutionToExecuting() {
        ProductionQualityInspection q = pendingFqc(1L, 2L, 3L);
        when(inspectionMapper.selectById(1L)).thenReturn(q);
        ProductionOrder o = order(2L, BigDecimal.ZERO, 0);
        when(productionOrderMapper.selectById(2L)).thenReturn(o);
        ProductionOperationExecution exec = new ProductionOperationExecution();
        exec.setExecutionId(3L);
        exec.setExecutionStatus(ExecutionStatusEnum.COMPLETED.getCode());
        when(executionMapper.selectById(3L)).thenReturn(exec);
        when(qualityInspectionService.getById(1L)).thenReturn(new QualityInspectionVO());

        QualityJudgeDTO dto = passDto("100", "0", "100");
        dto.setResult(QualityInspectionResultEnum.FAIL.getCode());
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUsername).thenReturn("测试员");
            service.judge(1L, dto);
        }

        assertEquals(QualityInspectionResultEnum.FAIL.getCode(), q.getResult());
        // Order 保持未完成 + 标记返工；finishedQuantity 不被 FAIL 修改（保持原值 ZERO）
        assertEquals(1, o.getReworkFlag());
        assertEquals(0, BigDecimal.ZERO.compareTo(o.getFinishedQuantity()));
        // Execution 恢复 EXECUTING
        assertEquals(ExecutionStatusEnum.EXECUTING.getCode(), exec.getExecutionStatus());
        assertNull(exec.getActualEndTime());
        verify(executionMapper).updateById(exec);
    }

    // ---------- 3. 不可变：已判定禁止再次 judge ----------

    @Test
    void judgeRejectedWhenAlreadyFinalized() {
        ProductionQualityInspection q = pendingFqc(1L, 2L, 3L);
        q.setResult(QualityInspectionResultEnum.FAIL.getCode()); // 已判定 FAIL
        when(inspectionMapper.selectById(1L)).thenReturn(q);
        assertThrows(BusinessException.class, () -> service.judge(1L, passDto("100", "98", "2")));
    }

    // ---------- 4. 数量规则 ----------

    @Test
    void judgeRejectsNegativeQuantities() {
        ProductionQualityInspection q = pendingFqc(1L, 2L, 3L);
        when(inspectionMapper.selectById(1L)).thenReturn(q);
        QualityJudgeDTO dto = passDto("100", "-1", "2");
        assertThrows(BusinessException.class, () -> service.judge(1L, dto));
    }

    @Test
    void judgeRejectsPassPlusFailExceedingTotal() {
        ProductionQualityInspection q = pendingFqc(1L, 2L, 3L);
        when(inspectionMapper.selectById(1L)).thenReturn(q);
        QualityJudgeDTO dto = passDto("100", "80", "30"); // 80+30 > 100
        assertThrows(BusinessException.class, () -> service.judge(1L, dto));
    }

    @Test
    void judgePassRejectsZeroPassQty() {
        ProductionQualityInspection q = pendingFqc(1L, 2L, 3L);
        when(inspectionMapper.selectById(1L)).thenReturn(q);
        QualityJudgeDTO dto = passDto("100", "0", "100"); // PASS 但 passQty=0
        assertThrows(BusinessException.class, () -> service.judge(1L, dto));
    }

    @Test
    void judgeRejectsInvalidResult() {
        ProductionQualityInspection q = pendingFqc(1L, 2L, 3L);
        when(inspectionMapper.selectById(1L)).thenReturn(q);
        QualityJudgeDTO dto = passDto("100", "98", "2");
        dto.setResult("INVALID");
        assertThrows(BusinessException.class, () -> service.judge(1L, dto));
    }

    // ---------- 5. reinspect：新建 PENDING 复检单 ----------

    @Test
    void reinspectCreatesNewPendingWithoutOverwriting() {
        ProductionQualityInspection old = pendingFqc(1L, 2L, 3L);
        old.setInspectionType(QualityInspectionTypeEnum.FQC.getCode());
        when(inspectionMapper.selectById(1L)).thenReturn(old);
        when(qualityInspectionService.create(any(QualityInspectionCreateDTO.class))).thenAnswer(inv -> {
            QualityInspectionCreateDTO dto = inv.getArgument(0);
            assertEquals(QualityInspectionTypeEnum.FQC.getCode(), dto.getInspectionType());
            assertEquals(2L, dto.getOrderId());
            assertEquals(3L, dto.getExecutionId());
            assertNull(dto.getWorkReportId());
            return 99L;
        });
        Long newId;
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUsername).thenReturn("测试员");
            newId = service.reinspect(1L);
        }
        assertEquals(99L, newId);
        // 旧记录未修改
        assertEquals(QualityInspectionResultEnum.PENDING.getCode(), old.getResult());
    }

    // ---------- 6. createFqcForExecution：幂等 + FQC 形态 ----------

    @Test
    void createFqcSkipsWhenPendingExists() {
        ProductionOperationExecution exec = new ProductionOperationExecution();
        exec.setExecutionId(3L);
        exec.setOrderId(2L);
        when(executionMapper.selectById(3L)).thenReturn(exec);
        when(qualityInspectionService.hasPendingFqc(3L)).thenReturn(true);
        assertNull(service.createFqcForExecution(3L));
    }

    @Test
    void createFqcCreatesPendingWhenNoneExists() {
        ProductionOperationExecution exec = new ProductionOperationExecution();
        exec.setExecutionId(3L);
        exec.setOrderId(2L);
        when(executionMapper.selectById(3L)).thenReturn(exec);
        when(qualityInspectionService.hasPendingFqc(3L)).thenReturn(false);
        when(qualityInspectionService.create(any(QualityInspectionCreateDTO.class))).thenAnswer(inv -> {
            QualityInspectionCreateDTO dto = inv.getArgument(0);
            assertEquals(QualityInspectionTypeEnum.FQC.getCode(), dto.getInspectionType());
            assertEquals(2L, dto.getOrderId());
            assertEquals(3L, dto.getExecutionId());
            assertNull(dto.getWorkReportId()); // FQC：不绑定报工
            return 77L;
        });
        Long fqcId;
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUsername).thenReturn("测试员");
            fqcId = service.createFqcForExecution(3L);
        }
        assertEquals(77L, fqcId);
    }

    // ---------- 7. createInspection：workReportId 一致性校验 ----------

    @Test
    void createInspectionRejectsInconsistentWorkReportLink() {
        QualityInspectionCreateDTO dto = new QualityInspectionCreateDTO();
        dto.setInspectionType(QualityInspectionTypeEnum.IPQC.getCode());
        dto.setOrderId(2L);
        dto.setExecutionId(3L);
        dto.setWorkReportId(5L);
        when(qualityInspectionService.checkWorkReportLink(5L, 3L, 2L)).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.createInspection(dto));
    }

    @Test
    void createInspectionPassesWhenLinkConsistent() {
        QualityInspectionCreateDTO dto = new QualityInspectionCreateDTO();
        dto.setInspectionType(QualityInspectionTypeEnum.IPQC.getCode());
        dto.setOrderId(2L);
        dto.setExecutionId(3L);
        dto.setWorkReportId(5L);
        when(qualityInspectionService.checkWorkReportLink(5L, 3L, 2L)).thenReturn(true);
        when(qualityInspectionService.create(dto)).thenReturn(11L);
        assertEquals(11L, service.createInspection(dto));
    }
}
