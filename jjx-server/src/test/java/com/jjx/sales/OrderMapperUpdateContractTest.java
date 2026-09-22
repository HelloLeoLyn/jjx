package com.jjx.sales;

import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.mapper.OrderMapper;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止自定义 updateById 白名单再次静默遗漏主表关键写回字段。 */
class OrderMapperUpdateContractTest {

    @Test
    void updateByIdKeepsPaymentProductionAndDeliveryFields() throws Exception {
        Update update = OrderMapper.class
                .getMethod("updateById", SalesOrder.class)
                .getAnnotation(Update.class);
        assertNotNull(update, "OrderMapper.updateById 必须保留自定义拆表更新 SQL");

        String sql = String.join(" ", update.value()).toLowerCase(Locale.ROOT);
        assertUpdateBranch(sql, "paymentstatus", "o.payment_status");
        assertUpdateBranch(sql, "paidamount", "o.paid_amount");
        assertUpdateBranch(sql, "unpaidamount", "o.unpaid_amount");
        assertUpdateBranch(sql, "producedquantity", "o.produced_quantity");
        assertUpdateBranch(sql, "prodstatus", "o.prod_status");
        assertUpdateBranch(sql, "shippedquantity", "o.shipped_quantity");
    }

    private static void assertUpdateBranch(String sql, String property, String column) {
        assertTrue(sql.contains("entity." + property + " != null"), property + " 缺少非空更新分支");
        assertTrue(sql.contains(column + "=#{entity." + property + "}"), column + " 未写回实体字段");
    }
}
