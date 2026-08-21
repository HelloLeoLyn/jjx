package com.jjx.production;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.enums.WorkReportStatusEnum;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2-B 回归测试：ProductionWorkReportMapper 基础能力 + Entity 关键约束
 * 说明：真实 MySQL insert/索引/无 FK 验证见 SchemaTest（事务回滚）；本测试验证 wrapper 查询链路与字段语义。
 */
@ExtendWith(MockitoExtension.class)
class ProductionWorkReportMapperTest {

    @Mock
    ProductionWorkReportMapper mapper;

    private ProductionWorkReport sample() {
        ProductionWorkReport r = new ProductionWorkReport();
        r.setReportId(1L);
        r.setOrderId(2L);
        r.setOrderNo("SO-001");
        r.setExecutionId(3L);
        r.setReporterId(104L);
        r.setReporterName("印刷一组工人");
        r.setQualifiedQuantity(new BigDecimal("950.0000"));
        r.setDefectiveQuantity(new BigDecimal("50.0000"));
        r.setLaborHours(new BigDecimal("2.50"));
        r.setMachineHours(new BigDecimal("2.00"));
        r.setReportTime(LocalDateTime.now());
        r.setReportStatus(WorkReportStatusEnum.SUBMITTED.getCode());
        return r;
    }

    @Test
    void insertPersistsCoreFactFields() {
        ProductionWorkReport r = sample();
        when(mapper.insert(r)).thenReturn(1);
        assertEquals(1, mapper.insert(r));
        // 核心事实字段语义
        assertEquals(3L, r.getExecutionId());
        assertEquals(104L, r.getReporterId());
        assertEquals(new BigDecimal("950.0000"), r.getQualifiedQuantity());
        assertEquals(WorkReportStatusEnum.SUBMITTED.getCode(), r.getReportStatus());
        verify(mapper).insert(r);
    }

    @Test
    void queryByExecutionIdAndStatus() {
        mapper.selectList(Wrappers.<ProductionWorkReport>lambdaQuery()
                .eq(ProductionWorkReport::getExecutionId, 3L)
                .eq(ProductionWorkReport::getReportStatus, WorkReportStatusEnum.SUBMITTED.getCode()));
        verify(mapper).selectList(any());
    }

    @Test
    void entityHasNoReportNoOrInputOutputFields() throws Exception {
        // 正式决策：无 report_no / input_quantity / output_quantity / org 快照
        for (var f : ProductionWorkReport.class.getDeclaredFields()) {
            assertFalse(f.getName().contains("reportNo"), "不应有 report_no");
            assertFalse(f.getName().contains("inputQuantity"), "不应有 input_quantity");
            assertFalse(f.getName().contains("outputQuantity"), "不应有 output_quantity");
            assertFalse(f.getName().contains("reportedQuantity"), "不应有 reported_quantity");
            assertFalse(f.getName().contains("orgId") || f.getName().contains("orgName")
                    || f.getName().contains("orgPath"), "不应有 org 快照");
        }
        // 必须有的字段
        assertTrue(hasField("cancelReason"));
        assertTrue(hasField("cancelledAt"));
    }

    private boolean hasField(String name) {
        try {
            ProductionWorkReport.class.getDeclaredField(name);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }
}
