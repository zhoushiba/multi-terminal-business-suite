package com.painuo.suite.common;

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

/**
 * traceId 过滤器：请求进入即生成链路 ID，写入 MDC 与响应头。
 *
 * <p>是否复用上游传入的 {@code X-Trace-Id}：仅当网关/前端已传入时复用，
 * 便于跨端（后台 / 小程序 / Nginx）串起同一次请求。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(TraceIdFilterSupport.HEADER);
        String traceId = (incoming == null || incoming.isBlank() || incoming.length() > 64)
                ? TraceIdFilterSupport.generate()
                : incoming;
        MDC.put(TraceIdFilterSupport.MDC_KEY, traceId);
        response.setHeader(TraceIdFilterSupport.HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceIdFilterSupport.MDC_KEY);
        }
    }
}
