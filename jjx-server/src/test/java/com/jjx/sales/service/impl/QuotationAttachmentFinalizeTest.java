package com.jjx.sales.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.jjx.common.exception.BusinessException;
import com.jjx.event.EventPublisher;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.IProductService;
import com.jjx.product.service.ProductCustomerValidator;
import com.jjx.sales.domain.converter.SalesQuotationConverter;
import com.jjx.sales.domain.dto.SalesQuotationAddDTO;
import com.jjx.sales.domain.entity.SalesQuotation;
import com.jjx.sales.domain.entity.SalesQuotationItem;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.QuotationFlowMapper;
import com.jjx.sales.mapper.QuotationMapper;
import com.jjx.sales.mapper.SalesInquiryMapper;
import com.jjx.sales.mapper.SalesQuotationItemMapper;
import com.jjx.sales.service.IOrderService;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.mapper.SysAttachmentRefMapper;
import com.jjx.system.service.AttachmentAccessService;
import com.jjx.system.service.AttachmentDisplayNameService;
import com.jjx.system.service.AttachmentDocumentTypeService;
import com.jjx.system.service.AttachmentRefService;
import com.jjx.system.service.AttachmentSourcePolicy;
import com.jjx.system.service.ISysAttachmentService;
import com.jjx.system.service.OperLogChangeRecorder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * dev-20261016-067：报价新增保存时随单附件落定（dev-20261016-066）的后端验证。
 *
 * <p>三条用例都断言「真实库侧结果」：本地起一个 H2（MODE=MySQL）库，MyBatis-Plus 走真 mapper
 * （SqlSessionTemplate 参与 Spring 事务，与 DataSourceTransactionManager 同一连接），
 * JdbcTemplate 直接查同一张表核对行——不是 Mockito 的 verify 断言，也不是「没抛异常」。
 *
 * <p>覆盖：
 * <ol>
 *   <li>保存成功 → sys_attachment_ref 出现落定行（ref_state='active' 且 source_id 有值）；</li>
 *   <li>附件缺 documentType → 抛业务异常并整单回滚（报价单/明细/引用都查不到行）；</li>
 *   <li>上传时已建的草稿行被转正（同一行 ref_state: draft→active、补 source_id），不新增重复行。</li>
 * </ol>
 *
 * <p>被测业务代码一行未改；仅本地内存库建表 + 起真 mapper，不依赖起服务，不依赖手工往真库造数。
 */
class QuotationAttachmentFinalizeTest {

    private static final AtomicInteger DB_SEQ = new AtomicInteger();
    private static final long ATTACHMENT_ID = 900L;
    private static final long PRODUCT_ID = 2L;
    private static final String PRODUCT_CODE = "P-067";
    private static final long DRAFT_REF_ID = 700L;
    private static final String QUOTATION_NO = "QT-067-TEST";
    private static final String TRACE_ID = "TRACE067";
    private static final String LOGIN_ID = "u1";

    private JdbcDataSource ds;
    private JdbcTemplate jdbc;
    private SysAttachmentRefMapper refs;
    private SalesQuotationConverter converter;
    /** 带 @Transactional 的代理（落定必须在真事务里，否则回滚断言没有意义）。 */
    private QuotationServiceImpl service;

    // ---------------------------------------------------------------- 环境

    private void init() {
        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:qt_finalize_" + DB_SEQ.incrementAndGet() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        createSchema();

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("qt-finalize-" + DB_SEQ.get(),
                new SpringManagedTransactionFactory(), ds));
        configuration.addMapper(QuotationMapper.class);
        configuration.addMapper(SalesQuotationItemMapper.class);
        configuration.addMapper(ProductMapper.class);
        configuration.addMapper(SysAttachmentMapper.class);
        configuration.addMapper(SysAttachmentRefMapper.class);
        SqlSessionFactory factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        SqlSessionTemplate template = new SqlSessionTemplate(factory);

        QuotationMapper quotationMapper = template.getMapper(QuotationMapper.class);
        SalesQuotationItemMapper quotationItemMapper = template.getMapper(SalesQuotationItemMapper.class);
        ProductMapper productMapper = template.getMapper(ProductMapper.class);
        SysAttachmentMapper files = template.getMapper(SysAttachmentMapper.class);
        refs = template.getMapper(SysAttachmentRefMapper.class);

