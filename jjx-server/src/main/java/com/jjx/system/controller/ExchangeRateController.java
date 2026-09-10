package com.jjx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.jjx.common.core.result.Result;
import com.jjx.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 汇率查询控制器（CNY 本币，只认外部实时汇率）。
 *
 * <p>2026-09-10 按 Leo 要求改造：</p>
 * <ul>
 *   <li><b>取消一切兜底</b>：不再使用内置写死汇率，也不再读 sys_config 的 exchange_rate.* 配置。
 *       外部汇率源不可用时<b>明确报错</b>，绝不用旧值/1 冒充实时值参与报价金额计算。</li>
 *   <li>加连接/读取超时（3s），避免外部服务卡住拖死请求线程。</li>
 *   <li>加 Redis 缓存（10 分钟），同一时段复用同一次实时结果，避免每次切换币种都打外部。</li>
 * </ul>
 *
 * <p>调用方：销售订单表单、报价单表单（选外币时自动取汇率）。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/exchange-rate")
@Tag(name = "汇率查询")
public class ExchangeRateController {

    /** 外部实时汇率源（免费、无需 API Key）：返回 1 CNY = N 外币 */
    private static final String EXCHANGE_RATE_API = "https://open.er-api.com/v6/latest/CNY";

    private static final String CACHE_KEY = "exchange-rate:latest:CNY";

    /** 缓存时长（分钟）：汇率日内变动很小，10 分钟内复用同一次实时结果 */
    private static final long CACHE_MINUTES = 10;

    /** 外部调用超时（毫秒） */
    private static final int TIMEOUT_MS = 3000;

    /** 汇率精度 */
    private static final int SCALE = 4;

    private final RedisTemplate<String, Object> redisTemplate;

    @Operation(summary = "获取所有币种实时汇率（CNY 本币）")
    @SaCheckPermission(value = {"sales:order:view", "sales:quotation:view", "system:user:view"}, mode = SaMode.OR)
    @GetMapping("/latest")
    public Result<Map<String, Object>> getLatestRates() {
        return Result.success(loadSnapshot());
    }

    @Operation(summary = "获取指定币种实时汇率（相对 CNY）")
    @SaCheckPermission(value = {"sales:order:view", "sales:quotation:view", "system:user:view"}, mode = SaMode.OR)
    @GetMapping("/rate")
    public Result<BigDecimal> getRate(@RequestParam String currency) {
        String code = currency == null ? "" : currency.trim().toUpperCase();
        if (code.isEmpty()) {
            throw new BusinessException("币种不能为空");
        }
        Object ratesObj = loadSnapshot().get("rates");
        if (!(ratesObj instanceof Map)) {
            throw new BusinessException("汇率数据异常，请稍后重试或手工填写汇率");
        }
        Object value = ((Map<?, ?>) ratesObj).get(code);
        if (value == null) {
            throw new BusinessException("未取到币种 " + code + " 的实时汇率，请手工填写汇率");
        }
        BigDecimal rate = new BigDecimal(value.toString());
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("币种 " + code + " 的实时汇率异常，请手工填写汇率");
        }
        return Result.success(rate.setScale(SCALE, RoundingMode.HALF_UP));
    }

    // ==================== 内部 ====================

    /**
     * 读取汇率快照：先查 10 分钟缓存，未命中则请求外部实时源。
     * 外部不可用时抛业务异常（**明确失败，不做任何兜底**）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadSnapshot() {
        try {
            Object cached = redisTemplate.opsForValue().get(CACHE_KEY);
            if (cached instanceof Map) {
                return (Map<String, Object>) cached;
            }
        } catch (Exception e) {
            log.warn("读取汇率缓存失败，直接取实时值：{}", e.getMessage());
        }

        Map<String, Object> snapshot = fetchFromRemote();
        try {
            redisTemplate.opsForValue().set(CACHE_KEY, snapshot, CACHE_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入汇率缓存失败（不影响本次返回）：{}", e.getMessage());
        }
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchFromRemote() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);

        Map<String, Object> apiResponse;
        try {
            apiResponse = new RestTemplate(factory).getForObject(EXCHANGE_RATE_API, Map.class);
        } catch (Exception e) {
            log.error("汇率服务调用失败：{}", e.getMessage());
            throw new BusinessException("汇率服务暂不可用（" + e.getMessage() + "），请稍后重试或手工填写汇率");
        }
        if (apiResponse == null || !(apiResponse.get("rates") instanceof Map)) {
            throw new BusinessException("汇率服务返回异常，请稍后重试或手工填写汇率");
        }

        Map<String, BigDecimal> rates = new HashMap<>();
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) apiResponse.get("rates")).entrySet()) {
            if (entry.getValue() instanceof Number) {
                BigDecimal apiRate = BigDecimal.valueOf(((Number) entry.getValue()).doubleValue());
                // 外部返回 1 CNY = N 外币，转为 1 外币 = N CNY
                if (apiRate.compareTo(BigDecimal.ZERO) > 0) {
                    rates.put(entry.getKey(), BigDecimal.ONE.divide(apiRate, SCALE, RoundingMode.HALF_UP));
                }
            }
        }
        if (rates.isEmpty()) {
            throw new BusinessException("汇率服务返回空数据，请稍后重试或手工填写汇率");
        }

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("base", "CNY");
        snapshot.put("source", "live");
        snapshot.put("fetchedAt", LocalDateTime.now().toString());
        snapshot.put("cacheMinutes", CACHE_MINUTES);
        snapshot.put("rates", rates);
        log.debug("实时汇率获取成功，币种数 {}", rates.size());
        return snapshot;
    }
}
