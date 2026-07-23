package com.jjx.system.controller;

import com.jjx.common.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 汇率查询控制器
 * 提供实时汇率查询功能（基于CNY本币）
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/exchange-rate")
@Tag(name = "汇率管理")
public class ExchangeRateController {

    // 汇率API（免费，无需API Key）
    private static final String EXCHANGE_RATE_API = "https://open.er-api.com/v6/latest/CNY";

    // 备用汇率（当API不可用时使用）
    private static final Map<String, BigDecimal> FALLBACK_RATES = new HashMap<>();

    static {
        FALLBACK_RATES.put("CNY", BigDecimal.ONE);
        FALLBACK_RATES.put("USD", new BigDecimal("7.2400"));
        FALLBACK_RATES.put("EUR", new BigDecimal("7.8800"));
        FALLBACK_RATES.put("GBP", new BigDecimal("9.3500"));
        FALLBACK_RATES.put("JPY", new BigDecimal("0.0480"));
        FALLBACK_RATES.put("HKD", new BigDecimal("0.9270"));
        FALLBACK_RATES.put("KRW", new BigDecimal("0.0053"));
        FALLBACK_RATES.put("AUD", new BigDecimal("4.7500"));
        FALLBACK_RATES.put("CAD", new BigDecimal("5.2700"));
        FALLBACK_RATES.put("SGD", new BigDecimal("5.3800"));
        FALLBACK_RATES.put("TWD", new BigDecimal("0.2230"));
        FALLBACK_RATES.put("CHF", new BigDecimal("8.1400"));
    }

    /**
     * API返回的是 1 CNY = N 外币，转换为 1 外币 = N CNY
     */
    private BigDecimal invertRate(BigDecimal rate) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return BigDecimal.ONE.divide(rate, 4, java.math.RoundingMode.HALF_UP);
    }

    @Operation(summary = "获取所有币种汇率（CNY本币）")
    @GetMapping("/latest")
    public Result<Map<String, Object>> getLatestRates() {
        Map<String, Object> result = new HashMap<>();
        Map<String, BigDecimal> rates;

        try {
            RestTemplate restTemplate = new RestTemplate();
            @SuppressWarnings("unchecked")
            Map<String, Object> apiResponse = restTemplate.getForObject(EXCHANGE_RATE_API, Map.class);

            if (apiResponse != null && apiResponse.get("rates") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rawRates = (Map<String, Object>) apiResponse.get("rates");
                rates = new HashMap<>();
                for (Map.Entry<String, Object> entry : rawRates.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        // API返回 1 CNY = N 外币，取倒数转为 1 外币 = N CNY
                        BigDecimal apiRate = BigDecimal.valueOf(((Number) entry.getValue()).doubleValue());
                        rates.put(entry.getKey(), invertRate(apiRate));
                    }
                }
                result.put("source", "live");
                log.debug("实时汇率获取成功");
            } else {
                rates = FALLBACK_RATES;
                result.put("source", "fallback");
                log.warn("汇率API响应异常，使用备用汇率");
            }
        } catch (Exception e) {
            rates = FALLBACK_RATES;
            result.put("source", "fallback");
            log.warn("汇率API调用失败，使用备用汇率: {}", e.getMessage());
        }

        result.put("base", "CNY");
        result.put("rates", rates);
        return Result.success(result);
    }

    @Operation(summary = "获取指定币种汇率（相对CNY）")
    @GetMapping("/rate")
    public Result<BigDecimal> getRate(@RequestParam String currency) {
        Map<String, Object> latest = getLatestRates().getData();
        if (latest == null) {
            return Result.success(BigDecimal.ONE);
        }

        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> rates = (Map<String, BigDecimal>) latest.get("rates");
        BigDecimal rate = rates.getOrDefault(currency.toUpperCase(), BigDecimal.ONE);
        return Result.success(rate);
    }
}
