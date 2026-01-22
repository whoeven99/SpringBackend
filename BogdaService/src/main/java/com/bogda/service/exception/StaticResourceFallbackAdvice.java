package com.bogda.service.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Order(1) // 设置更高优先级，优先处理 NoResourceFoundException
public class StaticResourceFallbackAdvice {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaticResourceFallbackAdvice.class);

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request){
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String addr = request.getRemoteAddr();
        String ua = request.getHeader("User-Agent");
        String details = (query != null) ? path + "?" + query : path;
        LOGGER.info("Static resource not found fallback: {} from {} userAgent={}", details, addr, ua);
        // 返回 204 表示无内容，避免产��异常堆栈日志。
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}

