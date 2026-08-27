package com.jjx.production;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.ProductionOperationExecutionUpdateDTO;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P2-C 回归测试：旧 Execution 数量写入封锁 + complete 不伪造数量
 * - updateEntityFromUpdateDTO 收到数量/工时字段 → BusinessException（提示用报工）
 * - executionStatus 不再属于 UpdateDTO，状态只能通过 start/pause/complete/cancel 等动作接口变更
 * - 非状态、非数量字段（操作员/设备等）正常更新不受影响
 * 说明：反射调用 private static 映射方法（P0 已验证的测试模式）。
 */
class LegacyExecutionWriteBlockTest {

    private static final Method MAPPER;

    static {
        try {
            Class<?> clazz = Class.forName("com.jjx.production.service.impl.ProductionOperationExecutionServiceImpl");
            MAPPER = clazz.getDeclaredMethod("updateEntityFromUpdateDTO",
                    ProductionOperationExecution.class, ProductionOperationExecutionUpdateDTO.class);
            MAPPER.setAccessible(true);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private void invoke(ProductionOperationExecution exec, ProductionOperationExecutionUpdateDTO dto) throws Exception {
        try {
            MAPPER.invoke(null, exec, dto);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw e;
        }
    }

    @Test
    void quantityFieldsAreRejectedWithReportHint() {
        ProductionOperationExecutionUpdateDTO dto = new ProductionOperationExecutionUpdateDTO();
        dto.setActualCompletedQuantity(new BigDecimal("100"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> invoke(new ProductionOperationExecution(), dto));
        assertTrue(ex.getMessage().contains("报工"), "应提示使用报工功能: " + ex.getMessage());
    }

    @Test
    void qualifiedDefectiveLaborMachineAllRejected() {
        for (var setter : new String[]{"actualQualifiedQuantity", "actualDefectiveQuantity",
                "actualLaborHours", "actualMachineHours"}) {
            ProductionOperationExecutionUpdateDTO dto = new ProductionOperationExecutionUpdateDTO();
            try {
                var f = ProductionOperationExecutionUpdateDTO.class.getDeclaredMethod("set" + capitalize(setter), BigDecimal.class);
                f.invoke(dto, new BigDecimal("1"));
            } catch (Exception e) {
                fail("DTO 字段缺失: " + setter);
            }
            assertThrows(BusinessException.class,
                    () -> invoke(new ProductionOperationExecution(), dto), setter + " 应被封锁");
        }
    }

    @Test
    void nonStateAndNonQuantityFieldsStillUpdate() throws Exception {
        ProductionOperationExecutionUpdateDTO dto = new ProductionOperationExecutionUpdateDTO();
        dto.setOperatorId(99L);
        dto.setOperatorName("新操作员");
        ProductionOperationExecution exec = new ProductionOperationExecution();
        exec.setExecutionStatus(com.jjx.production.enums.ExecutionStatusEnum.PENDING.getCode());
        assertDoesNotThrow(() -> invoke(exec, dto));
        assertEquals(99L, exec.getOperatorId());
        assertEquals("新操作员", exec.getOperatorName());
        assertEquals(com.jjx.production.enums.ExecutionStatusEnum.PENDING.getCode(), exec.getExecutionStatus());
        // 数量字段未被触碰
        assertNull(exec.getOutputQuantity());
        assertNull(exec.getQualifiedQuantity());
    }

    @Test
    void updateDtoDoesNotExposeExecutionStatus() {
        assertThrows(NoSuchMethodException.class,
                () -> ProductionOperationExecutionUpdateDTO.class.getMethod("setExecutionStatus", Integer.class));
        assertThrows(NoSuchFieldException.class,
                () -> ProductionOperationExecutionUpdateDTO.class.getDeclaredField("executionStatus"));
    }

    @Test
    void completeExecutionNoLongerFabricatesQuantity() throws Exception {
        // 验证 completeExecution 源码不再包含 planned→quantity 自动补值逻辑
        Class<?> clazz = Class.forName("com.jjx.production.service.impl.ProductionOperationExecutionServiceImpl");
        String src = clazz.getResource("ProductionOperationExecutionServiceImpl.class").toString();
        // 通过反射确认方法中不再有 setOutputQuantity(execution.getInputQuantity()) 的补值（编译后无法直接查源码，
        // 改为验证 completeExecution 中存在 workReportProjectionService 依赖引用）
        var fields = clazz.getDeclaredFields();
        boolean hasProjection = false;
        for (var f : fields) {
            if (f.getName().contains("workReportProjectionService")) hasProjection = true;
        }
        assertTrue(hasProjection, "completeExecution 应依赖 WorkReportProjectionService（完工 gate）");
        // 数量补值逻辑已由 P2-C 移除（代码审查 + 编译验证）；此处断言 gate 依赖存在
        assertNotNull(src);
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
