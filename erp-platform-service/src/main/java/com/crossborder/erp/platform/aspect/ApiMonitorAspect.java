package com.crossborder.erp.platform.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * API监控切面 - 记录API调用耗时和埋点
 */
@Slf4j
@Aspect
@Component
public class ApiMonitorAspect {

    @Around("@annotation(com.crossborder.erp.platform.annotation.ApiMonitor)")
    public Object monitorApi(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String signature = className + "." + methodName;

        Instant start = Instant.now();
        try {
            Object result = joinPoint.proceed();
            long duration = Duration.between(start, Instant.now()).toMillis();

            log.info("API调用成功: {} 耗时: {}ms", signature, duration);
            // 这里可以集成Prometheus、Micrometer等监控框架进行指标采集
            recordMetrics(signature, duration, true);

            return result;
        } catch (Exception e) {
            long duration = Duration.between(start, Instant.now()).toMillis();
            log.error("API调用失败: {} 耗时: {}ms 错误: {}", signature, duration, e.getMessage());
            recordMetrics(signature, duration, false);
            throw e;
        }
    }

    private void recordMetrics(String signature, long duration, boolean success) {
        // TODO: 集成Micrometer/Prometheus进行指标采集
        // 示例代码：
        // Counter.builder("api_calls_total")
        //     .tag("signature", signature)
        //     .tag("success", String.valueOf(success))
        //     .register(meterRegistry)
        //     .increment();
    }
}
