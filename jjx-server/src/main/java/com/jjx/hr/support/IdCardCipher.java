package com.jjx.hr.support;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import com.jjx.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 员工敏感字段（身份证号）加解密与脱敏工具。
 *
 * <p>密钥来源：sys_config {@code hr.idcard.key}，缺省为内置常量。AES/ECB/PKCS5Padding，
 * 密钥长度不足 16/24/32 时按 MD5 派生 32 字节，保证始终可用。</p>
 *
 * <p>2026-09-10 人事模块 P0：仅身份证号落库加密；其余敏感字段（住址）按权限脱敏展示。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdCardCipher {

    /** 配置键 */
    public static final String CONFIG_KEY = "hr.idcard.key";

    private static final String DEFAULT_KEY = "jjx-hr-idcard-key-2026-change-me";

    private static final String MASK = "****";

    private final SysConfigService sysConfigService;

    /** 取密钥原文（配置优先，异常回落默认值） */
    private String rawKey() {
        try {
            String v = sysConfigService.getValue(CONFIG_KEY);
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        } catch (Exception e) {
            log.warn("读取 {} 失败，使用内置默认密钥：{}", CONFIG_KEY, e.getMessage());
        }
        return DEFAULT_KEY;
    }

    private AES aes() {
        byte[] key = rawKey().getBytes(StandardCharsets.UTF_8);
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            key = SecureUtil.md5(rawKey()).getBytes(StandardCharsets.UTF_8);
        }
        return new AES(key);
    }

    /** 加密（明文为空返回 null） */
    public String encrypt(String plain) {
        if (plain == null || plain.trim().isEmpty()) {
            return null;
        }
        return aes().encryptHex(plain.trim());
    }

    /**
     * 解密。值不像密文（历史明文/长度异常）时原样返回，保证兼容与可用。
     */
    public String decrypt(String cipher) {
        if (cipher == null || cipher.trim().isEmpty()) {
            return null;
        }
        String v = cipher.trim();
        if (!v.matches("^[0-9a-fA-F]+$") || v.length() % 2 != 0) {
            return v;
        }
        try {
            return aes().decryptStr(v);
        } catch (Exception e) {
            log.debug("字段不是有效密文，按明文返回：{}", e.getMessage());
            return v;
        }
    }

    /** 身份证号脱敏：保留前 6 后 4，如 440301********1234 */
    public static String maskIdCard(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() <= 10) {
            return MASK;
        }
        return value.substring(0, 6) + MASK + value.substring(value.length() - 4);
    }

    /** 手机号脱敏：138****8888 */
    public static String maskPhone(String value) {
        if (value == null || value.length() < 7) {
            return value;
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    /** 地址脱敏：保留前 6 字符 */
    public static String maskAddress(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() <= 6) {
            return MASK;
        }
        return value.substring(0, 6) + MASK;
    }

    /** 是否已脱敏（含掩码串，前端回传时据此判断“未修改”） */
    public static boolean isMasked(String value) {
        return value != null && value.contains(MASK);
    }
}
