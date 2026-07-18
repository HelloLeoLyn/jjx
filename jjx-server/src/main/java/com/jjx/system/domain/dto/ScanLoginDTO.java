package com.jjx.system.domain.dto;

import lombok.Data;

/**
 * 扫码登录请求
 */
@Data
public class ScanLoginDTO {

    /**
     * 二维码唯一标识
     */
    private String qrCodeKey;

    /**
     * 扫码后获取的临时token
     */
    private String scanToken;
}
