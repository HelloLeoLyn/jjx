package com.jjx.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** 请求内各条操作日志共享的业务动作关联号；与单据 traceId 分开。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String MDC_KEY = "operGroupId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String groupId = request.getHeader("X-Request-Id");
        // 列宽 64；无效的超长输入重新生成，避免影响业务日志落库。
        if (groupId == null || groupId.isBlank() || groupId.length() > 64) {
            groupId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, groupId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
