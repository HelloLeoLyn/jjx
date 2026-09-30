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

    @Override
    public String nextSerial(String customerShort) {
        String shortName = customerShort == null ? "" : customerShort.trim();
        if (shortName.isEmpty()) {
            return "001";
        }
        // 匹配前缀：取简称前 1-3 位
        String likePrefix = shortName.substring(0, Math.min(3, shortName.length()));

        LambdaQueryWrapper<Product> wrapper = Wrappers.lambdaQuery();
        wrapper.select(Product::getProductCode);
        wrapper.likeRight(Product::getProductCode, likePrefix);
        // dev-20260929-028：① 去掉 LIMIT 500 —— 同客户产品超过 500 个时最大号会被漏看 → 取到重号；
        //   ② 只认「前缀 + 紧邻 3 位数字」这个流水号段，避免把别人家的号算进来。
        Pattern serialPattern = Pattern.compile("^" + Pattern.quote(likePrefix) + "(\\d{3})");

        int maxSerial = 0;
        try {
            List<Product> list = productMapper.selectList(wrapper);
            for (Product p : list) {
                if (p.getProductCode() == null) continue;
                Matcher m = serialPattern.matcher(p.getProductCode());
                if (m.find()) {
                    try {
                        maxSerial = Math.max(maxSerial, Integer.parseInt(m.group(1)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询产品流水号失败: {}", e.getMessage());
        }

        int next = maxSerial + 1;
        if (next > 999) {
            // dev-20260929-028：不再回绕到 001（回绕必然与已用号撞），改由界面手动指定序号
            throw new com.jjx.common.exception.BusinessException(
                    "客户「" + shortName + "」的序号已用满 999，请在「序号」里手动指定一个未占用的号");
        }
        return String.format("%03d", next);
    }
}
