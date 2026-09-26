package com.jjx.production.service.impl;

import com.jjx.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionTaskSupplementSourceTest {

    private JdbcTemplate jdbcTemplate;
    private ProductionTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new ProductionTaskServiceImpl(null, null, null, null, null, jdbcTemplate, null, null);
        doReturn(List.of(21L)).when(jdbcTemplate)
                .query(anyString(), any(RowMapper.class), any(Object[].class));
        when(jdbcTemplate.queryForObject(contains("COUNT(*)"), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SUM(quantity)"), eq(BigDecimal.class), any(Object[].class)))
                .thenReturn(new BigDecimal("2"));
    }

    @Test
    void acceptsOnlyRequestWithinCompletedScrapQuantity() {
        when(jdbcTemplate.queryForObject(contains("SUM(supplement_production_quantity)"),
                eq(BigDecimal.class), any(Object[].class))).thenReturn(new BigDecimal("2"));

        assertDoesNotThrow(() -> service.validateSupplementSource(
                21L, new BigDecimal("2"), 31L, 41L, false));
    }

    @Test
    void rejectsCumulativeSupplementQuantityAboveCompletedScrap() {
        when(jdbcTemplate.queryForObject(contains("SUM(supplement_production_quantity)"),
                eq(BigDecimal.class), any(Object[].class))).thenReturn(new BigDecimal("2.01"));

        assertThrows(BusinessException.class, () -> service.validateSupplementSource(
                21L, new BigDecimal("2"), 31L, 41L, false));
    }
}
