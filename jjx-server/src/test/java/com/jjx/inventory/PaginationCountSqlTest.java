package com.jjx.inventory;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * dev-20260922-031：验证「分页 count 优化」这条代码路径在当前依赖集下可用。
 *
 * 背景：运行中的后端（10:30 构建的 jar）在 production_task / production_order 分页查询时反复报
 *   optimize this sql to a count sql has exception: NoClassDefFoundError
 *   net/sf/jsqlparser/expression/operators/relational/IsNullExpression（另有 ExistsExpression 一次）
 * 怀疑是那次构建产物问题。本测试直接调用 MyBatis-Plus 的 autoCountSql（即报错的那段逻辑），
 * 用带 IS NULL / EXISTS 的 SQL 触发 jsqlparser 的相关类加载：若依赖集缺类，这里会直接抛 NoClassDefFoundError。
 */
class PaginationCountSqlTest {

    @Test
    void countSqlOptimizationWorksForIsNullAndExists() throws Exception {
        PaginationInnerInterceptor interceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        Method autoCountSql = PaginationInnerInterceptor.class
                .getDeclaredMethod("autoCountSql", IPage.class, String.class);
        autoCountSql.setAccessible(true);

        String taskSql = "SELECT task_id,task_no,execution_id,parent_task_id "
                + "FROM production_task WHERE parent_task_id IS NULL AND status = 'PENDING'";
        Object countSql1 = autoCountSql.invoke(interceptor, new Page<>(1, 10), taskSql);
        assertNotNull(countSql1, "任务分页 count SQL 不应为 null");
        assertTrue(String.valueOf(countSql1).toUpperCase().contains("COUNT"),
                "count SQL 应含 COUNT，实际：" + countSql1);

        String orderSql = "SELECT order_id,order_no FROM production_order o "
                + "WHERE EXISTS (SELECT 1 FROM production_task t WHERE t.execution_id = o.order_id)";
        Object countSql2 = autoCountSql.invoke(interceptor, new Page<>(1, 10), orderSql);
        assertNotNull(countSql2, "带 EXISTS 的分页 count SQL 不应为 null（此前报 ExistsExpression 缺类）");
    }
}
