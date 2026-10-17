package com.jjx.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.sql.ResultSet;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttachmentDisplayNameServiceTest {
    private JdbcTemplate jdbc;
    private AttachmentDisplayNameService service;
    private String base;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new AttachmentDisplayNameService(jdbc, new ObjectMapper());
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
            Map.of("label", "客户图纸", "ext_data", "{\"parent\":\"CUSTOMER_DOC\"}")));
        base = "INQ-3-P7-CUS-CD-客户图纸";
    }

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        if (TransactionSynchronizationManager.hasResource(service)) {
            TransactionSynchronizationManager.unbindResource(service);
        }
    }

    @Test
    void baseNameKeepsOriginalPartsAndTwoDigitSuffixIsAddedSeparately() {
        assertEquals(base, service.build("inquiry", 3L, 7L, "CUSTOMER_DRAWING"));
        assertEquals(base + "-01", service.numbered(base, 1));
        assertEquals(base + "-02", service.numbered(base, 2));
        assertEquals(base + "-100", service.numbered(base, 100));
    }

    @Test
    void generatedNamesAreRecognizedButUnrelatedCustomNamesAreNot() {
        assertTrue(service.isGeneratedName(base, base)); // pre-numbering automatic name
        assertTrue(service.isGeneratedName(base + "-01", base));
        assertTrue(service.isGeneratedName(base + "-100", base));
        assertFalse(service.isGeneratedName("customer final.pdf", base));
        assertFalse(service.isGeneratedName(base + "-A1", base));
    }

    @Test
    void renumberOrdersReferencesAndSkipsCustomNameCollisions() {
        StubJdbcTemplate stub = new StubJdbcTemplate();
        stub.rows = List.of(new Row(4L, null), new Row(1L, base),
            new Row(2L, base + "-01"), new Row(3L, "manual.pdf"));
        AttachmentDisplayNameService names = new AttachmentDisplayNameService(stub, new ObjectMapper());

        names.renumber("inquiry", 3L, 7L, "CUSTOMER_DRAWING", "operator");

        assertEquals(List.of(1L, 2L, 4L), stub.updates.stream().map(update -> (Long) update[2]).toList());
        assertEquals(List.of(base + "-01", base + "-02", base + "-03"),
            stub.updates.stream().map(update -> (String) update[0]).toList());
        assertTrue(stub.groupQuery.contains("ORDER BY id FOR UPDATE"));
        assertTrue(stub.groupQuery.contains("product_id <=> ?"));
    }

    @Test
    void groupLockIsDeduplicatedAndHeldUntilTransactionCompletion() {
        when(jdbc.queryForObject(startsWith("SELECT GET_LOCK"), eq(Integer.class), anyString())).thenReturn(1);
        when(jdbc.queryForObject(startsWith("SELECT RELEASE_LOCK"), eq(Integer.class), anyString())).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();
        var first = new AttachmentDisplayNameService.GroupKey("inquiry", 3L, 7L, "CUSTOMER_DRAWING");
        var same = new AttachmentDisplayNameService.GroupKey("inquiry", 3L, 7L, "CUSTOMER_DRAWING");
        var other = new AttachmentDisplayNameService.GroupKey("inquiry", 3L, 8L, "CUSTOMER_DRAWING");

        service.lockGroups(List.of(first, same, other));
        service.lockGroups(List.of(first));
        verify(jdbc, times(2)).queryForObject(startsWith("SELECT GET_LOCK"), eq(Integer.class), anyString());
        verify(jdbc, never()).queryForObject(startsWith("SELECT RELEASE_LOCK"), eq(Integer.class), anyString());

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        verify(jdbc, times(2)).queryForObject(startsWith("SELECT RELEASE_LOCK"), eq(Integer.class), anyString());
    }

    private record Row(Long id, String displayName) {}

    private static class StubJdbcTemplate extends JdbcTemplate {
        private List<Row> rows = List.of();
        private final List<Object[]> updates = new ArrayList<>();
        private String groupQuery = "";

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            return List.of(Map.of("label", "客户图纸", "ext_data", "{\"parent\":\"CUSTOMER_DOC\"}"));
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            groupQuery = sql;
            List<T> result = new ArrayList<>();
            List<Row> ordered = rows.stream().sorted(java.util.Comparator.comparing(Row::id)).toList();
            for (int i = 0; i < ordered.size(); i++) {
                Row row = ordered.get(i);
                ResultSet rs = mock(ResultSet.class);
                try {
                    when(rs.getLong("id")).thenReturn(row.id());
                    when(rs.getString("display_name")).thenReturn(row.displayName());
                    result.add(rowMapper.mapRow(rs, i));
                } catch (Exception ex) {
                    throw new AssertionError(ex);
                }
            }
            return result;
        }

        @Override
        public int update(String sql, Object... args) {
            updates.add(args);
            return 1;
        }
    }
}
