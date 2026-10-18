package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.*;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.*;
import com.jjx.product.service.impl.ProductServiceImpl;
import com.jjx.sales.domain.converter.*;
import com.jjx.sales.domain.dto.*;
import com.jjx.sales.domain.entity.*;
import com.jjx.sales.domain.vo.*;
import com.jjx.sales.enums.*;
import com.jjx.sales.mapper.*;
import com.jjx.sales.service.*;
import com.jjx.system.service.*;
import com.jjx.system.utils.SecurityUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * H2事务/回读契约测试。真实订单/产品建档/明细替换/转换器/客户校验/事务代理；
 * 持久层适配器以JDBC保存实体快照，外部编号/权限/日志及工程资料查询为替身。
 * 不覆盖生产MySQL的Mapper SQL、真实业务数据或浏览器。
 */
class OrderProductEntryParityTest {
    JdbcTemplate jdbc;
    DataSourceTransactionManager tx;
    ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    OrderMapper orders;
    ProductMapper products;
    IOrderService service;
    ISampleOrderService samples;
    SalesOrderProductServiceImpl lines;
    ProductServiceImpl productService;
    WorkSpecBindingService versions;
    IEngineeringBomService boms;
    IEngineeringRoutingService routes;
    MockedStatic<SecurityUtils> security;
    boolean failProductInsert;
    boolean failLineSave;

    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds); tx = new DataSourceTransactionManager(ds);
        jdbc.execute("CREATE TABLE orders(id BIGINT PRIMARY KEY, payload CLOB)");
        jdbc.execute("CREATE TABLE products(id BIGINT PRIMARY KEY, code VARCHAR(50) UNIQUE, payload CLOB)");
        jdbc.execute("CREATE TABLE lines(id BIGINT PRIMARY KEY, order_id BIGINT, payload CLOB)");
        jdbc.execute("CREATE TABLE caller_write(id INT)");
        var configuration = new MybatisConfiguration();
        for (Class<?> entity : List.of(Product.class, SalesOrderProduct.class, EngineeringBom.class,
                EngineeringRouting.class, EngineeringFilm.class, SalesSampleTransfer.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "parity"), entity);
        }
        security = mockStatic(SecurityUtils.class);
        security.when(SecurityUtils::getUserId).thenReturn(7L);
        security.when(SecurityUtils::getUsername).thenReturn("tester");
        products = mock(ProductMapper.class);
        when(products.selectById(anyLong())).thenAnswer(c -> read("products", c.getArgument(0), Product.class));
        when(products.selectOne(any(Wrapper.class))).thenAnswer(c -> {
            Object code = parameter(c.getArgument(0));
            var ids = jdbc.queryForList("SELECT id FROM products WHERE code=?", Long.class, code);
            return ids.isEmpty() ? null : read("products", ids.getFirst(), Product.class);
        });
        when(products.insert(any(Product.class))).thenAnswer(c -> {
            Product p = c.getArgument(0); p.setProductId(next("products"));
            jdbc.update("INSERT INTO products VALUES(?,?,?)", p.getProductId(), p.getProductCode(), encode(p));
            if (failProductInsert) throw new BusinessException("建档异常");
            return 1;
        });
        productService = mock(ProductServiceImpl.class, CALLS_REAL_METHODS);
        field(productService, "productMapper", products);
        field(productService, "logSaveService", mock(LogSaveService.class));
        var lineMapper = mock(SalesOrderProductMapper.class);
        when(lineMapper.selectList(any(Wrapper.class))).thenAnswer(c -> jdbc.query(
                "SELECT payload FROM lines WHERE order_id=? ORDER BY id",
                (rs, n) -> decode(rs.getString(1), SalesOrderProduct.class), parameter(c.getArgument(0))));
        versions = mock(WorkSpecBindingService.class);
        when(versions.latestPublishedId(anyLong())).thenAnswer(c -> c.getArgument(0, Long.class) * 10);
        lines = spy(new SalesOrderProductServiceImpl(Mappers.getMapper(SalesOrderProductConverter.class), versions));
        field(lines, "baseMapper", lineMapper);
        doAnswer(c -> {
            for (SalesOrderProduct line : c.<Collection<SalesOrderProduct>>getArgument(0)) {
                line.setId(next("lines"));
                jdbc.update("INSERT INTO lines VALUES(?,?,?)", line.getId(), line.getOrderId(), encode(line));
                if (failLineSave) throw new BusinessException("明细保存异常");
            }
            return true;
        }).when(lines).saveBatch(anyCollection());
        doAnswer(c -> { jdbc.update("DELETE FROM lines WHERE order_id=?", c.getArgument(0, Long.class)); return true; })
                .when(lines).deleteByOrderId(anyLong());
        orders = mock(OrderMapper.class);
        when(orders.checkOrderNoUnique(anyString())).thenReturn(0);
        when(orders.selectById(anyLong())).thenAnswer(c -> read("orders", c.getArgument(0), SalesOrder.class));
        when(orders.insert(any(SalesOrder.class))).thenAnswer(c -> {
            SalesOrder o = c.getArgument(0); o.setOrderId(next("orders")); o.setDeleted(0); // 模拟目标库默认值
            jdbc.update("INSERT INTO orders VALUES(?,?)", o.getOrderId(), encode(o)); return 1;
        });
        when(orders.updateById(any(SalesOrder.class))).thenAnswer(c -> {
            SalesOrder patch = c.getArgument(0);
            ObjectNode merged = json.valueToTree(read("orders", patch.getOrderId(), SalesOrder.class));
            ObjectNode values = json.valueToTree(patch);
            values.properties().forEach(e -> { if (!e.getValue().isNull()) merged.set(e.getKey(), e.getValue()); });
            return jdbc.update("UPDATE orders SET payload=? WHERE id=?", merged.toString(), patch.getOrderId());
        });
        var customers = mock(ICustomerService.class);
        when(customers.selectCustomerById(anyLong())).thenReturn(new CustomerVO());
        boms = mock(IEngineeringBomService.class); routes = mock(IEngineeringRoutingService.class);
        var target = mock(OrderServiceImpl.class, CALLS_REAL_METHODS);
        field(target, "orderMapper", orders);
        field(target, "orderConverter", Mappers.getMapper(SalesOrderConverter.class));
        field(target, "productCustomerValidator", new ProductCustomerValidator(products));
        field(target, "productMapper", products); field(target, "productService", productService);
        field(target, "orderProductService", lines); field(target, "customerService", customers);
        field(target, "logSaveService", mock(LogSaveService.class));
        field(target, "changeRecorder", mock(OperLogChangeRecorder.class));
        field(target, "bomService", boms); field(target, "routingService", routes);
        service = (IOrderService) proxy(target);

        var sample = mock(SampleOrderServiceImpl.class, CALLS_REAL_METHODS);
        field(sample, "orderMapper", orders); field(sample, "orderService", service);
        field(sample, "orderProductService", lines); field(sample, "productMapper", products);
        field(sample, "bomService", boms); field(sample, "routingService", routes);
        field(sample, "quotationMapper", mock(QuotationMapper.class));
        field(sample, "sampleTransferMapper", mock(SalesSampleTransferMapper.class));
        field(sample, "engineeringFilmMapper", mock(com.jjx.product.mapper.EngineeringFilmMapper.class));
        samples = (ISampleOrderService) proxy(sample);
    }
    @AfterEach void close() { security.close(); }
    Object proxy(Object target) {
        var factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(tx, new AnnotationTransactionAttributeSource()));
        return factory.getProxy();
    }
    void field(Object target, String name, Object value) { ReflectionTestUtils.setField(target, name, value); }
    Object parameter(Wrapper<?> wrapper) {
        wrapper.getSqlSegment();
        return ((LambdaQueryWrapper<?>) wrapper).getParamNameValuePairs().values().iterator().next();
    }
    String encode(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new AssertionError(e); } }
    <T> T decode(String value, Class<T> type) { try { return json.readValue(value, type); } catch (Exception e) { throw new AssertionError(e); } }
    <T> T read(String table, Object id, Class<T> type) {
        var rows = jdbc.queryForList("SELECT payload FROM " + table + " WHERE id=?", String.class, id);
        return rows.isEmpty() ? null : decode(rows.getFirst(), type);
    }
    long next(String table) { return jdbc.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM " + table, Long.class); }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    Product archive(long id, long customer, boolean released) {
        Product p = new Product(); p.setProductId(id); p.setCustomerId(customer); p.setProductCode("P" + id);
        p.setProductName("档案" + id); p.setProductStatus((released ? ProductEnums.Status.RELEASED : ProductEnums.Status.DEVELOPING).getValue());
        p.setSpecJson("{\"panelType\":\"A\"}"); p.setUnit("PCS");
        jdbc.update("INSERT INTO products VALUES(?,?,?)", id, p.getProductCode(), encode(p)); return p;
    }
    SalesOrderProductDTO item(Long id, String code) {
        var i = new SalesOrderProductDTO(); i.setProductId(id); i.setProductCode(code); i.setProductName("新产品");
        i.setQuantity(2); i.setUnitPrice(BigDecimal.TEN); i.setAmount(new BigDecimal("20")); i.setUnit("PCS"); return i;
    }
    SalesOrderAddDTO add(SalesOrderProductDTO... items) {
        var d = new SalesOrderAddDTO(); d.setCustomerId(1L); d.setCustomerName("客户1"); d.setOrderNo("SO-test");
        d.setOrderType(SalesOrderTypeEnum.STANDARD.getCode()); d.setItems(List.of(items));
        d.setShippingFee(new BigDecimal("5")); d.setTaxRate(new BigDecimal("13")); d.setDiscountAmount(new BigDecimal("2")); return d;
    }
    SalesOrderEditDTO edit(long id, SalesOrderProductDTO... items) {
        var d = new SalesOrderEditDTO(); d.setOrderId(id); d.setCustomerId(1L); d.setOrderType(SalesOrderTypeEnum.STANDARD.getCode());
        d.setItems(List.of(items)); d.setShippingFee(new BigDecimal("5")); d.setTaxRate(new BigDecimal("13")); d.setDiscountAmount(new BigDecimal("2")); return d;
    }
    void assertLinked(long order, long product, String code, String name, String spec) {
        var o = service.selectOrderById(order); var i = o.getItems().getFirst();
        assertEquals(1L, o.getCustomerId()); assertEquals(product, i.getProductId()); assertEquals(code, i.getProductCode());
        assertEquals(name, i.getProductName()); assertEquals(spec, i.getSpecification());
        assertEquals(1L, read("products", product, Product.class).getCustomerId());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void existingProductCreateAndEditReadBack(boolean released) {
        var p = archive(8, 1, released); var i = item(8L, " P8 "); i.setProductName("另一产品旧名");
        long id = service.insertOrder(add(i)); assertLinked(id, 8, "P8", "档案8", p.getSpecJson());
        var changed = item(8L, "P8"); changed.setId(service.selectOrderById(id).getItems().getFirst().getId());
        changed.setSpecification("订单规格"); assertTrue(service.updateOrder(edit(id, changed)));
        assertLinked(id, 8, "P8", "档案8", "订单规格");
        assertEquals(0, service.selectOrderById(id).getFinalAmount().compareTo(new BigDecimal("25.60")));
    }
    @Test void newProductCreateAndEditUseRealCustomerDraftEntry() {
        var i = item(null, " NEW "); i.setSpecification("行规格"); i.setSerialNo("001"); i.setPanelType("A");
        long id = service.insertOrder(add(i)); long product = i.getProductId();
        assertLinked(id, product, "NEW", "新产品", "行规格");
        var p = read("products", product, Product.class);
        assertEquals(ProductEnums.Status.DEVELOPING.getValue(), p.getProductStatus());
        assertEquals("sales_order", p.getFromSource()); assertTrue(p.getSpecJson().contains("001"));
        var newLine = item(null, "NEW2"); newLine.setSpecification("新规格");
        assertTrue(service.updateOrder(edit(id, newLine)));
        assertLinked(id, newLine.getProductId(), "NEW2", "新产品", "新规格");
        assertEquals(2, count("products"));
    }
    @Test void codeOnlyResolvesArchiveAndMissingCodeUsesSelectedId() {
        archive(8, 1, false); var i = item(null, "P8"); i.setProductName(null);
        long id = service.insertOrder(add(i)); assertEquals(8L, i.getProductId());
        var changed = item(8L, null); changed.setProductName(null); service.updateOrder(edit(id, changed));
        assertEquals("P8", service.selectOrderById(id).getItems().getFirst().getProductCode());
    }
    @Test void switchedProductDoesNotInheritWorkSpecAndDistinctProductsRemainValid() {
        archive(8, 1, true); archive(9, 1, true); archive(10, 1, true);
        long id = service.insertOrder(add(item(8L, "P8"), item(10L, "P10")));
        var old = service.selectOrderById(id).getItems(); assertEquals(2, old.size());
        var switched = item(9L, "P9"); switched.setId(old.getFirst().getId());
        var retained = item(10L, "P10"); retained.setId(old.getLast().getId());
        service.updateOrder(edit(id, switched, retained));
        var saved = service.selectOrderById(id).getItems();
        assertEquals(90L, saved.getFirst().getWorkSpecVersionId());
        assertEquals(100L, saved.getLast().getWorkSpecVersionId());
    }
    @Test void duplicateIdAndUnarchivedCodeRejectWithoutPartialWrites() {
        archive(8, 1, true);
        assertThrows(BusinessException.class, () -> service.insertOrder(add(item(8L, "P8"), item(8L, "P8"))));
        assertEquals(0, count("orders")); assertEquals(0, count("lines"));
        assertThrows(BusinessException.class, () -> service.insertOrder(add(item(null, "NEW1"), item(null, " new1 "))));
        assertEquals(1, count("products")); assertEquals(0, count("orders"));
        long id = service.insertOrder(add(item(8L, "P8")));
        assertThrows(BusinessException.class, () -> service.updateOrder(edit(id, item(8L, "P8"), item(8L, "P8"))));
        assertEquals(1, service.selectOrderById(id).getItems().size());
    }
    @Test void rejectsCrossCustomerByIdOrCodeAndMismatchedIdentity() {
        archive(8, 2, true); archive(9, 1, true);
        for (var i : List.of(item(8L, "P8"), item(null, "P8"), item(9L, "P8"), item(99L, "P99"))) {
            assertThrows(BusinessException.class, () -> service.insertOrder(add(i))); assertEquals(0, count("orders"));
        }
        assertEquals(0, count("lines")); assertEquals(2, count("products"));
    }
    @Test void missingNewIdentityOrCustomerRejected() {
        for (var i : List.of(item(null, null), item(null, " "))) {
            assertThrows(BusinessException.class, () -> service.insertOrder(add(i)));
        }
        var i = item(null, "NEW"); i.setProductName(" ");
        assertThrows(BusinessException.class, () -> service.insertOrder(add(i)));
        var d = add(item(null, "NEW")); d.setCustomerId(null);
        assertThrows(BusinessException.class, () -> service.insertOrder(d));
        assertEquals(0, count("orders")); assertEquals(0, count("products"));
    }
    @Test void productFailureRollsBackInsertAndEditIncludingEarlierLines() {
        archive(8, 1, true); long id = service.insertOrder(add(item(8L, "P8")));
        String previous = encode(service.selectOrderById(id));
        failProductInsert = true;
        assertThrows(BusinessException.class, () -> service.insertOrder(add(item(null, "NEW"))));
        assertThrows(BusinessException.class, () -> service.updateOrder(edit(id, item(null, "NEW2"))));
        assertEquals(1, count("orders")); assertEquals(1, count("products"));
        assertEquals(previous, encode(service.selectOrderById(id)));
    }
    @Test void nullCreationResultAndWrongReturnedIdentityRollback() {
        doReturn(null).when(productService).ensureDraftProduct(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThrows(BusinessException.class, () -> service.insertOrder(add(item(null, "NEW"))));
        assertEquals(0, count("orders"));
        archive(8, 1, false);
        doReturn(8L).when(productService).ensureDraftProduct(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThrows(BusinessException.class, () -> service.insertOrder(add(item(null, "NEW"))));
        assertEquals(0, count("orders"));
    }
    @Test void downstreamFailureRollsBackNewProductOrderAndReplacedLines() {
        archive(8, 1, true); long id = service.insertOrder(add(item(8L, "P8")));
        String previous = encode(service.selectOrderById(id)); failLineSave = true;
        assertThrows(BusinessException.class, () -> service.updateOrder(edit(id, item(null, "NEW"))));
        assertEquals(1, count("products")); assertEquals(previous, encode(service.selectOrderById(id)));
    }
    @Test void historicalUnknownAmountsKeepTheirConfirmationGuard() {
        archive(8, 1, true); long id = service.insertOrder(add(item(8L, "P8")));
        var old = service.selectOrderById(id); var d = edit(id, item(8L, "P8"));
        d.setShippingFee(null); d.setTotalAmount(old.getTotalAmount()); d.setTotalQuantity(old.getTotalQuantity());
        d.setDiscountRate(old.getDiscountRate()); d.setDiscountAmount(old.getDiscountAmount());
        assertTrue(service.updateOrder(d));
        assertEquals(0, service.selectOrderById(id).getFinalAmount().compareTo(old.getFinalAmount()));
        d.getItems().getFirst().setUnitPrice(BigDecimal.ONE);
        assertThrows(BusinessException.class, () -> service.updateOrder(d));
    }
    @Test void draftCanSaveButCannotGenerateProduction() {
        archive(8, 1, false); long id = service.insertOrder(add(item(8L, "P8")));
        var confirmed = new SalesOrder(); confirmed.setOrderId(id); confirmed.setOrderStatus(SalesOrderStatusEnum.CONFIRMED.getValue());
        orders.updateById(confirmed);
        assertTrue(assertThrows(BusinessException.class, () -> service.createInstances(id)).getMessage().contains("未发布"));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void releasedProductStillRequiresApprovedBomAndRouting(boolean bomReady) {
        archive(8, 1, true); long id = service.insertOrder(add(item(8L, "P8")));
        var confirmed = new SalesOrder(); confirmed.setOrderId(id); confirmed.setOrderStatus(SalesOrderStatusEnum.CONFIRMED.getValue());
        orders.updateById(confirmed);
        if (bomReady) when(boms.getOne(any(Wrapper.class))).thenReturn(new EngineeringBom());
        String message = assertThrows(BusinessException.class, () -> service.createInstances(id)).getMessage();
        assertTrue(message.contains(bomReady ? "工艺路线" : "BOM"));
    }
    @Test void lineConverterKeepsDetailIdentitySeparateFromOrderIdentity() {
        var converter = Mappers.getMapper(SalesOrderProductConverter.class);
        var dto = item(8L, "P8"); dto.setId(20L); dto.setOrderId(100L);
        assertNull(converter.toEntity(dto).getId());
        var entity = new SalesOrderProduct(); entity.setId(20L);
        converter.updateEntity(entity, dto);
        assertEquals(20L, entity.getId()); assertEquals(100L, entity.getOrderId());
    }
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void sampleConversionUsesReadinessAndRollsBackCallerTransaction(int missing) {
        archive(8, 1, missing != 1);
        var bom = new EngineeringBom(); bom.setApproveStatus(missing == 2 ? 0 : 3);
        var route = new EngineeringRouting(); route.setApproveStatus(missing == 3 ? 0 : 3);
        when(boms.getOne(any(Wrapper.class))).thenReturn(bom); when(routes.getOne(any(Wrapper.class))).thenReturn(route);
        long source = service.insertOrder(add(item(8L, "P8")));
        var patch = new SalesOrder(); patch.setOrderId(source); patch.setSampleStatus(SampleOrderStatusEnum.CONFIRMED.getValue());
        orders.updateById(patch);
        var request = add(item(8L, "P8"));
        if (missing != 0) {
            var error = assertThrows(BusinessException.class, () -> new TransactionTemplate(tx).execute(status -> {
                jdbc.update("INSERT INTO caller_write VALUES(1)");
                return samples.convertSampleToProduction(source, request);
            }));
            assertTrue(error.getMessage().startsWith("转量产资料未就绪："));
            assertTrue(error.getMessage().contains(switch (missing) { case 1 -> "产品建档"; case 2 -> "BOM"; default -> "工艺路线"; }));
            assertEquals(0, count("caller_write")); assertEquals(1, count("orders"));
            assertEquals(SampleOrderStatusEnum.CONFIRMED.getValue(), read("orders", source, SalesOrder.class).getSampleStatus());
            assertNull(read("orders", source, SalesOrder.class).getConvertedOrderId());
        } else {
            var created = samples.convertSampleToProduction(source, request);
            assertEquals(2, count("orders")); assertNotNull(created);
            var saved = read("orders", source, SalesOrder.class);
            assertEquals(SampleOrderStatusEnum.TRANSFERRED.getValue(), saved.getSampleStatus());
            assertEquals(created.getOrderId(), saved.getConvertedOrderId());
        }
    }
}
