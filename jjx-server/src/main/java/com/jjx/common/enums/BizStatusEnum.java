package com.jjx.common.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 业务状态枚举统一契约：key / value / label。
 *
 * <p>三者分工固定，不要再各模块自己起名（历史上有 getCode/getValue、getName/getLabel/getDescription 三套写法）：
 * <ul>
 *   <li>{@link #getValue()} —— 落库的数字码，如 0；同时满足 MyBatis-Plus {@code IEnum}，
 *       实体字段可以直接声明成枚举类型由 MP 自动转换。</li>
 *   <li>{@link #getLabel()} —— 展示文案，如 "草稿"。前端直接显示的就是它。</li>
 *   <li>{@link #getKey()} —— 英文标识，如 "DRAFT"。默认取枚举常量名，不额外存字段，
 *       避免常量名与 key 两份真相互相漂移。</li>
 * </ul>
 *
 * <p>@Log 注解取值统一写 {@code T(枚举).常量.getValue()} 或 {@code .getLabel()}，
 * 写错方法名编译不过——这是字符串表达式唯一能拿到的编译期保障。
 */
public interface BizStatusEnum extends IEnum<Integer> {

    /** 落库数字码 */
    @Override
    Integer getValue();

    /** 展示文案 */
    String getLabel();

    /** 英文标识，默认为枚举常量名 */
    default String getKey() {
        return ((Enum<?>) this).name();
    }
}
