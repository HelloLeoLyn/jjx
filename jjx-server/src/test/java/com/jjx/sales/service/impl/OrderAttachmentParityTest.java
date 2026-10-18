package com.jjx.sales.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jjx.common.exception.BusinessException;
import com.jjx.event.EventPublisher;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.ProductCustomerValidator;
import com.jjx.product.service.WorkSpecBindingService;
import com.jjx.product.service.impl.ProductServiceImpl;
import com.jjx.sales.domain.converter.SalesOrderConverter;
import com.jjx.sales.domain.converter.SalesOrderProductConverter;
import com.jjx.sales.domain.dto.SalesOrderAddDTO;
import com.jjx.sales.domain.dto.SalesOrderEditDTO;
import com.jjx.sales.domain.dto.SalesOrderProductDTO;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.entity.SalesOrderProduct;
import com.jjx.sales.domain.vo.CustomerVO;
import com.jjx.sales.enums.SalesOrderTypeEnum;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.SalesOrderProductMapper;
import com.jjx.sales.service.ICustomerService;
import com.jjx.sales.service.IOrderService;
import com.jjx.system.domain.dto.AttachmentRefDTO;
import com.jjx.system.domain.dto.AttachmentRefMetadataDTO;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.mapper.SysAttachmentRefMapper;
import com.jjx.system.mapper.SysOperLogMapper;
import com.jjx.system.service.AttachmentAccessService;
import com.jjx.system.service.AttachmentDisplayNameService;
import com.jjx.system.service.AttachmentDocumentTypeService;
import com.jjx.system.service.AttachmentRefOperationLogRecorder;
import com.jjx.system.service.AttachmentRefService;
import com.jjx.system.service.AttachmentSourcePolicy;
import com.jjx.system.service.LogSaveService;
import com.jjx.system.utils.SecurityUtils;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.MockedStatic;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 销售订单随单附件：事务归属与附件业务流水（dev-20261018-018）。
 *
 * <p>覆盖卡面「新增/编辑 × 整单/产品级」的归属一致性与业务流水契约：
 * <ol>
 *   <li>新增保存：整单附件与产品级附件同事务落定，编码→产品 ID 回填，文件 TEMP→BOUND 并回填订单链路号；</li>
 *   <li>新建产品 + 附件同次保存后重读：明细产品与附件归属指向同一新产品；</li>
 *   <li>一次保存多条附件 → 一条「附件新增」业务操作（按 sourceType:sourceId:action 归并）；</li>
 *   <li>编辑保存：资料修改 / 解绑流水可定位文件与产品，无变化与重复请求不产虚假/重复流水；</li>
 *   <li>原产品附件不因明细换产品而迁移、不删文件；</li>
 *   <li>反例：ID/编码不一致、产品不属本订单（含其他客户）、未知编码降整单、缺类型、无权、引用异常 → 整单回滚；</li>
 *   <li>样品单底层订单不能被 sales_order 来源占用，样品单流水不混入订单来源。</li>
 * </ol>
 *
 * <p>证据边界：本地 H2（MODE=MySQL）+ <b>真 Mapper</b>（sales_order_product / product / sys_attachment /
 * sys_attachment_ref / sys_oper_log）+ <b>真</b> {@link AttachmentSourcePolicy}、{@link AttachmentRefService}、
 * {@link AttachmentRefOperationLogRecorder}、{@link LogSaveService}，事务由 {@link DataSourceTransactionManager}
 * 真实代理驱动，断言查的是库里的行，不是 Mockito 的 verify。
 * 订单主表仓储为 JDBC 快照替身（{@code OrderMapper.selectById/updateById} 是 MySQL 的
 * {@code UPDATE ... LEFT JOIN}，H2 不兼容），并同步维护 {@code sales_order} 影子行供附件来源自证 SQL 使用；
 * 文件读取权限、资料类型字典、展示名编号服务、工程资料与编号服务为替身。
 * <b>不覆盖</b>：真实业务 MySQL、浏览器/接口层、服务重启后的端到端行为。
 */
class OrderAttachmentParityTest {

    private static final AtomicInteger DB_SEQ = new AtomicInteger();
    private static final long CUSTOMER_ID = 1L;
    private static final String ORDER_NO = "SO-018-TEST";
    private static final String TRACE_ID = "TRACE018";
    private static final String LOGIN_ID = "u1";

    private JdbcTemplate jdbc;
    private DataSourceTransactionManager tx;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    private OrderMapper orders;
    private ProductMapper products;
    private SysAttachmentRefMapper refs;
    private AttachmentRefService attachmentRefService;
    private IOrderService service;
    private ProductServiceImpl productService;
    private MockedStatic<StpUtil> auth;
    private MockedStatic<SecurityUtils> security;
    /** 无权用例开关：置 true 后来源写权限校验抛业务异常。 */
    private boolean denyEdit;

    // ------------------------------------------------------------------ 环境

