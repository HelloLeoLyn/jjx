package com.jjx.sales;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jjx.common.exception.BusinessException;
import com.jjx.event.EventPublisher;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.ProductCustomerValidator;
import com.jjx.sales.domain.converter.SalesOrderConverter;
import com.jjx.sales.domain.dto.*;
import com.jjx.sales.domain.entity.*;
import com.jjx.sales.domain.vo.CustomerVO;
import com.jjx.sales.enums.QuotationStatus;
import com.jjx.sales.mapper.*;
import com.jjx.sales.service.*;
import com.jjx.sales.service.impl.*;
import com.jjx.system.mapper.SysAttachmentRefMapper;
import com.jjx.system.domain.entity.SysAttachmentRef;
import com.jjx.system.domain.dto.AttachmentRefDTO;
import com.jjx.system.service.AttachmentRefService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mapstruct.factory.Mappers;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 真实 MySQL + 报价/订单服务 + Mapper + 事务；外部编号/权限/日志为替身，不启动服务。 */
@EnabledIfEnvironmentVariable(named="JJX_TEST_MYSQL", matches="1")
class QuotationOrderDraftMysqlTest {
    JdbcTemplate jdbc;
    IQuotationService service;
    QuotationMapper quotes;
    SalesQuotationItemMapper quoteItems;
    OrderMapper orders;
    SalesOrderProductMapper lines;
    ProductMapper products;
    EventPublisher events;
    SalesCustomer customer;
    Product product;
    SalesQuotation quote;
    String prefix;
    volatile boolean failLine;
    volatile boolean failRef;
    Long attachmentId;
    SysAttachmentRefMapper refs;
    DataSourceTransactionManager tx;

