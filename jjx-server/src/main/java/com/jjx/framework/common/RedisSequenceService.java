package com.jjx.framework.common;

import com.jjx.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.system.service.SysConfigService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Redis序列号服务
 * 使用Redis原子操作生成各种业务序列号
 * 格式：业务代码 + 日期(yyMMdd) + 序列号(4位)
 * 例如：SO2605180001、PCO2605180001
 * 每个业务代码在同一天独立自增，互不影响
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSequenceService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SysConfigService sysConfigService;
    private final ObjectMapper objectMapper;

    /** 日期格式化器 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");

    /** 序列号长度 */
    private static final int SEQUENCE_LENGTH = 4;

    /** 最大序列号 */
    private static final long MAX_SEQUENCE = 9999L;

    /** Redis key 默认过期天数 */
    private static final int DEFAULT_TTL_DAYS = 30;

    /** Redis key 前缀 */
    private static final String SEQUENCE_KEY_PREFIX = "seq:";

    /**
     * 业务代码枚举
     */
    @Getter
    public enum BizCode {
        SO("SO", "销售订单"),
        PCO("PCO", "采购订单"),
        PO("PO", "产品订单"),
        WPO("WPO", "生产订单"),
        CUST("CST", "客户编码");

        private final String code;
        private final String label;

        BizCode(String code, String desc) {
            this.code = code;
            this.label = desc;
        }
    }

    /**
     * 生成业务编号
     * 格式：业务代码 + 日期(yyMMdd) + 序列号(4位)
     * 例如：SO2605180001、PCO2605180001
     * 每个业务代码在同一天独立自增，互不影响
     *
     * @param bizCode 业务代码枚举
     * @return 业务编号
     */
    public String generateBizNumber(BizCode bizCode) {
        LocalDate today = LocalDate.now();
        String datePart = today.format(DATE_FORMATTER); // yyMMdd

        // Redis key: seq:{业务代码}:{yyMMdd}，例如 seq:SO:260518
        String redisKey = SEQUENCE_KEY_PREFIX + bizCode.getCode() + ":" + datePart;

        // 原子递增获取序列号
        Long sequence = getNextSequence(redisKey);

        // 验证序列号范围
        if (sequence > MAX_SEQUENCE) {
            log.error("序列号已达到最大值: {}，业务: {}，日期: {}", sequence, bizCode.getCode(), datePart);
            throw new BusinessException(bizCode.getLabel() + "序列号已达到最大值，日期: " + datePart);
        }

        // 生成编号：业务代码 + 日期 + 3位序列号
        String bizNumber = bizCode.getCode() + datePart + String.format("%03d", sequence);

        log.info("生成{}编号: {}，日期: {}，序列号: {}", bizCode.getLabel(), bizNumber, datePart, sequence);
        return bizNumber;
    }

    /**
     * 获取下一个序列号（原子操作）
     * 每个 Redis key 独立自增，互不影响
     */
    public Long getNextSequence(String redisKey) {
        try {
            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();

            // 原子递增，如果key不存在会自动创建并设置为1
            Long sequence = valueOps.increment(redisKey, 1L);

            // 如果是新创建的key，设置过期时间
            if (sequence != null && sequence == 1) {
                redisTemplate.expire(redisKey, DEFAULT_TTL_DAYS, TimeUnit.DAYS);
                log.debug("创建新的序列号键: {}，设置TTL: {}天", redisKey, DEFAULT_TTL_DAYS);
            }

            return sequence;

        } catch (Exception e) {
            log.error("获取Redis序列号失败，键: {}", redisKey, e);
            throw new RuntimeException("获取序列号失败", e);
        }
    }

    /**
     * 获取当前序列号（不递增）
     */
    public Long getCurrentSequence(BizCode bizCode) {
        LocalDate today = LocalDate.now();
        String datePart = today.format(DATE_FORMATTER);
        String redisKey = SEQUENCE_KEY_PREFIX + bizCode.getCode() + ":" + datePart;

        try {
            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
            Object value = valueOps.get(redisKey);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return 0L;
        } catch (Exception e) {
            log.error("获取当前序列号失败，键: {}", redisKey, e);
            return 0L;
        }
    }

    /**
     * 重置序列号（用于测试或手动调整）
     */
    public void resetSequence(BizCode bizCode, Long startValue) {
        LocalDate today = LocalDate.now();
        String datePart = today.format(DATE_FORMATTER);
        String redisKey = SEQUENCE_KEY_PREFIX + bizCode.getCode() + ":" + datePart;

        try {
            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
            valueOps.set(redisKey, startValue - 1, DEFAULT_TTL_DAYS, TimeUnit.DAYS);
            log.info("重置序列号，业务: {}，日期: {}，起始值: {}", bizCode.getCode(), datePart, startValue);
        } catch (Exception e) {
            log.error("重置序列号失败，业务: {}，日期: {}，起始值: {}", bizCode.getCode(), datePart, startValue, e);
            throw new BusinessException("重置序列号失败", e);
        }
    }

    /**
     * 检查序列号是否接近最大值（达到80%时告警）
     */
    public boolean isSequenceNearMax(BizCode bizCode) {
        Long current = getCurrentSequence(bizCode);
        return current != null && current >= MAX_SEQUENCE * 0.8;
    }

    /**
     * 获取序列号统计信息
     */
    public SequenceStats getSequenceStats(BizCode bizCode) {
        Long current = getCurrentSequence(bizCode);
        boolean nearMax = isSequenceNearMax(bizCode);

        return SequenceStats.builder()
                .bizCode(bizCode.getCode())
                .bizDesc(bizCode.getLabel())
                .currentSequence(current)
                .maxSequence(MAX_SEQUENCE)
                .usageRate(current != null ? (double) current / MAX_SEQUENCE : 0.0)
                .nearMax(nearMax)
                .build();
    }

    // ========== 兼容旧方法 ==========

    /**
     * 生成销售订单编号（兼容旧接口）
     * 格式：SO + 日期(yyMMdd) + 序列号(4位)
     */
    public String generateSalesOrderNumber() {
        return generateBizNumber(BizCode.SO);
    }

    /**
     * 生成业务编号（兼容旧接口）
     * 格式：前缀 + 日期(yyMMdd) + 序列号(4位)
     *
     * @param prefix 业务前缀
     * @param desc   业务描述
     * @return 业务编号
     */
    public String generateBusinessNumber(String prefix, String desc) {
        LocalDate today = LocalDate.now();
        String datePart = today.format(DATE_FORMATTER);
        String redisKey = SEQUENCE_KEY_PREFIX + prefix + ":" + datePart;
        Long sequence = getNextSequence(redisKey);
        if (sequence > MAX_SEQUENCE) {
            log.error("序列号已达到最大值: {}，业务: {}，日期: {}", sequence, desc, datePart);
            throw new BusinessException(desc + "序列号已达到最大值，日期: " + datePart);
        }
        String bizNumber = prefix + datePart + String.format("%04d", sequence);
        log.info("生成{}编号: {}，日期: {}，序列号: {}", desc, bizNumber, datePart, sequence);
        return bizNumber;
    }

    public String generateBusinessNumberByType(String bizType, String fallbackPrefix,
                                               String fallbackDateFormat, int fallbackDigits) {
        BusinessNumberRule rule = loadRule(bizType, fallbackPrefix, fallbackDateFormat, fallbackDigits);
        return generateBusinessNumber(rule, LocalDate.now(), bizType);
    }

    BusinessNumberRule loadRule(String bizType, String fallbackPrefix,
                                String fallbackDateFormat, int fallbackDigits) {
        BusinessNumberRule fallback = new BusinessNumberRule(fallbackPrefix, fallbackDateFormat, fallbackDigits);
        String value;
        try {
            value = sysConfigService.getValue("biz_no_rule." + bizType);
        } catch (Exception ex) {
            log.error("读取业务编号规则失败，使用兼容规则: bizType={}", bizType, ex);
            return fallback;
        }
        if (value == null || value.isBlank()) {
            log.warn("业务编号规则未配置，使用兼容规则: bizType={}", bizType);
            return fallback;
        }
        try {
            BusinessNumberRule configured = objectMapper.readValue(value, BusinessNumberRule.class);
            validateRule(configured);
            return configured;
        } catch (Exception ex) {
            log.error("业务编号规则无效，使用兼容规则: bizType={}, value={}", bizType, value, ex);
            return fallback;
        }
    }

    String generateBusinessNumber(BusinessNumberRule rule, LocalDate date, String bizType) {
        validateRule(rule);
        String datePart = date.format(DateTimeFormatter.ofPattern(normalizeDatePattern(rule.dateFormat())));
        // 沿用旧版 prefix 维度的 Redis key，迁移后不会把当日序列从 1 重新开始。
        Long sequence = getNextSequence(SEQUENCE_KEY_PREFIX + rule.prefix() + ":" + datePart);
        if (sequence > maxSequence(rule.digits())) {
            throw new BusinessException(bizType + "序列号已达到最大值，日期: " + datePart);
        }
        return rule.prefix() + datePart + String.format("%0" + rule.digits() + "d", sequence);
    }

    private static void validateRule(BusinessNumberRule rule) {
        if (rule == null || rule.prefix() == null || rule.prefix().isBlank()
                || rule.dateFormat() == null || rule.dateFormat().isBlank()
                || rule.digits() < 1 || rule.digits() > 12) {
            throw new IllegalArgumentException("编号规则必须包含 prefix、dateFormat，digits 范围为 1-12");
        }
        DateTimeFormatter.ofPattern(normalizeDatePattern(rule.dateFormat()));
    }

    private static String normalizeDatePattern(String pattern) {
        return pattern.replace("YYYY", "yyyy").replace("YY", "yy")
                .replace("DD", "dd").replace("D", "d");
    }

    private static long maxSequence(int digits) {
        long max = 0;
        for (int i = 0; i < digits; i++) max = max * 10 + 9;
        return max;
    }

    public record BusinessNumberRule(String prefix, String dateFormat, int digits) {}

    /**
     * 序列号统计信息
     */
    @lombok.Builder
    @lombok.Data
    public static class SequenceStats {
        private String bizCode;
        private String bizDesc;
        private Long currentSequence;
        private Long maxSequence;
        private Double usageRate;
        private Boolean nearMax;
    }
}