    @BeforeEach
    void init() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:order_attach_" + DB_SEQ.incrementAndGet() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        tx = new DataSourceTransactionManager(ds);
        createSchema();

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("order-attach-" + DB_SEQ.get(),
                new SpringManagedTransactionFactory(), ds));
        configuration.addMapper(SalesOrderProductMapper.class);
        configuration.addMapper(ProductMapper.class);
        configuration.addMapper(SysAttachmentMapper.class);
        configuration.addMapper(SysAttachmentRefMapper.class);
        configuration.addMapper(SysOperLogMapper.class);
        SqlSessionFactory factory = new com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder()
                .build(configuration);
        SqlSessionTemplate template = new SqlSessionTemplate(factory);

        // 实体字段与真实库同形（测试只声明主键，其余列按 TableInfo 补齐，避免手抄漏列）
        addEntityColumns(Product.class, "product");
        addEntityColumns(SalesOrderProduct.class, "sales_order_product");

        products = template.getMapper(ProductMapper.class);
        SalesOrderProductMapper lineMapper = template.getMapper(SalesOrderProductMapper.class);
        SysAttachmentMapper files = template.getMapper(SysAttachmentMapper.class);
        refs = template.getMapper(SysAttachmentRefMapper.class);
        SysOperLogMapper operLogs = template.getMapper(SysOperLogMapper.class);

        // 真日志写入：附件业务流水与订单变更流水都经 LogSaveService 落到 sys_oper_log
        LogSaveService logSaveService = new LogSaveService(operLogs,
                mock(com.jjx.system.mapper.SysLoginLogMapper.class),
                mock(com.jjx.system.mapper.SysErrorLogMapper.class),
                mock(com.jjx.product.mapper.EngineeringBomMapper.class),
                mock(com.jjx.product.mapper.EngineeringRoutingMapper.class),
                mock(com.jjx.inventory.mapper.InventoryInboundOrderMapper.class),
                mock(com.jjx.inventory.mapper.InventoryOutboundOrderMapper.class),
                mock(OrderMapper.class),
                mock(com.jjx.purchase.mapper.PurchaseOrderMapper.class),
                mock(com.jjx.purchase.mapper.PurchasePaymentMapper.class),
                mock(com.jjx.purchase.mapper.PurchaseDocumentMapper.class));
        AttachmentRefOperationLogRecorder recorder = new AttachmentRefOperationLogRecorder(logSaveService);

        AttachmentAccessService access = mock(AttachmentAccessService.class);
        AttachmentDocumentTypeService types = mock(AttachmentDocumentTypeService.class);
        AttachmentDisplayNameService displayNames = mock(AttachmentDisplayNameService.class);
        attachmentRefService = new AttachmentRefService(files, refs, new AttachmentSourcePolicy(jdbc),
                access, types, displayNames, jdbc);
        ReflectionTestUtils.setField(attachmentRefService, "operationLogs", recorder);

        // 真实产品建档服务（新产品随单建档走这里）
        productService = mock(ProductServiceImpl.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(productService, "productMapper", products);
        ReflectionTestUtils.setField(productService, "logSaveService", logSaveService);

        WorkSpecBindingService versions = mock(WorkSpecBindingService.class);
        when(versions.latestPublishedId(anyLong())).thenAnswer(c -> c.getArgument(0, Long.class) * 10);
        SalesOrderProductServiceImpl lines = spy(new SalesOrderProductServiceImpl(
                Mappers.getMapper(SalesOrderProductConverter.class), versions));
        ReflectionTestUtils.setField(lines, "baseMapper", lineMapper);
        doAnswer(c -> {
            for (SalesOrderProduct line : c.<Collection<SalesOrderProduct>>getArgument(0)) {
                lineMapper.insert(line);
            }
            return true;
        }).when(lines).saveBatch(anyCollection());

        ICustomerService customers = mock(ICustomerService.class);
        when(customers.selectCustomerById(anyLong())).thenReturn(new CustomerVO());

        orders = mock(OrderMapper.class);
        when(orders.checkOrderNoUnique(anyString())).thenReturn(0);
        when(orders.selectById(anyLong())).thenAnswer(c -> readOrder(c.getArgument(0)));
        when(orders.insert(any(SalesOrder.class))).thenAnswer(c -> {
            SalesOrder order = c.getArgument(0);
            long id = nextId("orders");
            order.setOrderId(id);
            order.setDeleted(0);
            // 落库副本模拟目标库默认（order_status 默认 1）；内存实体不回填，保持与真实保存路径一致
            ObjectNode stored = json.valueToTree(order);
            if (stored.get("orderStatus") == null || stored.get("orderStatus").isNull()) stored.put("orderStatus", 1);
            jdbc.update("INSERT INTO orders VALUES(?,?)", id, stored.toString());
            jdbc.update("INSERT INTO sales_order(order_id,order_no,trace_id,customer_id,deleted) VALUES(?,?,?,?,0)",
                    id, order.getOrderNo(), order.getTraceId(), order.getCustomerId());
            return 1;
        });
        when(orders.updateById(any(SalesOrder.class))).thenAnswer(c -> {
            SalesOrder patch = c.getArgument(0);
            ObjectNode merged = json.valueToTree(readOrder(patch.getOrderId()));
            ObjectNode values = json.valueToTree(patch);
            values.properties().forEach(field -> {
                if (!field.getValue().isNull()) merged.set(field.getKey(), field.getValue());
            });
            return jdbc.update("UPDATE orders SET payload=? WHERE id=?", merged.toString(), patch.getOrderId());
        });

        OrderServiceImpl target = mock(OrderServiceImpl.class, CALLS_REAL_METHODS);
        field(target, "orderMapper", orders);
        field(target, "eventPublisher", mock(EventPublisher.class));
        field(target, "redisSequenceService", mock(RedisSequenceService.class));
        field(target, "orderConverter", Mappers.getMapper(SalesOrderConverter.class));
        field(target, "productCustomerValidator", new ProductCustomerValidator(products));
        field(target, "orderProductService", lines);
        field(target, "customerService", customers);
        field(target, "productMapper", products);
        field(target, "productService", productService);
        field(target, "productionOrderService", mock(com.jjx.production.service.ProductionOrderService.class));
        field(target, "bomService", mock(com.jjx.product.service.IEngineeringBomService.class));
        field(target, "routingService", mock(com.jjx.product.service.IEngineeringRoutingService.class));
        field(target, "logSaveService", logSaveService);
        field(target, "changeRecorder", new com.jjx.system.service.OperLogChangeRecorder(logSaveService));
        field(target, "attachmentRefService", attachmentRefService);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(tx, new AnnotationTransactionAttributeSource()));
        service = (IOrderService) proxy.getProxy();

        auth = mockStatic(StpUtil.class);
        auth.when(StpUtil::checkLogin).thenAnswer(inv -> null);
        auth.when(() -> StpUtil.checkPermission(anyString())).thenAnswer(inv -> {
            String permission = inv.getArgument(0);
            if (denyEdit && permission != null && permission.endsWith(":edit")) {
                throw new BusinessException("没有该操作权限");
            }
            return null;
        });
        auth.when(StpUtil::isLogin).thenReturn(true);
        auth.when(StpUtil::getLoginIdAsString).thenReturn(LOGIN_ID);
        security = mockStatic(SecurityUtils.class);
        security.when(SecurityUtils::getUserId).thenReturn(7L);
        security.when(SecurityUtils::getUsername).thenReturn("tester");
        security.when(SecurityUtils::getRealName).thenReturn("测试员");
    }

    @AfterEach
    void close() {
        if (auth != null) auth.close();
        if (security != null) security.close();
    }

    private void createSchema() {
        jdbc.execute("CREATE TABLE product(product_id BIGINT AUTO_INCREMENT PRIMARY KEY, product_code VARCHAR(64))");
        jdbc.execute("CREATE TABLE sales_order_product(id BIGINT AUTO_INCREMENT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE orders(id BIGINT AUTO_INCREMENT PRIMARY KEY, payload CLOB)");
        // 附件来源自证 SQL 读的真表（列与目标库同名）
        jdbc.execute("CREATE TABLE sales_order(order_id BIGINT PRIMARY KEY, order_no VARCHAR(64),"
                + " trace_id VARCHAR(32), customer_id BIGINT, deleted TINYINT NOT NULL DEFAULT 0)");
        jdbc.execute("CREATE TABLE sales_sample_order(order_id BIGINT PRIMARY KEY, deleted TINYINT NOT NULL DEFAULT 0)");
        jdbc.execute("CREATE TABLE sys_attachment(id BIGINT PRIMARY KEY, upload_state VARCHAR(20),"
                + " expires_at TIMESTAMP, trace_id VARCHAR(64), deleted TINYINT NOT NULL DEFAULT 0,"
                + " file_name VARCHAR(255))");
        jdbc.execute("CREATE TABLE sys_attachment_ref("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, attachment_id BIGINT, source_type VARCHAR(50) NOT NULL,"
                + " source_id BIGINT, source_no VARCHAR(100), product_id BIGINT, product_code VARCHAR(64),"
                + " document_type VARCHAR(100), display_name VARCHAR(255), remark VARCHAR(500),"
                + " sort_order INT NOT NULL DEFAULT 0, deleted TINYINT NOT NULL DEFAULT 0, create_by VARCHAR(64),"
                + " create_time TIMESTAMP, update_by VARCHAR(64), update_time TIMESTAMP, deleted_by VARCHAR(64),"
                + " deleted_at TIMESTAMP, draft_key VARCHAR(64), ref_state VARCHAR(16) NOT NULL DEFAULT 'active')");
        jdbc.execute("CREATE TABLE sys_oper_log(id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT,"
                + " username VARCHAR(64), real_name VARCHAR(64), tenant_id BIGINT, module VARCHAR(64),"
                + " business_type INT, oper_url VARCHAR(200), oper_ip VARCHAR(64), oper_param VARCHAR(2000),"
                + " biz_type VARCHAR(50), biz_id VARCHAR(64), trace_id VARCHAR(64), biz_status VARCHAR(50),"
                + " action VARCHAR(50), detail CLOB, cost_time BIGINT, status INT, error_msg VARCHAR(1000),"
                + " user_agent VARCHAR(500), create_time TIMESTAMP)");
    }

    /** 按实体 TableInfo 补齐列，保证真实 Mapper 的 INSERT/SELECT 与目标库同形。 */
    private void addEntityColumns(Class<?> entity, String table) {
        var tableInfo = TableInfoHelper.getTableInfo(entity);
        assertNotNull(tableInfo, "实体未注册: " + entity.getSimpleName());
        List<String> existing = jdbc.queryForList(
                "SELECT LOWER(column_name) FROM information_schema.columns WHERE LOWER(table_name)=?",
                String.class, table);
        for (var field : tableInfo.getFieldList()) {
            if (existing.contains(field.getColumn().toLowerCase())) continue;
            jdbc.execute("ALTER TABLE " + table + " ADD " + field.getColumn() + " " + sqlType(field.getPropertyType()));
        }
    }

    private String sqlType(Class<?> type) {
        if (type == Long.class) return "BIGINT";
        if (type == Integer.class) return "INT";
        if (type == BigDecimal.class) return "DECIMAL(18,4)";
        if (type == LocalDateTime.class) return "TIMESTAMP";
        if (type == LocalDate.class) return "DATE";
        if (type == Boolean.class) return "TINYINT";
        return "VARCHAR(4000)";
    }

    private void field(Object target, String name, Object value) {
        ReflectionTestUtils.setField(target, name, value);
    }

    // ------------------------------------------------------------------ 数据准备

    private Product archive(long id, String code, long customerId, boolean released) {
        Product product = new Product();
        product.setProductId(id);
        product.setProductCode(code);
        product.setProductName("档案" + code);
        product.setCustomerId(customerId);
        product.setProductStatus((released ? ProductEnums.Status.RELEASED : ProductEnums.Status.DEVELOPING).getValue());
        product.setUnit("PCS");
        jdbc.update("INSERT INTO product(product_id,product_code,product_name,customer_id,product_status,unit)"
                + " VALUES(?,?,?,?,?,?)", id, code, product.getProductName(), customerId,
                product.getProductStatus(), product.getUnit());
        return product;
    }

    /** 上传接口留下的临时件（TEMP），保存时由落定流程接管。 */
    private void seedTempFile(long id, String fileName) {
        jdbc.update("INSERT INTO sys_attachment(id,upload_state,expires_at,deleted,file_name) VALUES(?,?,?,0,?)",
                id, "TEMP", Timestamp.valueOf(LocalDateTime.now().plusDays(3)), fileName);
    }

    private SalesOrderProductDTO item(Long productId, String code) {
        SalesOrderProductDTO item = new SalesOrderProductDTO();
        item.setProductId(productId);
        item.setProductCode(code);
        item.setProductName("新产品");
        item.setQuantity(2);
        item.setUnitPrice(BigDecimal.TEN);
        item.setUnit("PCS");
        return item;
    }

    private SalesOrderAddDTO.OrderAttachmentDTO att(long fileId, String documentType,
                                                    Long productId, String productCode) {
        SalesOrderAddDTO.OrderAttachmentDTO attachment = new SalesOrderAddDTO.OrderAttachmentDTO();
        attachment.setAttachmentId(fileId);
        attachment.setDocumentType(documentType);
        attachment.setProductId(productId);
        attachment.setProductCode(productCode);
        return attachment;
    }

    private SalesOrderAddDTO add(SalesOrderProductDTO item) {
        SalesOrderAddDTO dto = new SalesOrderAddDTO();
        dto.setCustomerId(CUSTOMER_ID);
        dto.setCustomerName("客户1");
        dto.setOrderNo(ORDER_NO);
        dto.setOrderDate(new java.util.Date());
        dto.setDeliveryDate(new java.util.Date());
        dto.setOrderType(SalesOrderTypeEnum.STANDARD.getCode());
        dto.setCurrency("CNY");
        dto.setExchangeRate(BigDecimal.ONE);
        dto.setTotalAmount(BigDecimal.TEN);
        dto.setTotalQuantity(2);
        dto.setShippingFee(new BigDecimal("5"));
        dto.setTaxRate(new BigDecimal("13"));
        dto.setDiscountAmount(new BigDecimal("2"));
        dto.setSalesManagerId(7L);
        dto.setSalesManagerName("测试员");
        dto.setTraceId(TRACE_ID);
        dto.setItems(new java.util.ArrayList<>(List.of(item)));
        return dto;
    }

    private SalesOrderEditDTO edit(long orderId, SalesOrderProductDTO item) {
        SalesOrderEditDTO dto = new SalesOrderEditDTO();
        dto.setOrderId(orderId);
        dto.setCustomerId(CUSTOMER_ID);
        dto.setCustomerName("客户1");
        dto.setOrderDate(new java.util.Date());
        dto.setDeliveryDate(new java.util.Date());
        dto.setOrderType(SalesOrderTypeEnum.STANDARD.getCode());
        dto.setCurrency("CNY");
        dto.setExchangeRate(BigDecimal.ONE);
        dto.setShippingFee(new BigDecimal("5"));
        dto.setTaxRate(new BigDecimal("13"));
        dto.setDiscountAmount(new BigDecimal("2"));
        dto.setSalesManagerId(7L);
        dto.setSalesManagerName("测试员");
        dto.setItems(new java.util.ArrayList<>(List.of(item)));
        return dto;
    }

    // ------------------------------------------------------------------ 断言助手

    private SalesOrder readOrder(Object id) {
        if (id == null) return null;
        List<String> rows = jdbc.queryForList("SELECT payload FROM orders WHERE id=?", String.class, id);
        if (rows.isEmpty()) return null;
        try {
            return json.readValue(rows.get(0), SalesOrder.class);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private long nextId(String table) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM " + table, Long.class);
    }

    private long countRows(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private Map<String, Object> refRow(long attachmentId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,source_type,source_id,source_no,product_id,product_code,document_type,ref_state,deleted"
                        + " FROM sys_attachment_ref WHERE attachment_id=? AND deleted=0", attachmentId);
        assertEquals(1, rows.size(), "附件 " + attachmentId + " 应有且只有一条有效引用");
        return rows.get(0);
    }

    private List<Map<String, Object>> logs(String action) {
        return jdbc.queryForList("SELECT biz_type,biz_id,trace_id,business_type,action,oper_param,detail"
                + " FROM sys_oper_log WHERE action=? ORDER BY id", action);
    }

    private long logCount(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sys_oper_log WHERE action=?", Long.class, action);
    }

    private String fileState(long attachmentId) {
        return jdbc.queryForObject("SELECT upload_state FROM sys_attachment WHERE id=?", String.class, attachmentId);
    }

    private String fileTrace(long attachmentId) {
        return jdbc.queryForObject("SELECT trace_id FROM sys_attachment WHERE id=?", String.class, attachmentId);
    }

    private AttachmentRefDTO refRequest(long sourceId, long fileId, String documentType,
                                        Long productId, String productCode) {
        AttachmentRefDTO request = new AttachmentRefDTO();
        request.setAttachmentId(fileId);
        request.setSourceType("sales_order");
        request.setSourceId(sourceId);
        request.setDocumentType(documentType);
        request.setProductId(productId);
        request.setProductCode(productCode);
        return request;
    }

    // ------------------------------------------------------------------ 用例：新增

    /**
     * A) 新增保存：整单附件 + 产品级附件（前端只给编码）同事务落定；
     * 一次保存的多条附件在订单业务流水中归并为一条「附件新增」，链路号=订单 traceId。
     */
    @Test
    void createOrderSettlesWholeAndProductAttachmentsInOneTransactionalLogEvent() {
        archive(8, "P8", CUSTOMER_ID, true);
        seedTempFile(900, "整单合同.pdf");
        seedTempFile(901, "产品图纸.pdf");

        SalesOrderAddDTO dto = add(item(8L, "P8"));
        dto.setAttachments(List.of(att(900, "ORDER_CONTRACT", null, null),
                att(901, "PRODUCT_DRAWING", null, "P8")));

        Long orderId = service.insertOrder(dto);
        assertNotNull(orderId);

        // 整单级：订单 + 无产品
        Map<String, Object> whole = refRow(900);
        assertEquals("sales_order", whole.get("source_type"));
        assertEquals(orderId, ((Number) whole.get("source_id")).longValue());
        assertEquals(ORDER_NO, whole.get("source_no"), "来源单号由 AttachmentRefService 按来源自证取得");
        assertNull(whole.get("product_id"));
        assertNull(whole.get("product_code"));
        assertEquals("active", whole.get("ref_state"));
        // 产品级：编码 → 产品 ID 回填，归属订单 + 产品
        Map<String, Object> productLevel = refRow(901);
        assertEquals(8L, ((Number) productLevel.get("product_id")).longValue());
        assertEquals("P8", productLevel.get("product_code"));
        assertEquals(orderId, ((Number) productLevel.get("source_id")).longValue());

        // 文件随落定转 BOUND 并回填订单链路号
        assertEquals("BOUND", fileState(900));
        assertEquals("BOUND", fileState(901));
        assertEquals(TRACE_ID, fileTrace(900));

        // 同一次保存的多条附件 = 一条业务操作
        assertEquals(1L, logCount("附件新增"), "同一次保存的多条附件必须归并为一条业务流水");
        Map<String, Object> event = logs("附件新增").get(0);
        assertEquals("sales_order", event.get("biz_type"));
        assertEquals(String.valueOf(orderId), event.get("biz_id"));
        assertEquals(TRACE_ID, event.get("trace_id"), "附件流水必须按订单链路号归属");
        assertEquals(1, ((Number) event.get("business_type")).intValue());
        String detail = String.valueOf(event.get("detail"));
        var parsed = JSONUtil.parseObj(detail);
        assertEquals(2, parsed.getJSONArray("attachments").size(), "归并后应带两条附件引用");
        assertTrue(parsed.getJSONArray("changes").toList(String.class).stream()
                .anyMatch(change -> change.contains("整单合同.pdf") && change.contains("整单")));
        assertTrue(parsed.getJSONArray("changes").toList(String.class).stream()
                .anyMatch(change -> change.contains("产品图纸.pdf") && change.contains("产品 P8")));

        // 明细与产品同时落库（原子提交的另一半）
        assertEquals(8L, jdbc.queryForObject(
                "SELECT product_id FROM sales_order_product WHERE order_id=?", Long.class, orderId));

        // 附件流水与订单创建流水属同一条订单业务流水（同一 traceId）
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_oper_log WHERE oper_url='order.create' AND trace_id=? AND biz_id=?",
                Long.class, TRACE_ID, String.valueOf(orderId)));
    }

    /** B) 新建产品 + 附件同次保存：附件归属到随单建档的新产品，重读一致。 */
    @Test
    void newProductAndItsAttachmentSavedTogetherReadBackConsistent() {
        seedTempFile(900, "新产品图纸.pdf");
        SalesOrderProductDTO item = item(null, "P-FRESH");
        item.setProductName("新产品名称");
        item.setSerialNo("001");

        SalesOrderAddDTO dto = add(item);
        dto.setAttachments(List.of(att(900, "PRODUCT_DRAWING", null, "P-FRESH")));

        Long orderId = service.insertOrder(dto);
        Long newProductId = item.getProductId();
        assertNotNull(newProductId, "随单应完成新产品建档");

        Map<String, Object> reloaded = refRow(900);
        assertEquals(newProductId, ((Number) reloaded.get("product_id")).longValue());
        assertEquals("P-FRESH", reloaded.get("product_code"));
        assertEquals(newProductId, jdbc.queryForObject(
                "SELECT product_id FROM sales_order_product WHERE order_id=?", Long.class, orderId));
        assertEquals("P-FRESH", jdbc.queryForObject(
                "SELECT product_code FROM product WHERE product_id=?", String.class, newProductId));
        assertEquals(1L, countRows("product"), "不应额外建档第二个产品");
        assertEquals(1L, logCount("附件新增"));
    }

    // ------------------------------------------------------------------ 用例：编辑

    /** C) 编辑保存：原产品附件不迁移到新产品、不删文件；新增附件按订单链路号留痕。 */
    @Test
    void editDoesNotMigrateOldProductAttachmentAndSettlesNewOne() {
        archive(8, "P8", CUSTOMER_ID, true);
        archive(9, "P9", CUSTOMER_ID, true);
        seedTempFile(901, "P8图纸.pdf");
        seedTempFile(902, "P9图纸.pdf");

        SalesOrderAddDTO create = add(item(8L, "P8"));
        create.setAttachments(List.of(att(901, "PRODUCT_DRAWING", null, "P8")));
        Long orderId = service.insertOrder(create);
        Long oldRefId = ((Number) refRow(901).get("id")).longValue();

        // 明细换成 P9，只提交 P9 的新附件（旧附件不再出现在请求里）
        SalesOrderEditDTO update = edit(orderId, item(9L, "P9"));
        update.setAttachments(List.of(att(902, "PRODUCT_DRAWING", null, "P9")));
        assertTrue(service.updateOrder(update));

        // 旧引用保持原产品归属，文件仍在、未被静默迁移或删除
        Map<String, Object> oldRef = refRow(901);
        assertEquals(oldRefId, ((Number) oldRef.get("id")).longValue());
        assertEquals(8L, ((Number) oldRef.get("product_id")).longValue());
        assertEquals("P8", oldRef.get("product_code"));
        assertEquals("BOUND", fileState(901));
        assertEquals(0, jdbc.queryForObject("SELECT deleted FROM sys_attachment WHERE id=?", Integer.class, 901));
        // 新附件落到新产品名下
        assertEquals(9L, ((Number) refRow(902).get("product_id")).longValue());
        assertEquals("P9", refRow(902).get("product_code"));

        // 两次保存 = 两条「附件新增」（各自一次业务操作），都挂订单链路号
        List<Map<String, Object>> added = logs("附件新增");
        assertEquals(2, added.size());
        added.forEach(event -> assertEquals(TRACE_ID, event.get("trace_id")));

        // 明细已换成 P9：附件归属属于当前订单明细
        assertEquals(9L, jdbc.queryForObject(
                "SELECT product_id FROM sales_order_product WHERE order_id=?", Long.class, orderId));
    }

    /** D) 资料修改 / 解绑可定位文件与产品；无变化与重复请求不产虚假或重复流水。 */
    @Test
    void metadataChangeAndUnlinkAreTraceableAndIdempotent() {
        archive(8, "P8", CUSTOMER_ID, true);
        seedTempFile(901, "产品图纸.pdf");
        SalesOrderAddDTO create = add(item(8L, "P8"));
        create.setAttachments(List.of(att(901, "PRODUCT_DRAWING", null, "P8")));
        Long orderId = service.insertOrder(create);
        Long refId = ((Number) refRow(901).get("id")).longValue();

        // 改类型：一条「附件资料修改」，能定位文件与产品
        AttachmentRefMetadataDTO changeType = new AttachmentRefMetadataDTO();
        changeType.setDocumentType("PRODUCT_DRAWING_V2");
        attachmentRefService.update(refId, changeType);
        assertEquals(1L, logCount("附件资料修改"));
        Map<String, Object> typeEvent = logs("附件资料修改").get(0);
        assertEquals("sales_order", typeEvent.get("biz_type"));
        assertEquals(String.valueOf(orderId), typeEvent.get("biz_id"));
        assertEquals(TRACE_ID, typeEvent.get("trace_id"));
        assertEquals(2, ((Number) typeEvent.get("business_type")).intValue(), "资料修改按「修改」记流水");
        assertTrue(String.valueOf(typeEvent.get("oper_param")).contains("产品图纸.pdf"));
        assertTrue(String.valueOf(typeEvent.get("oper_param")).contains("产品 P8"));
        assertTrue(String.valueOf(typeEvent.get("oper_param")).contains("资料类型"));

        // 无变化：不写流水
        attachmentRefService.update(refId, changeType);
        assertEquals(1L, logCount("附件资料修改"));

        // 改备注：新增一条，仍能定位文件与产品
        AttachmentRefMetadataDTO changeRemark = new AttachmentRefMetadataDTO();
        changeRemark.setDocumentType("PRODUCT_DRAWING_V2");
        changeRemark.setRemark("客供");
        attachmentRefService.update(refId, changeRemark);
        assertEquals(2L, logCount("附件资料修改"));
        assertTrue(String.valueOf(logs("附件资料修改").get(1).get("oper_param")).contains("备注"));

        // 解绑：一条「附件解绑」，detail 带文件；文件转 ORPHAN 设保留期
        attachmentRefService.unlink(refId);
        assertEquals(1L, logCount("附件解绑"));
        Map<String, Object> unlinkEvent = logs("附件解绑").get(0);
        assertEquals("sales_order", unlinkEvent.get("biz_type"));
        assertEquals(TRACE_ID, unlinkEvent.get("trace_id"));
        assertEquals(3, ((Number) unlinkEvent.get("business_type")).intValue());
        assertTrue(String.valueOf(unlinkEvent.get("oper_param")).contains("产品图纸.pdf"));
        assertEquals("ORPHAN", fileState(901));
        assertNotNull(jdbc.queryForObject("SELECT expires_at FROM sys_attachment WHERE id=?", Timestamp.class, 901));

        // 重复解绑：幂等，不再写流水
        attachmentRefService.unlink(refId);
        assertEquals(1L, logCount("附件解绑"));
    }

    /** E) 重复提交同一附件（同来源 + 同产品）：返回原引用，不新增行、不重复写流水。 */
    @Test
    void duplicateCreateIsIdempotentWithoutDuplicateLogs() {
        archive(8, "P8", CUSTOMER_ID, true);
        seedTempFile(901, "产品图纸.pdf");
        SalesOrderAddDTO create = add(item(8L, "P8"));
        create.setAttachments(List.of(att(901, "PRODUCT_DRAWING", null, "P8")));
        Long orderId = service.insertOrder(create);
        Long refId = ((Number) refRow(901).get("id")).longValue();

        Long again = attachmentRefService.create(refRequest(orderId, 901, "PRODUCT_DRAWING", 8L, "P8"));

        assertEquals(refId, again, "同意来源+同附件+同产品应复用原引用");
        assertEquals(1L, countRows("sys_attachment_ref"));
        assertEquals(1L, logCount("附件新增"));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT biz_type,oper_url,action,trace_id FROM sys_oper_log WHERE biz_id=? ORDER BY id",
                String.valueOf(orderId));
        assertEquals(2, rows.size(), "本单业务流水 = 创建 + 一条附件新增，实际：" + rows);
        assertEquals("order", rows.get(0).get("biz_type"));
        assertEquals(TRACE_ID, rows.get(0).get("trace_id"));
        assertEquals(TRACE_ID, rows.get(1).get("trace_id"), "附件流水与订单创建流水同链路（同一次订单业务流水）");
    }

    /** F) 临时上传 / 移除待保存件不产流水（未保存不落定）。 */
    @Test
    void tempUploadAndDraftRemovalWriteNoBusinessLog() {
        seedTempFile(902, "待保存件.pdf");
        jdbc.update("INSERT INTO sys_attachment_ref(id,attachment_id,source_type,ref_state,draft_key,create_by,deleted)"
                + " VALUES(700,902,'sales_order','draft','draft-018',?,0)", LOGIN_ID);

        attachmentRefService.removeDraft(700L);

        assertEquals(0L, countRows("sys_oper_log"), "临时上传/移除待保存件不得产生业务流水");
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_attachment_ref WHERE ref_state='active'", Long.class));
        assertEquals("TEMP", fileState(902));
    }

    // ------------------------------------------------------------------ 用例：反例（整单回滚）

    /** G) 产品 ID 与编码不一致 → 拒绝并整单回滚。 */
    @Test
    void mismatchedProductIdAndCodeRollsBackWholeOrder() {
        archive(8, "P8", CUSTOMER_ID, true);
        archive(9, "P9", CUSTOMER_ID, true);
        seedTempFile(900, "整单合同.pdf");

        SalesOrderAddDTO dto = add(item(8L, "P8"));
        dto.setAttachments(List.of(att(900, "ORDER_CONTRACT", 8L, "P9")));

        BusinessException error = assertThrows(BusinessException.class, () -> service.insertOrder(dto));
        assertTrue(error.getMessage().contains("ID与编码不一致"), "实际：" + error.getMessage());
        assertNothingPersisted(900);
    }

    /** H) 产品不属于本订单（含其他客户的产品）→ 拒绝并整单回滚。 */
    @Test
    void productOutsideOrderRollsBackWholeOrder() {
        archive(8, "P8", CUSTOMER_ID, true);
        archive(10, "P10", 2L, true);   // 其他客户的产品
        archive(11, "P11", CUSTOMER_ID, true); // 同客户、但不在本订单明细
        seedTempFile(900, "整单合同.pdf");

        for (SalesOrderAddDTO.OrderAttachmentDTO attachment : List.of(
                att(900, "ORDER_CONTRACT", 10L, null),
                att(900, "ORDER_CONTRACT", 11L, "P11"))) {
            SalesOrderAddDTO dto = add(item(8L, "P8"));
            dto.setAttachments(List.of(attachment));
            BusinessException error = assertThrows(BusinessException.class, () -> service.insertOrder(dto));
            assertTrue(error.getMessage().contains("不属于当前订单明细"), "实际：" + error.getMessage());
            assertNothingPersisted(900);
        }
    }

    /** I) 未知编码静默降为整单附件（不留「有编码无产品 ID」的悬空归属）。 */
    @Test
    void unknownProductCodeFallsBackToWholeOrderAttachment() {
        archive(8, "P8", CUSTOMER_ID, true);
        seedTempFile(900, "客供参考.pdf");

        SalesOrderAddDTO dto = add(item(8L, "P8"));
        dto.setAttachments(List.of(att(900, "CUSTOMER_PROVIDED", null, "UNKNOWN-CODE")));

        Long orderId = service.insertOrder(dto);
        Map<String, Object> ref = refRow(900);
        assertNull(ref.get("product_id"));
        assertNull(ref.get("product_code"), "查不到的编码必须清掉，不能留下悬空归属");
        assertEquals(orderId, ((Number) ref.get("source_id")).longValue());
        assertEquals(1L, logCount("附件新增"));
        assertTrue(String.valueOf(logs("附件新增").get(0).get("oper_param")).contains("整单"));
    }

    /** J) 缺资料类型 → 服务层拦截并整单回滚（与 Controller 同口径）。 */
    @Test
    void missingDocumentTypeRollsBackWholeOrder() {
        archive(8, "P8", CUSTOMER_ID, true);
        seedTempFile(900, "整单合同.pdf");

        SalesOrderAddDTO dto = add(item(8L, "P8"));
        dto.setAttachments(List.of(att(900, null, null, null)));

        BusinessException error = assertThrows(BusinessException.class, () -> service.insertOrder(dto));
        assertTrue(error.getMessage().contains("documentType"), "实际：" + error.getMessage());
        assertNothingPersisted(900);
    }

    /** K) 无附件写权限（sales:order:edit）→ 来源校验失败并整单回滚。 */
    @Test
    void missingEditPermissionRollsBackWholeOrder() {
        archive(8, "P8", CUSTOMER_ID, true);
        seedTempFile(900, "整单合同.pdf");

        SalesOrderAddDTO dto = add(item(8L, "P8"));
        dto.setAttachments(List.of(att(900, "ORDER_CONTRACT", null, null)));
        denyEdit = true;

        BusinessException error = assertThrows(BusinessException.class, () -> service.insertOrder(dto));
        assertTrue(error.getMessage().contains("权限"), "实际：" + error.getMessage());
        assertNothingPersisted(900);
    }

    /** L) 引用异常（附件不存在）出现在第二条：第一条已建的引用一并回滚，不留半成功。 */
    @Test
    void referenceFailureOnSecondAttachmentRollsBackFirstReference() {
        archive(8, "P8", CUSTOMER_ID, true);
        seedTempFile(900, "整单合同.pdf");

        SalesOrderAddDTO dto = add(item(8L, "P8"));
        dto.setAttachments(List.of(att(900, "ORDER_CONTRACT", null, null),
                att(999, "ORDER_CONTRACT", null, null)));

        assertThrows(BusinessException.class, () -> service.insertOrder(dto));
        assertNothingPersisted(900);
        assertEquals(0L, countRows("sys_attachment_ref"), "第一条附件引用不得残留");
        assertEquals("TEMP", fileState(900));
    }

    /** M) 样品单底层订单不得被 sales_order 来源占用；样品单流水不混入订单来源。 */
    @Test
    void sampleBackedOrderCannotBeClaimedBySalesOrderSource() {
        archive(8, "P8", CUSTOMER_ID, true);
        seedTempFile(900, "样品图纸.pdf");
        SalesOrderAddDTO create = add(item(8L, "P8"));
        Long orderId = service.insertOrder(create);
        // 样品单泳道：同一 order_id 下挂 sales_sample_order 行
        jdbc.update("INSERT INTO sales_sample_order(order_id,deleted) VALUES(?,0)", orderId);

        BusinessException error = assertThrows(BusinessException.class,
                () -> attachmentRefService.create(refRequest(orderId, 900, "PRODUCT_DRAWING", null, "P8")));
        assertTrue(error.getMessage().contains("不存在或不唯一"), "实际：" + error.getMessage());
        assertEquals(0L, countRows("sys_attachment_ref"));
        assertEquals(0L, logCount("附件新增"), "越权/错来源不得留附件流水");

        // 样品单来源可落定，但流水 biz_type=sample，不冒充订单（sales_order）流水
        AttachmentRefDTO sampleRequest = refRequest(orderId, 900, "SAMPLE_DRAWING", null, "P8");
        sampleRequest.setSourceType("sample");
        attachmentRefService.create(sampleRequest);
        assertEquals("sample", refRow(900).get("source_type"));
        assertEquals(1L, logCount("附件新增"));
        assertEquals("sample", logs("附件新增").get(0).get("biz_type"));
    }

    /** 整单回滚后：订单、明细、引用都查不到行，文件保持上传态。 */
    private void assertNothingPersisted(long attachmentId) {
        assertEquals(0L, countRows("orders"), "整单回滚：订单不应入库");
        assertEquals(0L, countRows("sales_order"), "整单回滚：来源影子行不应残留");
        assertEquals(0L, countRows("sales_order_product"), "整单回滚：明细不应残留");
        assertEquals(0L, countRows("sys_attachment_ref"), "整单回滚：不应留下半成功引用");
        assertEquals(0L, countRows("sys_oper_log"), "整单回滚：不应留下流水");
        assertEquals("TEMP", fileState(attachmentId), "文件应保持上传态 TEMP");
    }
}
