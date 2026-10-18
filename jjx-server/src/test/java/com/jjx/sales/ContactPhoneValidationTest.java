package com.jjx.sales;

import com.jjx.common.validation.ContactPhoneRule;
import com.jjx.sales.domain.dto.CustomerAddDTO;
import com.jjx.sales.domain.dto.CustomerEditDTO;
import com.jjx.sales.domain.dto.SalesOrderAddDTO;
import com.jjx.sales.domain.dto.SalesOrderEditDTO;
import com.jjx.sales.domain.dto.SalesQuotationAddDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * dev-20261018-026：客户、报价、销售订单联系电话统一口径（可选、≤50 字符、字符白名单）。
 *
 * <p>直接对 DTO 的 contactPhone 属性做校验，不启动 Spring 上下文；
 * 覆盖卡面验收样例（留空 / 国际号 / 带空格 / 分机 / 50 字通过；51 字、中文、控制符拒绝）。
 */
class ContactPhoneValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private String phoneViolation(Class<?> dtoClass, String value) {
        try {
            Object dto = dtoClass.getDeclaredConstructor().newInstance();
            dtoClass.getMethod("setContactPhone", String.class).invoke(dto, value);
            Set<ConstraintViolation<Object>> violations = validator.validateProperty(dto, "contactPhone");
            return violations.isEmpty() ? null : violations.iterator().next().getMessage();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("DTO 反射失败：" + dtoClass.getName(), e);
        }
    }

    private static String repeat(char c, int times) {
        return String.valueOf(c).repeat(times);
    }

    @Test
    @DisplayName("卡面验收样例在四个 DTO 上全部通过（客户编辑继承客户新增）")
    void acceptedSamples() {
        String[] accepted = {
                "",
                "+86 13800000000",
                "+852 9123 4567",
                "0755-12345678",
                "12345678-123",
                "(021) 5555 1234 #808",
                "13800138000",
                repeat('1', 50)
        };
        Class<?>[] dtoClasses = {
                CustomerAddDTO.class,
                CustomerEditDTO.class,
                SalesQuotationAddDTO.class,
                SalesOrderAddDTO.class,
                SalesOrderEditDTO.class
        };
        for (Class<?> dtoClass : dtoClasses) {
            for (String sample : accepted) {
                String message = phoneViolation(dtoClass, sample);
                assertTrue(message == null,
                        dtoClass.getSimpleName() + " 应接受样例 [" + sample + "]，实际提示：" + message);
            }
            assertEquals(null, phoneViolation(dtoClass, null),
                    dtoClass.getSimpleName() + " 应允许联系人为空（null）");
        }
    }

    @Test
    @DisplayName("51 字符超过 varchar(50)：五个入口都提示长度超限")
    void rejectsTooLong() {
        String tooLong = repeat('1', ContactPhoneRule.MAX_LENGTH + 1);
        for (Class<?> dtoClass : new Class<?>[]{CustomerAddDTO.class, CustomerEditDTO.class,
                SalesQuotationAddDTO.class, SalesOrderAddDTO.class, SalesOrderEditDTO.class}) {
            assertEquals(ContactPhoneRule.LENGTH_MESSAGE, phoneViolation(dtoClass, tooLong),
                    dtoClass.getSimpleName() + " 应对 51 字符提示超长");
        }
    }

    @Test
    @DisplayName("中文、字母、控制符不在白名单：五个入口都提示字符不合法")
    void rejectsIllegalChars() {
        String[] rejected = {"13800000000转123", "phone", "138\u0001000000", "13800000000；"};
        for (Class<?> dtoClass : new Class<?>[]{CustomerAddDTO.class, CustomerEditDTO.class,
                SalesQuotationAddDTO.class, SalesOrderAddDTO.class, SalesOrderEditDTO.class}) {
            for (String sample : rejected) {
                assertEquals(ContactPhoneRule.CHARSET_MESSAGE, phoneViolation(dtoClass, sample),
                        dtoClass.getSimpleName() + " 应拒绝 [" + sample + "]");
            }
        }
    }

    @Test
    @DisplayName("规则常量本身：长度边界与白名单与前端 contactPhone.ts 同串")
    void ruleConstants() {
        assertEquals(50, ContactPhoneRule.MAX_LENGTH);
        assertEquals("^[0-9+()# -]*$", ContactPhoneRule.REGEX);
        assertTrue(ContactPhoneRule.isValid("+86 13800000000"));
        assertTrue(!ContactPhoneRule.isValid(repeat('0', 51)));
        assertTrue(!ContactPhoneRule.isValid("手机"));
    }
}
