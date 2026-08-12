package com.recruit.smartrecruit.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * HTTP 请求链路追踪过滤器
 *
 * 为每一次 HTTP 请求生成唯一 TraceId，
 * 并通过 MDC 保存，使同一次请求产生的日志可以关联起来。
 *
 * 同时记录请求方法、URI、响应状态码以及请求耗时，
 * 便于日常维护和 Bug 定位。
 */
@Slf4j
@Component
public class RequestTraceFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "traceId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = UUID.randomUUID().toString().replace("-", "");

        MDC.put(TRACE_ID, traceId);

        long startTime = System.currentTimeMillis();

        log.info("请求开始 method={} uri={}",
                request.getMethod(),
                request.getRequestURI());

        try {
            filterChain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - startTime;

            log.info("请求结束 method={} uri={} status={} cost={}ms",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    cost);

            MDC.remove(TRACE_ID);
        }
    }
}


