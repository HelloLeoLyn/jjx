package com.jjx.sales;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.event.EventPublisher;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.product.service.ProductCustomerValidator;
import com.jjx.sales.domain.dto.SalesOrderBatchSampleDTO;
import com.jjx.sales.domain.dto.SalesOrderProductDTO;
import com.jjx.sales.domain.entity.*;
import com.jjx.sales.domain.vo.SalesOrderProductVO;
import com.jjx.sales.enums.SalesOrderTypeEnum;
import com.jjx.sales.mapper.*;
import com.jjx.sales.service.*;
import com.jjx.system.aspect.EventAspect;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实 MySQL 主表/扩展表/明细与事务代理；仅清理本测试唯一前缀创建的记录。 */
@EnabledIfEnvironmentVariable(named="JJX_TEST_MYSQL", matches="1")
class OrderBatchSampleMysqlTest {
    private JdbcTemplate jdbc;
    private OrderMapper orders;
    private CustomerMapper customers;
    private SalesOrderProductMapper items;
    private ISampleOrderService service;
    private EventPublisher events;
    private String prefix;
    private SalesOrder source;
    private SalesCustomer customer;
    private final AtomicInteger writes = new AtomicInteger();
    private final Set<Long> writtenOrders = ConcurrentHashMap.newKeySet();
    private volatile boolean failSecond;

    @BeforeEach void setup() throws Exception {
        prefix = "B34" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl(System.getenv().getOrDefault("JJX_TEST_MYSQL_URL", "jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"));
        ds.setUsername(System.getenv().getOrDefault("DB_USER", "root"));
        ds.setPassword(System.getenv().getOrDefault("DB_PASS", "123456"));
        jdbc = new JdbcTemplate(ds);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        for (var type : List.of(OrderMapper.class, CustomerMapper.class, SalesSampleOrderMapper.class, SalesOrderProductMapper.class)) config.addMapper(type);
        var bean = new MybatisSqlSessionFactoryBean(); bean.setDataSource(ds); bean.setConfiguration(config);
        bean.setGlobalConfig(new GlobalConfig().setBanner(false));
        var session = new SqlSessionTemplate(bean.getObject());
        orders = session.getMapper(OrderMapper.class); customers = session.getMapper(CustomerMapper.class);
        items = session.getMapper(SalesOrderProductMapper.class);
        var productService = mock(ISalesOrderProductService.class);
        when(productService.getListByOrderId(anyLong())).thenAnswer(i -> items.selectList(Wrappers.<SalesOrderProduct>lambdaQuery()
                .eq(SalesOrderProduct::getOrderId, i.getArgument(0)).orderByAsc(SalesOrderProduct::getId)).stream().map(e -> {
                    var vo = new SalesOrderProductVO(); BeanUtils.copyProperties(e, vo); return vo;
                }).toList());
        when(productService.batchAdd(anyList())).thenAnswer(i -> {
            List<SalesOrderProductDTO> dtos = i.getArgument(0);
            for (var dto : dtos) {
                writtenOrders.add(dto.getOrderId());
                var entity = new SalesOrderProduct(); BeanUtils.copyProperties(dto, entity); items.insert(entity);
            }
            if (writes.incrementAndGet() == 2 && failSecond) throw new IllegalStateException("second product insertion failed");
            return true;
        });
        var sequence = mock(RedisSequenceService.class);
        when(sequence.generateBusinessNumberByType(anyString(), anyString(), anyString(), anyInt()))
                .thenAnswer(i -> prefix + UUID.randomUUID().toString().substring(0, 8));
        var target = OrderBatchSampleTest.service(Map.of(OrderMapper.class, orders, CustomerMapper.class, customers,
                SalesSampleOrderMapper.class, session.getMapper(SalesSampleOrderMapper.class), SalesOrderProductMapper.class, items,
                ISalesOrderProductService.class, productService, RedisSequenceService.class, sequence,
                ProductCustomerValidator.class, mock(ProductCustomerValidator.class), ObjectMapper.class, new ObjectMapper().findAndRegisterModules()));
        events = mock(EventPublisher.class);
        var factory = new AspectJProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        factory.addAspect(new EventAspect(events));
        service = (ISampleOrderService) factory.getProxy(); ReflectionTestUtils.setField(target, "self", service);
        customer = new SalesCustomer(); customer.setCustomerCode(prefix); customer.setCustomerName(prefix); customers.insert(customer);
        source = new SalesOrder(); source.setOrderNo(prefix + "SOURCE"); source.setCustomerId(customer.getCustomerId());
        source.setCustomerName(prefix); source.setOrderDate(new Date()); source.setOrderType(SalesOrderTypeEnum.STANDARD.getCode());
        source.setContactPerson("source contact"); source.setContactPhone("1234"); source.setCurrency("CNY"); source.setExchangeRate(BigDecimal.ONE);
        source.setTotalQuantity(300); source.setTraceId(prefix); orders.insert(source);
        for (int n = 1; n <= 3; n++) {
            var row = new SalesOrderProduct(); row.setOrderId(source.getOrderId()); row.setProductId((long)n);
            row.setProductCode(prefix + n); row.setProductName("product" + n); row.setSpecification("spec" + n);
            row.setUnit("PCS"); row.setQuantity(100); row.setAmount(BigDecimal.ZERO); items.insert(row);
        }
    }

