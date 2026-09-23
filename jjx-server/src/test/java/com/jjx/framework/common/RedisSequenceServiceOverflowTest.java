package com.jjx.framework.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.system.mapper.SysNumberSequenceMapper;
import com.jjx.system.service.SysConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisSequenceServiceOverflowTest {

    @Test
    void shouldExpandDigitsAndPublishEventAtOverflowBoundary() {
        SysNumberSequenceMapper mapper = mock(SysNumberSequenceMapper.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(mapper.selectCurrentForUpdate("quality_lot", "20260922")).thenReturn(1000L);
        RedisSequenceService service = service(mapper, publisher);

        String number = service.generateBusinessNumber(
                new RedisSequenceService.BusinessNumberRule("QL", "yyMMdd", 3),
                LocalDate.of(2026, 9, 22), "quality_lot");

        assertEquals("QL2609221000", number);
        verify(publisher).publishEvent(new BusinessNumberOverflowEvent(
                "quality_lot", "20260922", 3, 4, 1000));
    }

    @Test
    void shouldNotRepeatOverflowEventAfterBoundary() {
        SysNumberSequenceMapper mapper = mock(SysNumberSequenceMapper.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(mapper.selectCurrentForUpdate("quality_lot", "20260922")).thenReturn(1001L);
        RedisSequenceService service = service(mapper, publisher);

        String number = service.generateBusinessNumber(
                new RedisSequenceService.BusinessNumberRule("QL", "yyMMdd", 3),
                LocalDate.of(2026, 9, 22), "quality_lot");

        assertEquals("QL2609221001", number);
        verify(publisher, never()).publishEvent(any());
    }

    @SuppressWarnings("unchecked")
    private RedisSequenceService service(SysNumberSequenceMapper mapper,
                                         ApplicationEventPublisher publisher) {
        return new RedisSequenceService(mock(RedisTemplate.class), mock(SysConfigService.class),
                new ObjectMapper(), mapper, publisher);
    }
}
