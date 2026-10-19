package com.jjx.sales;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jjx.common.exception.BusinessException;
import com.jjx.event.EventPublisher;
import com.jjx.sales.domain.entity.*;
import com.jjx.sales.domain.vo.SalesOrderProductVO;
import com.jjx.sales.enums.*;
import com.jjx.sales.mapper.*;
import com.jjx.sales.service.*;
import com.jjx.sales.service.impl.SampleOrderServiceImpl;
import com.jjx.system.aspect.EventAspect;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实MySQL Mapper、事务锁、提交后事件；工程资料查询为替身，不宣称工程接口已端到端验证。 */
@EnabledIfEnvironmentVariable(named="JJX_TEST_MYSQL", matches="1")
class SampleReturnSourceOrderMysqlTest {
    JdbcTemplate jdbc; OrderMapper orders; SalesSampleOrderMapper profiles; SalesOrderProductMapper lines;
    ISampleOrderService service; EventPublisher events; IOrderService orderService;
    SalesOrder source, sample; SalesOrderProduct originalLine; String prefix;
    volatile boolean failAfterLink;
    @BeforeEach void setup() throws Exception {
        prefix = "R37" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
        var ds = new DriverManagerDataSource(); ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl(System.getenv().getOrDefault("JJX_TEST_MYSQL_URL", "jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"));
        ds.setUsername(System.getenv().getOrDefault("DB_USER", "root")); ds.setPassword(System.getenv().getOrDefault("DB_PASS", "123456")); jdbc = new JdbcTemplate(ds);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        for (var type : List.of(OrderMapper.class, SalesSampleOrderMapper.class, SalesOrderProductMapper.class)) config.addMapper(type);
        var bean = new MybatisSqlSessionFactoryBean(); bean.setDataSource(ds); bean.setConfiguration(config); bean.setGlobalConfig(new GlobalConfig().setBanner(false));
        var session = new SqlSessionTemplate(bean.getObject());
        orders = spy(session.getMapper(OrderMapper.class)); profiles = session.getMapper(SalesSampleOrderMapper.class); lines = session.getMapper(SalesOrderProductMapper.class);
        var deps = SampleReturnSourceOrderTest.dependencies(); deps.put(OrderMapper.class, orders); deps.put(SalesSampleOrderMapper.class, profiles); deps.put(SalesOrderProductMapper.class, lines);
        var productService = (ISalesOrderProductService)deps.get(ISalesOrderProductService.class);
        when(productService.getListByOrderId(anyLong())).thenAnswer(i -> lines.selectList(Wrappers.<SalesOrderProduct>lambdaQuery().eq(SalesOrderProduct::getOrderId, i.getArgument(0))).stream().map(row -> {
            var vo = new SalesOrderProductVO(); BeanUtils.copyProperties(row, vo); return vo;
        }).toList());
        SampleReturnSourceOrderTest.ready(deps, 8L); orderService = (IOrderService)deps.get(IOrderService.class);
        var target = SampleReturnSourceOrderTest.construct(SampleOrderServiceImpl.class, deps);
        events = mock(EventPublisher.class); var factory = new AspectJProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource())); factory.addAspect(new EventAspect(events));
        service = (ISampleOrderService)factory.getProxy();
        Long customer = jdbc.queryForObject("SELECT MIN(customer_id) FROM sales_customer WHERE deleted=0", Long.class); assertNotNull(customer);
        source = new SalesOrder(); source.setOrderNo(prefix + "SO"); source.setCustomerId(customer); source.setCustomerName(prefix);
        source.setOrderDate(new Date()); source.setOrderType(SalesOrderTypeEnum.STANDARD.getCode()); source.setOrderStatus(SalesOrderStatusEnum.IN_PRODUCTION.getValue());
        source.setTotalQuantity(100); source.setTotalAmount(new BigDecimal("1230.00")); source.setFinalAmount(new BigDecimal("1230.00")); source.setTraceId(prefix); orders.insert(source);
        sample = new SalesOrder(); sample.setOrderNo(prefix + "SP"); sample.setCustomerId(customer); sample.setCustomerName(prefix);
        sample.setOrderDate(new Date()); sample.setOrderType(SalesOrderTypeEnum.SAMPLE.getCode()); sample.setOrderStatus(SalesOrderStatusEnum.DRAFT.getValue()); sample.setTotalQuantity(3); sample.setTraceId(prefix); orders.insert(sample);
        var profile = new SalesSampleOrder(); profile.setOrderId(sample.getOrderId()); profile.setSourceType("SALES_ORDER"); profile.setSourceNo(source.getOrderNo());
        profile.setProductId(8L); profile.setProductCode("P37"); profile.setProductName("Test product"); profile.setSampleQty(3); profile.setSampleStatus(SampleOrderStatusEnum.CONFIRMED.getValue()); profile.setDeleted(0); profiles.insert(profile);
        originalLine = new SalesOrderProduct(); originalLine.setOrderId(source.getOrderId()); originalLine.setProductId(8L); originalLine.setProductCode("P37"); originalLine.setProductName("Test product");
        originalLine.setQuantity(100); originalLine.setUnitPrice(new BigDecimal("12.30")); originalLine.setAmount(new BigDecimal("1230.00")); lines.insert(originalLine);
        var sampleLine = new SalesOrderProduct(); BeanUtils.copyProperties(originalLine, sampleLine); sampleLine.setId(null); sampleLine.setOrderId(sample.getOrderId()); sampleLine.setQuantity(3); lines.insert(sampleLine);
        // 在真实回写已执行后注入异常，验证状态、关联和事件一起回滚。
        doAnswer(i -> { int result = (int)i.callRealMethod(); if (failAfterLink && ((SalesOrder)i.getArgument(0)).getConvertedOrderId() != null) throw new BusinessException("injected after link"); return result; }).when(orders).updateById(any(SalesOrder.class));
    }
    @AfterEach void cleanupOwnFixtures() {
        if (jdbc == null || prefix == null) return;
        // 单行主键+本轮随机前缀双重限定，仅清理本测试新建夹具，不触碰存量业务数据。
        for (var order : Arrays.asList(sample, source)) {
            if (order == null || order.getOrderId() == null) continue;
            var ids = jdbc.queryForList("SELECT p.id FROM sales_order_product p JOIN sales_order o ON p.order_id=o.order_id WHERE o.order_id=? AND o.order_no LIKE ?", Long.class, order.getOrderId(), prefix + "%");
            for (Long id : ids) jdbc.update("DELETE FROM sales_order_product WHERE id=? AND order_id=?", id, order.getOrderId());
            jdbc.update("DELETE s FROM sales_sample_order s JOIN sales_order o ON o.order_id=s.order_id WHERE o.order_id=? AND o.order_no LIKE ?", order.getOrderId(), prefix + "%");
            jdbc.update("DELETE FROM sales_order WHERE order_id=? AND order_no LIKE ?", order.getOrderId(), prefix + "%");
        }
    }
    Map<String,Object> original() { return jdbc.queryForMap("SELECT * FROM sales_order WHERE order_id=?", source.getOrderId()); }
    Map<String,Object> originalProduct() { return jdbc.queryForMap("SELECT * FROM sales_order_product WHERE id=?", originalLine.getId()); }
    void assertLinkedWithoutNewOrder() {
        var saved = orders.selectById(sample.getOrderId()); assertEquals(source.getOrderId(), saved.getConvertedOrderId());
        assertEquals(SampleOrderStatusEnum.TRANSFERRED.getValue(), saved.getSampleStatus()); assertNotNull(saved.getConvertOrderTime());
        assertEquals("SALES_ORDER", saved.getSourceType()); assertEquals("P37", saved.getSampleProductCode());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sales_order WHERE order_no LIKE ?", Integer.class, prefix + "%")); verifyNoInteractions(orderService);
    }
    @Test void postLinksAndRetryCannotRepeatWhileOriginalIsByteForByteUnchanged() {
        var before = original(); var product = originalProduct(); assertEquals(source.getOrderId(), service.convertSampleToProduction(sample.getOrderId(), null).getOrderId());
        assertLinkedWithoutNewOrder(); assertEquals(before, original()); assertEquals(product, originalProduct());
        assertThrows(BusinessException.class, () -> service.convertToProduction(sample.getOrderId())); verify(events, times(1)).fire(eq("sample.converted"), anyMap());
    }
    @Test void oldPutLinksWithoutNewOrder() {
        var before = original(); service.convertToProduction(sample.getOrderId()); assertLinkedWithoutNewOrder(); assertEquals(before, original()); verify(events, times(1)).fire(eq("sample.converted"), anyMap());
    }
    @Test void failureAfterRealLinkUpdateRollsBackStateTimeAndEvents() {
        var before = original(); failAfterLink = true;
        assertThrows(BusinessException.class, () -> service.convertSampleToProduction(sample.getOrderId(), null));
        var saved = orders.selectById(sample.getOrderId()); assertNull(saved.getConvertedOrderId()); assertNull(saved.getConvertOrderTime());
        assertEquals(SampleOrderStatusEnum.CONFIRMED.getValue(), saved.getSampleStatus()); assertEquals(before, original()); verifyNoInteractions(events);
    }
    @Test void concurrentPostAndPutOnlyOneWritesAndPublishes() throws Exception {
        var before = original(); var product = originalProduct(); var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> { start.await(); try { service.convertSampleToProduction(sample.getOrderId(), null); return true; } catch (BusinessException e) { return false; } });
            var b = pool.submit(() -> { start.await(); try { service.convertToProduction(sample.getOrderId()); return true; } catch (BusinessException e) { return false; } });
            start.countDown(); assertNotEquals(a.get(25, TimeUnit.SECONDS), b.get(25, TimeUnit.SECONDS)); assertLinkedWithoutNewOrder();
            assertEquals(before, original()); assertEquals(product, originalProduct()); verify(events, times(1)).fire(eq("sample.converted"), anyMap());
        } finally { pool.shutdown(); assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS)); }
    }
    @Test void deletedClosedWrongCustomerAndRemovedProductRejectWithoutLink() {
        jdbc.update("UPDATE sales_order SET deleted=1 WHERE order_id=?", source.getOrderId()); assertThrows(BusinessException.class, () -> service.convertSampleToProduction(sample.getOrderId(), null));
        jdbc.update("UPDATE sales_order SET deleted=0,order_status=? WHERE order_id=?", SalesOrderStatusEnum.COMPLETED.getValue(), source.getOrderId()); assertThrows(BusinessException.class, () -> service.convertToProduction(sample.getOrderId()));
        jdbc.update("UPDATE sales_order SET order_status=?,customer_id=? WHERE order_id=?", SalesOrderStatusEnum.DRAFT.getValue(), source.getCustomerId()+99999, source.getOrderId()); assertThrows(BusinessException.class, () -> service.convertSampleToProduction(sample.getOrderId(), null));
        jdbc.update("UPDATE sales_order SET customer_id=? WHERE order_id=?", source.getCustomerId(), source.getOrderId());
        jdbc.update("UPDATE sales_order_product SET product_id=9 WHERE id=?", originalLine.getId()); assertThrows(BusinessException.class, () -> service.convertSampleToProduction(sample.getOrderId(), null));
        assertNull(orders.selectById(sample.getOrderId()).getConvertedOrderId()); verifyNoInteractions(events);
    }
}