    private <T> T proxy(Object target, Class<T> type) {
        var f = new ProxyFactory(target); f.addAdvice(new TransactionInterceptor(tx, new AnnotationTransactionAttributeSource()));
        return type.cast(f.getProxy());
    }
    @BeforeEach void setup() throws Exception {
        prefix = "Q31" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
        var ds = new DriverManagerDataSource(); ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl(System.getenv().getOrDefault("JJX_TEST_MYSQL_URL", "jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"));
        ds.setUsername(System.getenv().getOrDefault("DB_USER", "root")); ds.setPassword(System.getenv().getOrDefault("DB_PASS", "123456"));
        jdbc = new JdbcTemplate(ds); tx = new DataSourceTransactionManager(ds);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        for (var c : List.of(QuotationMapper.class, SalesQuotationItemMapper.class, QuotationFlowMapper.class, OrderMapper.class,
                SalesOrderProductMapper.class, CustomerMapper.class, ProductMapper.class, SysAttachmentRefMapper.class)) config.addMapper(c);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config); factory.setGlobalConfig(new GlobalConfig().setBanner(false));
        var session = new SqlSessionTemplate(factory.getObject()); quotes = session.getMapper(QuotationMapper.class);
        quoteItems = session.getMapper(SalesQuotationItemMapper.class); orders = session.getMapper(OrderMapper.class);
        lines = session.getMapper(SalesOrderProductMapper.class); products = session.getMapper(ProductMapper.class);
        refs = session.getMapper(SysAttachmentRefMapper.class);
        var attachmentService = mock(AttachmentRefService.class);
        when(attachmentService.create(any())).thenAnswer(i -> {
            AttachmentRefDTO dto = i.getArgument(0); var ref = new SysAttachmentRef(); BeanUtils.copyProperties(dto, ref);
            refs.insert(ref); if (failRef) throw new BusinessException("attachment reference failed"); return ref.getId();
        });
        var productService = mock(ISalesOrderProductService.class);
        when(productService.batchAdd(anyList())).thenAnswer(i -> {
            for (SalesOrderProductDTO dto : i.<List<SalesOrderProductDTO>>getArgument(0)) {
                var row = new SalesOrderProduct(); BeanUtils.copyProperties(dto, row); lines.insert(row);
            }
            if (failLine) throw new BusinessException("line save failed"); return true;
        });
        var sequence = mock(RedisSequenceService.class);
        when(sequence.generateBusinessNumberByType(anyString(), anyString(), anyString(), anyInt()))
                .thenAnswer(i -> prefix + UUID.randomUUID().toString().substring(0,8));
        var customers = mock(ICustomerService.class); when(customers.selectCustomerById(anyLong())).thenReturn(new CustomerVO());
        var orderTarget = QuotationOrderDraftTest.service(OrderServiceImpl.class, Map.of(OrderMapper.class, orders,
                SalesOrderConverter.class, Mappers.getMapper(SalesOrderConverter.class), RedisSequenceService.class, sequence,
                ISalesOrderProductService.class, productService, ICustomerService.class, customers, ProductMapper.class, products,
                ProductCustomerValidator.class, new ProductCustomerValidator(products), AttachmentRefService.class, attachmentService));
        events = mock(EventPublisher.class);
        var target = QuotationOrderDraftTest.service(QuotationServiceImpl.class, Map.of(QuotationMapper.class, quotes,
                SalesQuotationItemMapper.class, quoteItems, QuotationFlowMapper.class, session.getMapper(QuotationFlowMapper.class),
                IOrderService.class, proxy(orderTarget, IOrderService.class), ProductMapper.class, products,
                SysAttachmentRefMapper.class, session.getMapper(SysAttachmentRefMapper.class), EventPublisher.class, events));
        service = proxy(target, IQuotationService.class);
        customer = new SalesCustomer(); customer.setCustomerCode(prefix); customer.setCustomerName(prefix); session.getMapper(CustomerMapper.class).insert(customer);
        product = new Product(); product.setProductCode(prefix); product.setProductName(prefix); product.setCustomerId(customer.getCustomerId());
        product.setCreateBy("Codex"); product.setUpdateBy("Codex"); product.setCreateTime(java.time.LocalDateTime.now()); product.setUpdateTime(java.time.LocalDateTime.now()); products.insert(product);
        quote = new SalesQuotation(); quote.setQuotationNo(prefix); quote.setCustomerId(customer.getCustomerId()); quote.setCustomerName(prefix);
        quote.setQuotationDate(LocalDate.now()); quote.setValidUntil(LocalDate.now().plusDays(20)); quote.setQuotationStatus(QuotationStatus.ACCEPTED.getValue());
        quote.setContactPerson("source contact"); quote.setContactPhone("1234"); quote.setTraceId(prefix); quote.setCurrency("USD"); quote.setExchangeRate(BigDecimal.valueOf(7));
        quote.setTaxRate(BigDecimal.valueOf(13)); quote.setShippingFee(BigDecimal.valueOf(5)); quote.setDiscountAmount(BigDecimal.ONE); quotes.insert(quote);
        var line = new SalesQuotationItem(); line.setQuotationId(quote.getQuotationId()); line.setProductId(product.getProductId());
        line.setProductCode(prefix); line.setProductName(prefix); line.setQuantity(2); line.setUnitPrice(BigDecimal.TEN); line.setAmount(BigDecimal.valueOf(20)); line.setUnit("PCS"); quoteItems.insert(line);
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO sys_attachment(file_name,file_path) VALUES(?,?)", java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, prefix); statement.setString(2, ".tmp/" + prefix); return statement;
        }, key);
        attachmentId = key.getKey().longValue();
        var ref = new SysAttachmentRef(); ref.setAttachmentId(attachmentId); ref.setSourceType("quotation"); ref.setSourceId(quote.getQuotationId());
        ref.setProductId(product.getProductId()); ref.setProductCode(product.getProductCode()); ref.setDocumentType("spec"); refs.insert(ref);
    }
    @AfterEach void cleanup() {
        if (jdbc == null || customer == null || customer.getCustomerId() == null) return;
        Long cid = customer.getCustomerId();
        if (attachmentId != null) {
            jdbc.update("DELETE FROM sys_attachment_ref WHERE attachment_id=?", attachmentId);
            // 文件元数据按系统规则逻辑删除，不绕过禁止物理删除的触发器；本测试未创建实际文件。
            jdbc.update("UPDATE sys_attachment SET deleted=1 WHERE id=? AND file_name=?", attachmentId, prefix);
        }
        jdbc.update("DELETE p FROM sales_order_product p JOIN sales_order o ON p.order_id=o.order_id WHERE o.customer_id=? AND o.order_no LIKE ?", cid, prefix+"%");
        jdbc.update("DELETE FROM sales_order WHERE customer_id=? AND order_no LIKE ?", cid, prefix+"%");
        if (quote != null && quote.getQuotationId() != null) {
            jdbc.update("DELETE FROM sales_quotation_flow WHERE quotation_id=?", quote.getQuotationId());
            jdbc.update("DELETE FROM sales_quotation_item WHERE quotation_id=?", quote.getQuotationId());
            jdbc.update("DELETE FROM sales_quotation WHERE quotation_id=? AND quotation_no=?", quote.getQuotationId(), prefix);
        }
        if (product != null && product.getProductId() != null) jdbc.update("DELETE FROM product WHERE product_id=? AND product_code=?", product.getProductId(), prefix);
        jdbc.update("DELETE FROM sales_customer WHERE customer_id=? AND customer_code=?", cid, prefix);
    }
    private int countOrders() { return jdbc.queryForObject("SELECT COUNT(*) FROM sales_order WHERE quotation_id=?", Integer.class, quote.getQuotationId()); }
    @Test void previewCancelWritesNothingAndSaveCopiesSourceThenRejectsRepeat() {
        var draft = service.getOrderDraft(quote.getQuotationId()); assertEquals(0, countOrders());
        assertEquals(QuotationStatus.ACCEPTED.getValue(), quotes.selectById(quote.getQuotationId()).getQuotationStatus());
        draft.getOrder().setDeliveryAddress("new delivery address"); Long id = service.convertToOrder(quote.getQuotationId(), draft);
        var saved = orders.selectById(id); assertEquals(prefix, saved.getTraceId()); assertEquals(quote.getQuotationId(), saved.getQuotationId());
        assertEquals("source contact", saved.getContactPerson()); assertEquals("USD", saved.getCurrency());
        assertEquals("new delivery address", saved.getDeliveryAddress()); assertEquals(2, saved.getTotalQuantity());
        assertEquals(0, BigDecimal.valueOf(5).compareTo(saved.getShippingFee()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_attachment_ref WHERE source_type='sales_order' AND source_id=? AND attachment_id=?", Integer.class, id, attachmentId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_attachment_ref WHERE source_type='quotation' AND source_id=? AND attachment_id=?", Integer.class, quote.getQuotationId(), attachmentId));
        assertThrows(BusinessException.class, () -> service.convertToOrder(quote.getQuotationId(), draft));
        assertEquals(1, countOrders()); verify(events).fire(eq("quotation.converted"), anyMap());
    }
    @Test void failedLineRollsBackOrderItemsQuotationStateAndEvents() {
        var draft = service.getOrderDraft(quote.getQuotationId()); failLine = true;
        assertThrows(BusinessException.class, () -> service.convertToOrder(quote.getQuotationId(), draft));
        assertEquals(0, countOrders()); assertNull(quotes.selectById(quote.getQuotationId()).getConvertedOrderId());
        assertEquals(QuotationStatus.ACCEPTED.getValue(), quotes.selectById(quote.getQuotationId()).getQuotationStatus());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sales_order_product WHERE product_id=?", Integer.class, product.getProductId()));
        verifyNoInteractions(events);
    }
    @Test void concurrentSavesCreateOneOrder() throws Exception {
        var first = service.getOrderDraft(quote.getQuotationId()); var second = service.getOrderDraft(quote.getQuotationId());
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        java.util.function.Function<QuotationOrderDraftDTO, Callable<Boolean>> action = draft -> () -> {
            start.await(); try { service.convertToOrder(quote.getQuotationId(), draft); return true; } catch (BusinessException e) { return false; }
        };
        try {
            var a = pool.submit(action.apply(first)); var b = pool.submit(action.apply(second)); start.countDown();
            assertNotEquals(a.get(25, TimeUnit.SECONDS), b.get(25, TimeUnit.SECONDS)); assertEquals(1, countOrders());
            verify(events).fire(eq("quotation.converted"), anyMap());
        } finally { pool.shutdown(); assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS)); }
    }
    @Test void attachmentFailureRollsBackOrderReferenceAndQuotationFlow() {
        var draft = service.getOrderDraft(quote.getQuotationId()); failRef = true;
        assertThrows(BusinessException.class, () -> service.convertToOrder(quote.getQuotationId(), draft));
        assertEquals(0, countOrders()); assertNull(quotes.selectById(quote.getQuotationId()).getConvertedOrderId());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_attachment_ref WHERE attachment_id=?", Integer.class, attachmentId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sales_quotation_flow WHERE quotation_id=?", Integer.class, quote.getQuotationId()));
        verifyNoInteractions(events);
    }
}
