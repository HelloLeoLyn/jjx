package com.jjx.production;

import com.jjx.production.domain.dto.ProductionOperationExecutionUpdateDTO;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * P0-03 回归测试：Execution DTO 映射（updateEntityFromUpdateDTO）
 * - remark 不再错误写入 defective_reason
 * - defectiveReason 正确写入 defective_reason
 * 说明：直接反射调用 private static 映射方法，避免 mock JdbcTemplate（Java 25 + Mockito 不兼容）
 */
class ExecutionDtoMappingTest {

    private static final Class<?> IMPL;
    private static final Method MAPPER;

    static {
        try {
            IMPL = Class.forName("com.jjx.production.service.impl.ProductionOperationExecutionServiceImpl");
            MAPPER = IMPL.getDeclaredMethod("updateEntityFromUpdateDTO",
                    ProductionOperationExecution.class, ProductionOperationExecutionUpdateDTO.class);
            MAPPER.setAccessible(true);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private void invokeMapper(ProductionOperationExecution exec, ProductionOperationExecutionUpdateDTO dto) throws Exception {
        MAPPER.invoke(null, exec, dto);
    }

    @Test
    void defectiveReasonWritesToDefectiveReason() throws Exception {
        ProductionOperationExecution exec = new ProductionOperationExecution();
        ProductionOperationExecutionUpdateDTO dto = new ProductionOperationExecutionUpdateDTO();
        dto.setRemark("普通备注");
        dto.setDefectiveReason("划伤");

        invokeMapper(exec, dto);

        assertEquals("划伤", exec.getDefectiveReason());
    }

    @Test
    void remarkNoLongerWritesToDefectiveReason() throws Exception {
        ProductionOperationExecution exec = new ProductionOperationExecution();
        ProductionOperationExecutionUpdateDTO dto = new ProductionOperationExecutionUpdateDTO();
        dto.setRemark("仅备注");

        invokeMapper(exec, dto);

        // P0-03 修复：remark 不再被映射到 defective_reason
        assertNull(exec.getDefectiveReason());
    }
}
