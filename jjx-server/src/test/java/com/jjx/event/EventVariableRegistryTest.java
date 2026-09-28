package com.jjx.event;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EventVariableRegistryTest {

    @Test
    void mergesCollectedValuesAndManualDescriptions() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productCode", "P-001");
        payload.put("quantity", 12);
        payload.put("customerName", "真实客户");

        List<EventVariableRegistry.Variable> variables = EventVariableRegistry.merge(
                "order.delivering", payload, "2026-09-28 10:20:30");

        EventVariableRegistry.Variable productCode = variables.stream()
                .filter(variable -> variable.key().equals("productCode")).findFirst().orElseThrow();
        EventVariableRegistry.Variable customerName = variables.stream()
                .filter(variable -> variable.key().equals("customerName")).findFirst().orElseThrow();
        EventVariableRegistry.Variable quantity = variables.stream()
                .filter(variable -> variable.key().equals("quantity")).findFirst().orElseThrow();

        assertEquals("产品编码", productCode.description());
        assertEquals("collected", productCode.source());
        assertEquals("manual", customerName.source());
        assertEquals("12", quantity.example());
        assertEquals("2026-09-28 10:20:30", quantity.lastSeenAt());
    }

    @Test
    void deduplicatesAndSortsBySourceThenKey() {
        Map<String, Object> payload = Map.of("bizNo", "REAL-1", "zeta", "z", "alpha", "a");

        List<EventVariableRegistry.Variable> variables = EventVariableRegistry.merge("unknown", payload, "now");

        assertEquals(List.of("bizId", "bizNo", "bizType", "triggerRealName", "triggerUserName",
                "alpha", "zeta"), variables.stream().map(EventVariableRegistry.Variable::key).toList());
        assertEquals("common", variables.stream().filter(variable -> variable.key().equals("bizNo"))
                .findFirst().orElseThrow().source());
    }

    @Test
    void acceptsNullPayloadAndKeepsNullCollectedValue() {
        assertEquals(5, EventVariableRegistry.merge("unknown", null, null).size());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nullable", null);
        List<EventVariableRegistry.Variable> variables = EventVariableRegistry.merge("unknown", payload, null);
        assertNull(variables.stream().filter(variable -> variable.key().equals("nullable"))
                .findFirst().orElseThrow().example());
    }

    @Test
    void mergesAccumulatedVariablesWithStablePriorityAndManualDescription() {
        List<EventVariableRegistry.Variable> collected = List.of(
                new EventVariableRegistry.Variable("zeta", "采集描述", "z", "collected", "old"),
                new EventVariableRegistry.Variable("customerName", "采集客户", "old", "manual", "old"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alpha", "a");
        payload.put("zeta", "new");

        List<EventVariableRegistry.Variable> variables = EventVariableRegistry.merge(
                "order.delivering", collected, payload, "now");

        assertEquals(List.of("bizId", "bizNo", "bizType", "triggerRealName", "triggerUserName",
                "customerName", "deliverQuantity", "deliveryId", "deliveryNo", "orderNo",
                "alpha", "zeta"), variables.stream().map(EventVariableRegistry.Variable::key).toList());
        assertEquals("客户名称", variables.stream().filter(v -> v.key().equals("customerName"))
                .findFirst().orElseThrow().description());
        assertEquals(1, variables.stream().filter(v -> v.key().equals("zeta")).count());
    }

    @Test
    void acceptsNullCollectedVariables() {
        assertEquals(5, EventVariableRegistry.merge("unknown", null, null, null).size());
    }
}