    @AfterEach void cleanup() {
        if (jdbc == null || prefix == null) return;
        // 精确归属：只删除本轮随机前缀客户创建的单据，绝不清理其他业务数据。
        if (customer != null && customer.getCustomerId() != null) {
            Long cid = customer.getCustomerId();
            jdbc.update("DELETE p FROM sales_order_product p JOIN sales_order o ON p.order_id=o.order_id WHERE o.customer_id=? AND o.order_no LIKE ?", cid, prefix + "%");
            jdbc.update("DELETE s FROM sales_sample_order s JOIN sales_order o ON s.order_id=o.order_id WHERE o.customer_id=? AND o.order_no LIKE ?", cid, prefix + "%");
            jdbc.update("DELETE FROM sales_order WHERE customer_id=? AND order_no LIKE ?", cid, prefix + "%");
            jdbc.update("DELETE FROM sales_customer WHERE customer_id=? AND customer_code=?", cid, prefix);
        }
    }

    private SalesOrderBatchSampleDTO request() {
        var options = service.getSalesOrderSampleOptions(source.getOrderId());
        var dto = new SalesOrderBatchSampleDTO(); dto.setSourceVersion(options.getSourceVersion());
        dto.setItems(options.getItems().subList(0, 2).stream().map(row -> {
            var item = new SalesOrderBatchSampleDTO.Item(); item.setItemId(row.getItemId());
            item.setQuantity(BigDecimal.valueOf(row.getProductName().endsWith("1") ? 3 : 5)); return item;
        }).toList()); return dto;
    }

    private int sampleCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM sales_sample_order WHERE source_no=?", Integer.class, source.getOrderNo()); }

    @Test void savesTwoIndependentSamplesAndRejectsRetry() {
        var dto = request(); var result = service.createBatchFromSalesOrder(source.getOrderId(), dto);
        assertEquals(List.of(3,5), result.stream().map(SalesOrder::getSampleQty).toList());
        assertEquals(2, sampleCount());
        for (var saved : result) {
            assertEquals("source contact", saved.getContactPerson()); assertEquals(source.getOrderNo(), saved.getSourceNo());
            assertEquals(prefix, saved.getTraceId()); assertEquals(saved.getSampleQty(), saved.getTotalQuantity());
        }
        verify(events, times(2)).fire(eq("sample.created"), anyMap());
        assertThrows(BusinessException.class, () -> service.createBatchFromSalesOrder(source.getOrderId(), dto));
        assertEquals(2, sampleCount()); assertEquals(300, orders.selectById(source.getOrderId()).getTotalQuantity());
    }

    @Test void secondFailureRollsBackMainProfilesItemsAndEvents() {
        failSecond = true; var dto = request();
        assertThrows(BusinessException.class, () -> service.createBatchFromSalesOrder(source.getOrderId(), dto));
        assertEquals(0, sampleCount());
        for (Long id : writtenOrders) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sales_order_product WHERE order_id=?", Integer.class, id));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sales_order WHERE order_id=?", Integer.class, id));
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sales_order WHERE customer_id=?", Integer.class, customer.getCustomerId()));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM sales_order_product WHERE order_id=?", Integer.class, source.getOrderId()));
        verifyNoInteractions(events);
    }

    @Test void concurrentRequestsCreateOnlyOneBatch() throws Exception {
        var dto = request(); var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        Callable<Boolean> submit = () -> { start.await(); try { service.createBatchFromSalesOrder(source.getOrderId(), dto); return true; }
            catch (BusinessException expected) { return false; } };
        try {
            var a = pool.submit(submit); var b = pool.submit(submit); start.countDown();
            assertNotEquals(a.get(25, TimeUnit.SECONDS), b.get(25, TimeUnit.SECONDS));
            assertEquals(2, sampleCount()); verify(events, times(2)).fire(eq("sample.created"), anyMap());
        } finally { pool.shutdown(); assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS)); }
    }
}
