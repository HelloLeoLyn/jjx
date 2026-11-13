package com.jjx.purchase.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jjx.common.enums.ApproveStatusEnum;
import com.jjx.purchase.domain.enums.ReceiptStatusEnum;
import com.jjx.purchase.mapper.PurchasePlanContextMapper;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** H2真实SQL：余额与采购在途独立读，不依赖旧在途SQL的状态字面量。 */
class PurchasePlanContextTest {
    JdbcTemplate jdbc;PurchasePlanContextMapper mapper;
    @BeforeEach void setup() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(ds);var config=new MybatisConfiguration();config.addMapper(PurchasePlanContextMapper.class);
        var factory=new MybatisSqlSessionFactoryBean();factory.setConfiguration(config);factory.setDataSource(ds);
        mapper=new SqlSessionTemplate(factory.getObject()).getMapper(PurchasePlanContextMapper.class);
        jdbc.execute("CREATE TABLE inventory_stock(material_id BIGINT,total_quantity DECIMAL(12,4),total_reserved DECIMAL(12,4))");
        jdbc.execute("CREATE TABLE purchase_order(order_id BIGINT,approval_status INT,receipt_status INT)");
        jdbc.execute("CREATE TABLE purchase_order_item(order_id BIGINT,material_id BIGINT,quantity DECIMAL(12,4),received_quantity DECIMAL(12,4))");
    }
    void eq(String expected,BigDecimal n){assertEquals(0,new BigDecimal(expected).compareTo(n));}
    @Test void balancesUseSummaryAndEmptySuccessMeansZero(){
        jdbc.update("INSERT INTO inventory_stock VALUES(110,19.5,9.75)");eq("19.5",mapper.stock(110L));eq("9.75",mapper.available(110L));eq("0",mapper.stock(999L));
    }
    @Test void transitIncludesPendingAndPartialButExcludesRejectedCancelledAndComplete(){
        long id=0;
        for(var approval:ApproveStatusEnum.values())for(var receipt:ReceiptStatusEnum.values()){
            jdbc.update("INSERT INTO purchase_order VALUES(?,?,?)",++id,approval.getValue(),receipt.getValue());
            jdbc.update("INSERT INTO purchase_order_item VALUES(?,110,10,3)",id);
            jdbc.update("INSERT INTO purchase_order_item VALUES(?,999,100,0)",id);
        }
        // 三种有效审批态 × 两种未齐收态 × 每单7㎡。
        eq("42",mapper.inTransit(110L,List.of(ApproveStatusEnum.DRAFT.getValue(),ApproveStatusEnum.PENDING.getValue(),ApproveStatusEnum.APPROVED.getValue()),List.of(ReceiptStatusEnum.PENDING.getValue(),ReceiptStatusEnum.PARTIALLY_RECEIVED.getValue())));
    }
    @Test void overReceivedRowsNeverCreateNegativeTransit(){
        jdbc.update("INSERT INTO purchase_order VALUES(1,?,?)",ApproveStatusEnum.APPROVED.getValue(),ReceiptStatusEnum.PARTIALLY_RECEIVED.getValue());
        jdbc.update("INSERT INTO purchase_order_item VALUES(1,110,10,12)");
        eq("0",mapper.inTransit(110L,List.of(ApproveStatusEnum.APPROVED.getValue()),List.of(ReceiptStatusEnum.PARTIALLY_RECEIVED.getValue())));
    }
}
