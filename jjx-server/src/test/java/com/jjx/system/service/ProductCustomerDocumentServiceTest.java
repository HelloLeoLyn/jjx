package com.jjx.system.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductCustomerDocumentServiceTest {
    JdbcTemplate jdbc;
    AttachmentSourcePolicy sources;
    ProductCustomerDocumentService service;

    @BeforeEach
    void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        sources = mock(AttachmentSourcePolicy.class);
        when(sources.canRead(anyString(), anyLong())).thenReturn(true);
        service = new ProductCustomerDocumentService(jdbc, sources);
        jdbc.execute("CREATE TABLE product(product_id BIGINT PRIMARY KEY, product_code VARCHAR(64))");
        jdbc.execute("CREATE TABLE sys_attachment(id BIGINT PRIMARY KEY,file_name VARCHAR(255),file_path VARCHAR(255),"
                + "file_size BIGINT,file_type VARCHAR(64),sha256 VARCHAR(64),version VARCHAR(64),create_by VARCHAR(64),"
                + "create_time TIMESTAMP,update_time TIMESTAMP,deleted INT DEFAULT 0,customer_product_code VARCHAR(64))");
        jdbc.execute("CREATE TABLE sys_attachment_ref(id BIGINT PRIMARY KEY,attachment_id BIGINT,source_type VARCHAR(32),"
                + "source_id BIGINT,product_id BIGINT,product_code VARCHAR(64),document_type VARCHAR(100),"
                + "display_name VARCHAR(255),sort_order INT,update_time TIMESTAMP,deleted INT DEFAULT 0,"
                + "ref_state VARCHAR(20) DEFAULT 'active')");
        jdbc.execute("CREATE TABLE sys_dict_item(dict_code VARCHAR(64),item_key VARCHAR(100),label VARCHAR(100),deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE sales_inquiry(inquiry_id BIGINT,inquiry_no VARCHAR(64),product_id BIGINT,product_code VARCHAR(64),deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE sales_quotation(quotation_id BIGINT,quotation_no VARCHAR(64),deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE sales_quotation_item(quotation_id BIGINT,product_id BIGINT,product_code VARCHAR(64))");
        jdbc.execute("CREATE TABLE sales_order(order_id BIGINT,order_no VARCHAR(64),deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE sales_order_product(order_id BIGINT,product_id BIGINT,product_code VARCHAR(64))");
        jdbc.execute("CREATE TABLE sales_sample_order(order_id BIGINT,product_id BIGINT,product_code VARCHAR(64),deleted INT DEFAULT 0)");
        jdbc.update("INSERT INTO product VALUES(1,'P1'),(2,'P2')");
        jdbc.update("INSERT INTO sales_quotation VALUES(10,'QT10',0)");
        jdbc.update("INSERT INTO sales_quotation_item VALUES(10,1,'P1'),(10,2,'P2')");
        jdbc.update("INSERT INTO sales_inquiry VALUES(20,'INQ20',NULL,'P1',0)");
        jdbc.update("INSERT INTO sys_dict_item VALUES('attachment_document_type','CUSTOMER_DRAWING','客户图纸',0)");
    }

    void ref(long id, String type, long source, Long productId, String productCode) {
        jdbc.update("INSERT INTO sys_attachment(id,file_name,create_time) VALUES(?, ?, CURRENT_TIMESTAMP)", id, "original-" + id);
        jdbc.update("INSERT INTO sys_attachment_ref(id,attachment_id,source_type,source_id,product_id,product_code,document_type,display_name,sort_order)"
                + " VALUES(?,?,?,?,?,?,'CUSTOMER_DRAWING',?,0)", id, id, type, source, productId, productCode, "ref-name-" + id);
    }

    List<Long> ids(long productId) {
        return service.list(productId).stream().map(row -> ((Number) row.get("id")).longValue()).sorted().toList();
    }

    @Test
    void codeOnlyReferencesAppearForTheirOwnProductWithReferenceMetadata() {
        ref(1, "quotation", 10, null, "P1");
        ref(2, "quotation", 10, null, "P2");
        ref(3, "inquiry", 20, null, "P1");
        assertEquals(List.of(1L, 3L), ids(1));
        assertEquals(List.of(2L), ids(2));
        var row = service.list(1L).stream().filter(r -> ((Number) r.get("id")).longValue() == 1L).findFirst().orElseThrow();
        assertEquals("ref-name-1", row.get("display_name"));
        assertEquals("original-1", row.get("file_name"));
        assertEquals("客户图纸", row.get("category"));
        assertEquals("报价单", row.get("sourceType"));
        assertEquals("QT10", row.get("sourceNo"));
    }

    @Test
    void rejectsMissingConflictingOrInvalidProductOwnershipAndLegacyOnlyFiles() {
        ref(1, "quotation", 10, 1L, null);
        ref(2, "quotation", 10, null, null);
        ref(3, "quotation", 10, 1L, "P2");
        ref(4, "inquiry", 20, null, "P2"); // source belongs to P1
        jdbc.update("INSERT INTO sys_attachment(id,customer_product_code) VALUES(99,'P1')");
        assertEquals(List.of(1L), ids(1));
        assertEquals(List.of(), ids(2));
    }

    @Test
    void excludesUnlinkedDeletedOrMissingSourcesAndUnauthorizedReferences() {
        ref(1, "quotation", 10, null, "P1");
        ref(2, "quotation", 10, null, "P1");
        ref(3, "quotation", 999, null, "P1");
        ref(4, "inquiry", 20, null, "P1");
        ref(5, "quotation", 10, null, "P1");
        jdbc.update("UPDATE sys_attachment_ref SET deleted=1 WHERE id=1");
        jdbc.update("UPDATE sys_attachment SET deleted=1 WHERE id=2");
        when(sources.canRead("inquiry", 20L)).thenReturn(false);
        assertEquals(List.of(5L), ids(1));
        jdbc.update("UPDATE sales_quotation SET deleted=1 WHERE quotation_id=10");
        assertEquals(List.of(), ids(1));
    }

    @Test
    void orderAndSampleReferencesKeepTheirSourceBoundary() {
        jdbc.update("INSERT INTO sales_order VALUES(30,'SO30',0),(40,'SA40',0)");
        jdbc.update("INSERT INTO sales_order_product VALUES(30,1,'P1'),(40,1,'P1')");
        jdbc.update("INSERT INTO sales_sample_order VALUES(40,1,'P1',0)");
        ref(1, "sales_order", 30, 1L, "P1");
        ref(2, "sample", 40, 1L, "P1");
        ref(3, "sales_order", 40, 1L, "P1");
        assertEquals(List.of(1L, 2L), ids(1));
    }

    /** 真库只读验证普通查询和发布锁读SQL，不执行发布或写数据。 */
    @Test
    @EnabledIfEnvironmentVariable(named = "JJX_SNAPSHOT_SQL_CHECK", matches = "true")
    void queryExplainsAgainstRealSchema() {
        assertEquals("jjx_ro", System.getenv("JJX_TEST_DB_USER"));
        JdbcTemplate database = new JdbcTemplate(new DriverManagerDataSource(
                System.getenv("JJX_TEST_DB_URL"), System.getenv("JJX_TEST_DB_USER"), System.getenv("JJX_TEST_DB_PASSWORD")));
        assertFalse(database.queryForList("EXPLAIN " + ProductCustomerDocumentService.QUERY, 1L).isEmpty());
        assertFalse(database.queryForList("EXPLAIN " + ProductCustomerDocumentService.QUERY + " FOR SHARE", 1L).isEmpty());
        // 真库也实际执行只读SELECT，确保字段和映射可用；不对业务数据数量作固定假设。
        database.queryForList(ProductCustomerDocumentService.QUERY, 1L);
    }
}
