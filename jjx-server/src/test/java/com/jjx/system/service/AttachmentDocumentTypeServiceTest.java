package com.jjx.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 资料类型来源判断 supports（dev-20261019-001）。 */
class AttachmentDocumentTypeServiceTest {

    private AttachmentDocumentTypeService service(JdbcTemplate jdbc) {
        return new AttachmentDocumentTypeService(jdbc, new ObjectMapper());
    }

    private JdbcTemplate jdbcWith(String extData) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(AttachmentDocumentTypeService.TYPE), eq("T")))
                .thenReturn(List.of(Map.of("ext_data", extData)));
        return jdbc;
    }

    @Test
    void blankTypeIsOrdinaryAttachmentAndAlwaysSupported() {
        assertTrue(service(mock(JdbcTemplate.class)).supports(null, "sales_order"));
        assertTrue(service(mock(JdbcTemplate.class)).supports("  ", "sales_order"));
    }

    @Test
    void quotationOnlyEvidenceIsNotSupportedForSalesOrder() {
        var svc = service(jdbcWith("{\"parent\":\"BUSINESS_DOC\",\"sources\":[\"quotation\"]}"));
        assertFalse(svc.supports("T", "sales_order"));
        assertTrue(svc.supports("T", "quotation"));
    }

    @Test
    void missingOrInvalidTypeIsNotSupported() {
        JdbcTemplate empty = mock(JdbcTemplate.class);
        when(empty.queryForList(anyString(), anyString(), anyString())).thenReturn(List.of());
        assertFalse(service(empty).supports("T", "sales_order"));
        assertFalse(service(jdbcWith("not-json")).supports("T", "sales_order"));
    }
}
