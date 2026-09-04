package com.jjx.system.aspect;

import com.jjx.system.annotation.Log;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 启动期校验所有 @Log 的 bizStatus：注解本身没有必填语法，只能靠启动扫描做 fail-fast。
 *
 * <p>2026-09-04 修复：原实现 AopUtils.getTargetClass(type) 传入的是 Class 对象而非 bean 实例，
 * 永远返回 Class.class 自身，getDeclaredMethods 扫不到任何 @Log（checked 恒为 0），校验器从未生效。
 * 改为 ClassUtils.getUserClass(type) 剥掉 CGLIB 代理后缀后扫描真实类。
 *
 * <p>2026-09-04 止血：存量 174 处 @Log 缺 bizStatus 尚未治理，此处暂以 log.error 告警而非抛异常
 * （否则存量未清前应用无法启动）。存量清零后恢复 fail-fast（throw IllegalStateException）。
 *
 * <p>三条规则（存量治理后生效）：
 * <ol>
 *   <li>写了 bizType 就必须写 bizStatus —— 有业务对象却不记状态没有意义；</li>
 *   <li>bizStatus 不能是裸数字（"3"）—— 各模块同一个数字含义不同，必须走枚举；</li>
 *   <li>bizStatus 不能是三元表达式 —— 状态机映射不许硬编码进注解，应由业务方法返回真实状态。</li>
 * </ol>
 */
@Slf4j
@Component
public class LogBizStatusValidator implements SmartInitializingSingleton {

    private static final Pattern NUMERIC_LITERAL = Pattern.compile("^-?\\d+$");

    private final ApplicationContext applicationContext;

    public LogBizStatusValidator(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> violations = new ArrayList<>();
        int checked = 0;

        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> type;
            try {
                type = applicationContext.getType(beanName);
            } catch (Exception e) {
                continue;
            }
            if (type == null || !type.getName().startsWith("com.jjx")) {
                continue;
            }
            Class<?> target = ClassUtils.getUserClass(type);
            // getUserClass 剥 CGLIB 代理后缀（$$EnhancerBySpringCGLIB$$...）后拿到真实类，
            // 才能扫到方法上的 @Log（原 AopUtils.getTargetClass(Class) 用法有误，恒扫不到）。
            for (Method method : target.getDeclaredMethods()) {
                Log logAnnotation = AnnotationUtils.findAnnotation(method, Log.class);
                if (logAnnotation == null) {
                    continue;
                }
                checked++;
                String where = target.getSimpleName() + "#" + method.getName();
                String bizType = logAnnotation.bizType().trim();
                String bizStatus = logAnnotation.bizStatus().trim();

                if (!bizType.isEmpty() && bizStatus.isEmpty()) {
                    violations.add(where + " 有 bizType 但没写 bizStatus");
                    continue;
                }
                if (bizStatus.isEmpty()) {
                    continue;
                }
                String unquoted = stripQuotes(bizStatus);
                if (NUMERIC_LITERAL.matcher(unquoted).matches()) {
                    violations.add(where + " bizStatus 是裸数字 \"" + bizStatus + "\"，改成 T(状态枚举).常量.getLabel()");
                }
                if (bizStatus.contains("?") && bizStatus.contains(":")) {
                    violations.add(where + " bizStatus 用三元表达式硬编码状态机 \"" + bizStatus
                            + "\"，改成由业务方法返回真实状态后取 #result.data.label");
                }
            }
        }

        if (!violations.isEmpty()) {
            // 2026-09-04 止血：存量违规未清零前不阻断启动，改 error 告警；治理完成后恢复 throw。
            StringBuilder sb = new StringBuilder("@Log bizStatus 校验未通过（共 ")
                    .append(violations.size()).append(" 处，存量治理完成前仅告警不阻断）：");
            for (String v : violations) {
                sb.append("\n  - ").append(v);
            }
            log.error(sb.toString());
            return;
        }
        log.info("@Log bizStatus 校验通过，共检查 {} 个方法", checked);
    }

    private static String stripQuotes(String raw) {
        String s = raw;
        if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
            s = s.substring(1, s.length() - 1);
        }
        return s.trim();
    }
}
