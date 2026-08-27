package com.jjx.production;

import com.jjx.production.domain.dto.ProductionOperationExecutionCreateDTO;
import com.jjx.production.domain.dto.ProductionOperationExecutionQueryDTO;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionStatusMapperContractTest {

    private static final Set<String> REMOVED_METHODS = Set.of(
            "selectByOrderIdAndStatus", "selectPending", "selectProcessing", "selectCompleted",
            "selectOverdue", "updateStatus", "updateBatchStatus", "startExecution",
            "completeExecution", "updateOutput", "updateHours", "getOrderExecutionStats",
            "getOperatorStats", "getEquipmentStats");

    @Test
    void legacyStringStatusMapperMethodsAreAbsent() {
        Set<String> methodNames = Arrays.stream(ProductionOperationExecutionMapper.class.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        REMOVED_METHODS.forEach(name -> assertFalse(methodNames.contains(name), name + " 不应继续暴露"));
    }

    @Test
    void executionStatusQueryUsesIntegerEnumCodeAndCreateCannotChooseStatus() throws Exception {
        assertEquals(Integer.class,
                ProductionOperationExecutionQueryDTO.class.getDeclaredField("executionStatus").getType());
        assertThrows(NoSuchFieldException.class,
                () -> ProductionOperationExecutionCreateDTO.class.getDeclaredField("executionStatus"));
    }

    @Test
    void mapperXmlContainsNoStringExecutionStatusLiterals() throws Exception {
        var resource = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("mapper/production/ProductionOperationExecutionMapper.xml");
        assertNotNull(resource);
        String xml;
        try (resource) {
            xml = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String literal : Set.of("'PENDING'", "'IN_PROGRESS'", "'COMPLETED'", "'SKIPPED'")) {
            assertFalse(xml.contains(literal), "XML 不应包含字符串 execution status: " + literal);
        }
    }
}
