package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.production.domain.entity.ProductionTask;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * dev-20260930-022 回归：一个工序存在「标准根任务 + 补产根任务」时（两者 parent_task_id 都为空），
 * 取 First Task / 工序负责人必须只认标准根任务 —— 否则 selectOne 抛
 * `Expected one result (or null) to be returned by selectOne(), but found: 2`（工单完成收口报错）。
 */
class ProductionTaskRootLookupTest {

    static {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "production-task-root-lookup-test"),
                ProductionTask.class);
    }

    @Test
    void rootQueryIsRestrictedToStandardTaskType() {
        LambdaQueryWrapper<ProductionTask> wrapper = ProductionTaskServiceImpl.standardRootQuery(1L);
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("task_type"), "取根任务必须带 task_type 条件，实际 SQL 片段：" + sql);
        assertTrue(sql.contains("parent_task_id"),
                "取根任务仍须限定 parent_task_id IS NULL，实际 SQL 片段：" + sql);
        assertTrue(wrapper.getParamNameValuePairs().containsValue("STANDARD"),
                "取根任务必须限定 STANDARD，实际参数：" + wrapper.getParamNameValuePairs());
        assertEquals(1L, wrapper.getParamNameValuePairs().values().stream()
                .filter(v -> v instanceof Long).findFirst().orElse(null), "executionId 必须进参数");
    }
}
