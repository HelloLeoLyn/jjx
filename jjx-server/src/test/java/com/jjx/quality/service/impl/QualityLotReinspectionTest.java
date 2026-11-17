package com.jjx.quality.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jjx.quality.domain.entity.QualityLot;
import com.jjx.quality.mapper.QualityLotMapper;

/** 复检建批收敛：幂等与版本不跳号（dev-20261117-029）。 */
@ExtendWith(MockitoExtension.class)
class QualityLotReinspectionTest {

    @Mock
    private QualityLotMapper lotMapper;

    @InjectMocks
    private QualityLotServiceImpl service;

    @Test
    void repeatedCreateReturnsExistingSublot() {
        QualityLot existing = new QualityLot();
        existing.setLotId(99L);
        existing.setBatchNo("RW-1");
        when(lotMapper.selectOne(any())).thenReturn(existing);

        QualityLot result = service.createReinspectionLot(1L, 2L, 3L, "M1", "PET",
                new BigDecimal("5"), 10L, 1, "RW-1", "返工复检");

        assertEquals(99L, result.getLotId());
        verify(lotMapper, never()).insert(any(QualityLot.class));
    }

    @Test
    void versionContinuesFromMaxChild() {
        QualityLot child = new QualityLot();
        child.setVersion(3);
        when(lotMapper.selectOne(any())).thenReturn(child);

        assertEquals(4, service.nextReinspectionVersion(10L, 1));
    }

    @Test
    void versionStartsAfterParentWhenNoChild() {
        when(lotMapper.selectOne(any())).thenReturn(null);
        assertEquals(2, service.nextReinspectionVersion(10L, 1));
    }
}
