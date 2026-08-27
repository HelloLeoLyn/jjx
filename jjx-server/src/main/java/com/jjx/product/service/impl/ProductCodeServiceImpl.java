package com.jjx.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.ProductCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 产品编码流水号统一服务实现（2026-08-12）
 * 兼容客户简称 1-3 位：前缀匹配 + 正则提取，不硬编码流水号位置
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCodeServiceImpl implements ProductCodeService {

    private final ProductMapper productMapper;

    /** 匹配第一段 3 位连续数字（流水号段） */
    private static final Pattern SERIAL_PATTERN = Pattern.compile("\\d{3}");

    @Override
    public String nextSerial(String customerShort) {
        String shortName = customerShort == null ? "" : customerShort.trim();
        if (shortName.isEmpty()) {
            return "001";
        }
        // 匹配前缀：取简称前 1-3 位
        String likePrefix = shortName.substring(0, Math.min(3, shortName.length()));

        LambdaQueryWrapper<Product> wrapper = Wrappers.lambdaQuery();
        wrapper.likeRight(Product::getProductCode, likePrefix);
        wrapper.last("LIMIT 500");

        int maxSerial = 0;
        try {
            List<Product> list = productMapper.selectList(wrapper);
            for (Product p : list) {
                if (p.getProductCode() == null) continue;
                Matcher m = SERIAL_PATTERN.matcher(p.getProductCode());
                if (m.find()) {
                    try {
                        maxSerial = Math.max(maxSerial, Integer.parseInt(m.group()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询产品流水号失败: {}", e.getMessage());
        }

        int next = maxSerial + 1;
        if (next > 999) {
            next = 1;
        }
        return String.format("%03d", next);
    }
}
