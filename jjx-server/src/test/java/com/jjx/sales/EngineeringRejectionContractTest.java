package com.jjx.sales;

import com.jjx.sales.mapper.OrderMapper;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineeringRejectionContractTest {

    @Test
    void rejectionAtomicallyClearsEngineeringAcceptance() throws Exception {
        Update update = OrderMapper.class
                .getMethod("rejectEngineering", Long.class, Integer.class, Integer.class)
                .getAnnotation(Update.class);
        String sql = String.join(" ", update.value()).toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("engineering_acceptor = null"));
        assertTrue(sql.contains("engineering_accept_time = null"));
        assertTrue(sql.contains("sample_status = #{engineeringstatus}"));
    }
}