        AttachmentAccessService access = mock(AttachmentAccessService.class);
        AttachmentDocumentTypeService types = mock(AttachmentDocumentTypeService.class);
        AttachmentDisplayNameService displayNames = mock(AttachmentDisplayNameService.class);
        when(displayNames.build(anyString(), anyLong(), any(), anyString())).thenReturn("QT-067-DISPLAY");
        AttachmentRefService attachmentRefService = new AttachmentRefService(
                files, refs, new AttachmentSourcePolicy(jdbc), access, types, displayNames, jdbc);

        converter = mock(SalesQuotationConverter.class);
        QuotationServiceImpl target = new QuotationServiceImpl(
                quotationMapper,
                mock(QuotationFlowMapper.class),
                quotationItemMapper,
                mock(SalesInquiryMapper.class),
                productMapper,
                mock(ProductCustomerValidator.class),
                mock(IProductService.class),
                mock(IOrderService.class),
                mock(OrderMapper.class),
                mock(ISysAttachmentService.class),
                mock(RedisSequenceService.class),
                mock(OperLogChangeRecorder.class),
                files,
                refs,
                attachmentRefService,
                converter,
                mock(EventPublisher.class));

        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),
                new AnnotationTransactionAttributeSource()));
        service = (QuotationServiceImpl) proxy.getProxy();
    }

    private void createSchema() {
        jdbc.execute("CREATE TABLE sales_quotation("
                + "quotation_id BIGINT AUTO_INCREMENT PRIMARY KEY, trace_id VARCHAR(32), quotation_no VARCHAR(50),"
                + "quotation_type INT, customer_id BIGINT, customer_name VARCHAR(100), contact_person VARCHAR(50),"
                + "contact_phone VARCHAR(30), quotation_date DATE, valid_until DATE, currency VARCHAR(10),"
                + "exchange_rate DECIMAL(18,4), quotation_status INT, subtotal_amount DECIMAL(18,2),"
                + "tax_rate DECIMAL(18,2), tax_amount DECIMAL(18,2), total_amount DECIMAL(18,2),"
                + "shipping_fee DECIMAL(18,2), discount_amount DECIMAL(18,2), final_amount DECIMAL(18,2),"
                + "remark VARCHAR(500), sales_person_id BIGINT, sales_person_name VARCHAR(50),"
                + "approver_id BIGINT, approver_name VARCHAR(50), approve_time TIMESTAMP,"
                + "approve_remark VARCHAR(500), send_time TIMESTAMP, send_method VARCHAR(20),"
                + "send_remark VARCHAR(500), converted_order_id BIGINT, convert_time TIMESTAMP,"
                + "create_by VARCHAR(64), create_time TIMESTAMP, update_by VARCHAR(64), update_time TIMESTAMP,"
                + "deleted TINYINT NOT NULL DEFAULT 0)");
        jdbc.execute("CREATE TABLE sales_quotation_item("
                + "item_id BIGINT AUTO_INCREMENT PRIMARY KEY, quotation_id BIGINT, product_id BIGINT,"
                + "product_code VARCHAR(64), product_name VARCHAR(255), key_count INT, width DECIMAL(18,2),"
                + "height DECIMAL(18,2), thickness DECIMAL(18,2), material_type VARCHAR(50), color VARCHAR(50),"
                + "circuit_type VARCHAR(50), serial_no VARCHAR(50), panel_type VARCHAR(50),"
                + "panel_feature VARCHAR(255), circuit_feature VARCHAR(255), connector_type VARCHAR(50),"
                + "quantity INT, unit_price DECIMAL(18,2), unit VARCHAR(20), amount DECIMAL(18,2),"
                + "delivery_days INT, estimated_delivery_date DATE, custom_requirements VARCHAR(500),"
                + "logo_requirement VARCHAR(255), certification_requirement VARCHAR(255), item_order INT,"
                + "create_time TIMESTAMP, update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE product(product_id BIGINT PRIMARY KEY, product_code VARCHAR(64))");
        jdbc.execute("CREATE TABLE sys_attachment("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, upload_state VARCHAR(20), expires_at TIMESTAMP,"
                + "trace_id VARCHAR(32), deleted TINYINT NOT NULL DEFAULT 0, file_name VARCHAR(255))");
        jdbc.execute("CREATE TABLE sys_attachment_ref("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, attachment_id BIGINT, source_type VARCHAR(50) NOT NULL,"
                + "source_id BIGINT, source_no VARCHAR(100), product_id BIGINT, product_code VARCHAR(64),"
                + "document_type VARCHAR(100), display_name VARCHAR(255), remark VARCHAR(500),"
                + "sort_order INT NOT NULL DEFAULT 0, deleted TINYINT NOT NULL DEFAULT 0, create_by VARCHAR(64),"
                + "create_time TIMESTAMP, update_by VARCHAR(64), update_time TIMESTAMP, deleted_by VARCHAR(64),"
                + "deleted_at TIMESTAMP, draft_key VARCHAR(64), ref_state VARCHAR(16) NOT NULL DEFAULT 'active')");
        jdbc.update("INSERT INTO product(product_id, product_code) VALUES (?, ?)", PRODUCT_ID, PRODUCT_CODE);
    }

    private MockedStatic<StpUtil> login() {
        MockedStatic<StpUtil> auth = mockStatic(StpUtil.class);
        auth.when(StpUtil::checkLogin).thenAnswer(inv -> null);
        auth.when(() -> StpUtil.checkPermission(anyString())).thenAnswer(inv -> null);
        auth.when(StpUtil::isLogin).thenReturn(true);
        auth.when(StpUtil::getLoginIdAsString).thenReturn(LOGIN_ID);
        return auth;
    }

    /** 上传接口留下的临时件（TEMP），保存时由落定流程接管。 */
    private void seedUploadedTempFile() {
        jdbc.update("INSERT INTO sys_attachment(id, upload_state, expires_at, deleted, file_name) VALUES (?,?,?,0,?)",
                ATTACHMENT_ID, "TEMP", Timestamp.valueOf(LocalDateTime.now().plusDays(3)), "quote.pdf");
    }

    /** 未保存单据时上传即建的草稿行（dev-20261016-012）：归属未定、单据侧/产品侧不可见。 */
    private void seedDraftRef() {
        jdbc.update("INSERT INTO sys_attachment_ref(id, attachment_id, source_type, ref_state, draft_key, create_by, deleted)"
                + " VALUES (?,?,?,?,?,?,0)",
                DRAFT_REF_ID, ATTACHMENT_ID, "quotation", "draft", "draft-067", LOGIN_ID);
    }

    /** 新报价单请求；documentType 传 null 复现「缺资料类型」。 */
    private SalesQuotationAddDTO newQuotationRequest(String documentType) {
        SalesQuotation entity = new SalesQuotation();
        entity.setQuotationNo(QUOTATION_NO);
        entity.setQuotationType(1);
        entity.setCustomerId(1L);
        entity.setCustomerName("测试客户");
        entity.setQuotationDate(LocalDate.now());
        entity.setValidUntil(LocalDate.now().plusDays(30));
        entity.setCurrency("CNY");
        entity.setExchangeRate(BigDecimal.ONE);
        entity.setTraceId(TRACE_ID);
        entity.setSalesPersonId(7L);
        entity.setSalesPersonName("测试员");
        SalesQuotationItem item = new SalesQuotationItem();
        item.setProductId(PRODUCT_ID);
        item.setProductCode(PRODUCT_CODE);
        item.setProductName("薄膜开关");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("10.00"));
        entity.setItems(new ArrayList<>(List.of(item)));
        when(converter.toEntity(any(SalesQuotationAddDTO.class))).thenReturn(entity);

        SalesQuotationAddDTO.QuotationAttachmentDTO attachment = new SalesQuotationAddDTO.QuotationAttachmentDTO();
        attachment.setAttachmentId(ATTACHMENT_ID);
        attachment.setDocumentType(documentType);
        attachment.setProductId(PRODUCT_ID);
        attachment.setProductCode(PRODUCT_CODE);

        SalesQuotationAddDTO dto = new SalesQuotationAddDTO();
        dto.setQuotationType(1);
        dto.setCustomerId(1L);
        dto.setTotalAmount(BigDecimal.TEN);
        dto.setAttachments(new ArrayList<>(List.of(attachment)));
        return dto;
    }

    // ---------------------------------------------------------------- 用例

    /** A) 保存成功 → 库里留下落定引用：ref_state='active'、source_id=报价单ID、挂到单据+产品。 */
    @Test
    void savingQuotationLeavesActiveReferenceInDatabase() {
        init();
        seedUploadedTempFile();
        SalesQuotationAddDTO dto = newQuotationRequest("SALES_QUOTATION_ATTACHMENT");
        try (MockedStatic<StpUtil> auth = login()) {
            Long quotationId = service.insertQuotation(dto);
            assertNotNull(quotationId, "保存成功应返回报价单ID");

            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT attachment_id, source_type, source_id, source_no, product_id, product_code,"
                            + " document_type, display_name, ref_state, deleted FROM sys_attachment_ref"
                            + " WHERE attachment_id=?", ATTACHMENT_ID);
            assertEquals("active", row.get("ref_state"), "落定行的 ref_state 必须是 active");
            assertEquals(quotationId, ((Number) row.get("source_id")).longValue(), "落定行必须带上来源单据ID");
            assertEquals("quotation", row.get("source_type"));
            assertEquals(QUOTATION_NO, row.get("source_no"));
            assertEquals(PRODUCT_ID, ((Number) row.get("product_id")).longValue(), "归属 = 单据 + 当时的产品");
            assertEquals(PRODUCT_CODE, row.get("product_code"));
            assertEquals(0, ((Number) row.get("deleted")).intValue());
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_attachment_ref WHERE attachment_id=?",
                    Long.class, ATTACHMENT_ID));

            assertEquals("BOUND", jdbc.queryForObject("SELECT upload_state FROM sys_attachment WHERE id=?",
                    String.class, ATTACHMENT_ID), "落定后文件应从 TEMP 转 BOUND");
            assertEquals(TRACE_ID, jdbc.queryForObject("SELECT trace_id FROM sys_attachment WHERE id=?",
                    String.class, ATTACHMENT_ID), "建引用时应回填附件链路标识");
            assertEquals(quotationId, jdbc.queryForObject("SELECT quotation_id FROM sales_quotation WHERE quotation_no=?",
                    Long.class, QUOTATION_NO));
        }
    }

    /** B) 缺 documentType → 整单回滚：报价单、明细、引用在库里都查不到行。 */
    @Test
    void missingDocumentTypeRollsBackQuotationAndLeavesNoReference() {
        init();
        seedUploadedTempFile();
        SalesQuotationAddDTO dto = newQuotationRequest(null);
        try (MockedStatic<StpUtil> auth = login()) {
            BusinessException ex = assertThrows(BusinessException.class, () -> service.insertQuotation(dto));
            assertTrue(ex.getMessage().contains("documentType"), "应报缺资料类型，实际：" + ex.getMessage());
        }
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sales_quotation", Long.class),
                "整单回滚：报价单不应入库");
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sales_quotation_item", Long.class),
                "整单回滚：明细不应残留");
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_attachment_ref", Long.class),
                "整单回滚：不应留下半成功引用");
        assertEquals("TEMP", jdbc.queryForObject("SELECT upload_state FROM sys_attachment WHERE id=?",
                String.class, ATTACHMENT_ID), "文件应保持上传态 TEMP，不被落定改写");
    }

    /** C) 已存在草稿行 → 转正（同一行 draft→active、补 source_id），不新增重复行。 */
    @Test
    void existingDraftRowIsPromotedInsteadOfDuplicated() {
        init();
        seedUploadedTempFile();
        seedDraftRef();
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_attachment_ref WHERE ref_state='draft'",
                Long.class), "前置：草稿行已在库");

        SalesQuotationAddDTO dto = newQuotationRequest("SALES_QUOTATION_ATTACHMENT");
        try (MockedStatic<StpUtil> auth = login()) {
            Long quotationId = service.insertQuotation(dto);

            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_attachment_ref", Long.class),
                    "转正不得新增重复行");
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT id, source_type, source_id, product_id, ref_state, draft_key FROM sys_attachment_ref"
                            + " WHERE attachment_id=?", ATTACHMENT_ID);
            assertEquals(DRAFT_REF_ID, ((Number) row.get("id")).longValue(), "必须复用原草稿行的主键");
            assertEquals("active", row.get("ref_state"), "草稿行应被转正为 active");
            assertEquals(quotationId, ((Number) row.get("source_id")).longValue(), "转正时补齐来源单据ID");
            assertEquals(PRODUCT_ID, ((Number) row.get("product_id")).longValue());
            assertEquals("draft-067", row.get("draft_key"), "转正后草稿键保留");
            assertEquals(DRAFT_REF_ID, jdbc.queryForObject("SELECT MAX(id) FROM sys_attachment_ref", Long.class),
                    "库里不应出现更大的新行");
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_attachment_ref WHERE ref_state='draft'",
                    Long.class), "不应再剩草稿态行");
        }
    }
}
