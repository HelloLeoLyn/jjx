package com.jjx.product.service;

/**
 * 产品编码流水号统一服务（2026-08-12）
 * 统一"客户简称 + 流水号"编码的流水号生成逻辑，兼容 1-3 位简称
 * 原分散实现：InquiryServiceImpl.nextProductSerial / ProductServiceImpl.generateSerialNo
 */
public interface ProductCodeService {

    /**
     * 按客户简称取下一个流水号（3位数字）
     * 规则：按简称前缀(1-3位)模糊匹配已有产品编码，正则提取第一段3位连续数字取最大+1，超999回1
     *
     * @param customerShort 客户简称（1-3位，可空）
     * @return 3位流水号，如 "001"、"042"
     */
    String nextSerial(String customerShort);
}
