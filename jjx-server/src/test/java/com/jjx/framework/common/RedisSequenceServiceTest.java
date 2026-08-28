package com.jjx.framework.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.system.service.SysConfigService;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisSequenceServiceTest {

    @Test
    void parsesConfiguredRuleAndGeneratesExpectedNumber() {
        SysConfigService configService = configService(
                "{\"prefix\":\"WR-\",\"dateFormat\":\"YYYYMMDD-\",\"digits\":4}");
        RedisSequenceService service = service(configService, 12L);

        RedisSequenceService.BusinessNumberRule rule = service.loadRule(
                "work_report", "OLD", "yyMMdd", 3);
        String number = service.generateBusinessNumber(rule, LocalDate.of(2026, 8, 28), "work_report");

        assertEquals("WR-20260828-0012", number);
    }

    @Test
    void missingConfigurationFallsBackToCallerRule() {
        RedisSequenceService service = service(configService(null), 1L);

        RedisSequenceService.BusinessNumberRule rule = service.loadRule(
                "sales_order", "SO", "yyMMdd", 3);

        assertEquals(new RedisSequenceService.BusinessNumberRule("SO", "yyMMdd", 3), rule);
    }

    @Test
    void invalidConfigurationFallsBackToCallerRule() {
        RedisSequenceService service = service(
                configService("{\"prefix\":\"SO\",\"digits\":0}"), 1L);

        RedisSequenceService.BusinessNumberRule rule = service.loadRule(
                "sales_order", "SO", "yyMMdd", 3);

        assertEquals(new RedisSequenceService.BusinessNumberRule("SO", "yyMMdd", 3), rule);
    }

    private static SysConfigService configService(String value) {
        return new SysConfigService(null) {
            @Override
            public String getValue(String configKey) {
                return value;
            }
        };
    }

    private static RedisSequenceService service(SysConfigService configService, long sequence) {
        return new RedisSequenceService(null, configService, new ObjectMapper()) {
            @Override
            public Long getNextSequence(String redisKey) {
                return sequence;
            }
        };
    }
}
