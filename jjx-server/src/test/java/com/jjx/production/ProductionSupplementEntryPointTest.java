package com.jjx.production;

import com.jjx.production.service.ProductionTaskService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionSupplementEntryPointTest {

    @Test
    void onlyConfirmedRouteEntryPointIsExposed() {
        Method[] methods = ProductionTaskService.class.getMethods();

        assertTrue(java.util.Arrays.stream(methods)
                .anyMatch(method -> method.getName().equals("createSupplementRouteTasks")));
        assertFalse(java.util.Arrays.stream(methods)
                .anyMatch(method -> method.getName().equals("createSupplementTask")));
    }
}
