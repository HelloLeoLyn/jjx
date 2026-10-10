package com.jjx.product.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 规范分色检查表保存入参：仅接收检查项白名单字段，不触发任何流程。
 * <p>items 以 {@link com.jjx.product.enums.ColorCheckItemEnum} 稳定键为索引；
 * 结论取值见 {@link com.jjx.product.enums.ColorCheckResultEnum}，空串表示未检查。</p>
 */
@Data
public class ColorCheckDTO {

    @NotNull
    @Size(max = 64)
    private String revision;

    @NotNull
    @Size(max = 120)
    private Map<String, @NotNull @Valid Item> items = new LinkedHashMap<>();

    @Data
    public static class Item {
        /** CORRECT / INCORRECT / NA，空串或 null 表示未检查。 */
        @Size(max = 20)
        private String result;
        /** 选择“错误”时必填。 */
        @Size(max = 1000)
        private String reason;
    }
}
