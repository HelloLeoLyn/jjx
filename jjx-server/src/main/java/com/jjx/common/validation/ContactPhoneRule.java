package com.jjx.common.validation;

import java.util.regex.Pattern;

/**
 * 联系电话统一口径（dev-20261018-026，覆盖 dev-20261014-012 的格式校验部分）。
 *
 * <p>规则：可选、最长 {@link #MAX_LENGTH} 个字符、字符白名单（数字 0-9、+、-、空格、(、)、#），
 * 不做大陆手机号/座机格式判断，以便接受国际号码、分机号及其他业务写法。
 * 客户、报价单、销售订单的 DTO 一律引用 {@link ValidContactPhone}，禁止各写一份正则。
 *
 * <p>前端同源规则：{@code jjx-web/src/utils/contactPhone.ts}（正则字符串必须与本类一致）。
 */
public final class ContactPhoneRule {

    /** 与 sales_customer.contact_phone / sales_quotation.contact_phone / sales_order.contact_phone 的 varchar(50) 对齐 */
    public static final int MAX_LENGTH = 50;

    /** 只允许数字、+、-、空格、括号、#；允许空串（- 置于字符类末尾表示字面量，前后端同一字符串） */
    public static final String REGEX = "^[0-9+()# -]*$";

    public static final Pattern PATTERN = Pattern.compile(REGEX);

    public static final String LENGTH_MESSAGE = "联系电话长度不能超过50个字符";

    public static final String CHARSET_MESSAGE = "联系电话只能包含数字、+、-、空格、括号和#";

    private ContactPhoneRule() {
    }

    /** 空值合法（可选字段）；仅校验长度与字符白名单。 */
    public static boolean isValid(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        return value.length() <= MAX_LENGTH && PATTERN.matcher(value).matches();
    }
}
