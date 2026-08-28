package com.jjx.sales;

import cn.hutool.json.JSONUtil;
import com.jjx.sales.controller.OrderStatusController;
import com.jjx.sales.service.impl.OrderStatusServiceImpl;
import com.jjx.system.annotation.Log;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStatusLogContractTest {

    private static final List<String> STATUS_ACTIONS = List.of(
            "submitReview", "startReview", "approveOrder", "rejectOrder",
            "resubmit", "cancelOrder", "generatePlan", "shipOrder", "completeOrder", "confirmOrder");

    @Test
    void statusActionsKeepSingleAopLogWithVisibleTransitionDetail() {
        SpelExpressionParser parser = new SpelExpressionParser();
        for (String methodName : STATUS_ACTIONS) {
            var method = Arrays.stream(OrderStatusController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst().orElseThrow();
            Log annotation = method.getAnnotation(Log.class);
            assertNotNull(annotation, methodName + " 必须保留 @Log");
            assertEquals("'order'", annotation.bizType(), methodName + " 必须使用统一业务类型");
            assertFalse(annotation.detail().isBlank(), methodName + " 必须提供流转详情");

            String detail = parser.parseExpression(annotation.detail())
                    .getValue(new StandardEvaluationContext(), String.class);
            var changes = JSONUtil.parseObj(detail).getJSONArray("changes");
            assertNotNull(changes, methodName + " detail 必须包含 changes");
            assertFalse(changes.isEmpty(), methodName + " changes 不能为空");
        }
    }

    @Test
    void serviceNoLongerDependsOnManualOperationLogWriter() {
        assertTrue(Arrays.stream(OrderStatusServiceImpl.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("saveOrderLog")));
        assertTrue(Arrays.stream(OrderStatusServiceImpl.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getName().equals("com.jjx.system.service.LogSaveService")));
    }
}
