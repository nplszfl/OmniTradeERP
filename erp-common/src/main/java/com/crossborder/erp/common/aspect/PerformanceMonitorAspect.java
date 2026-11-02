package com.crossborder.erp.common.aspect;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 性能监控切面
 * 记录方法执行时间
 *
 * <p>⚠️ 之前这个类的包名是 {@code com.crossborder.erp.order.config}，但文件却放在
 * {@code com/crossborder/erp/common/aspect/} 目录下 —— 包名与目录不一致。
 * 编译能过（javac 不校验目录），但它只会被「恰好扫描到 com.crossborder.erp.order.config」
 * 的 order 服务捡到，其它服务永远看不到，而且放在 erp-common 里却声明成 order 的包，
 * 语义完全错位。已修正为 {@code com.crossborder.erp.common.aspect}，
 * 并通过 {@code CommonMetricsAutoConfiguration} 自动装配到所有带 Actuator 的服务。</p>
 *
 * <p>另外：切面生效需要 classpath 上有 aspectjweaver（由 spring-boot-starter-aop 提供），
 * 否则 {@code @Aspect} 只是一个普通 bean，Around 通知不会织入。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PerformanceMonitorAspect {

    private final MeterRegistry meterRegistry;

    /**
     * 拦截 {@code @Timed} 注解的方法，记录执行耗时。
     *
     * <p>指标名优先使用 {@code @Timed(value=...)} 声明的名字（与 Micrometer 自身语义一致），
     * 未声明时退化为 {@code 类名.方法名}。</p>
     */
    @Around("@annotation(io.micrometer.core.annotation.Timed)")
    public Object monitorPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String metricName = resolveMetricName(joinPoint, className, methodName);

        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            recordExecutionTime(metricName, duration);

            if (duration > 1000) {
                log.warn("方法执行时间过长: {} - {}ms", metricName, duration);
            } else {
                log.debug("方法执行时间: {} - {}ms", metricName, duration);
            }

            return result;
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - startTime;
            recordExecutionTime(metricName + ".error", duration);

            log.error("方法执行出错: {} - {}ms", metricName, duration, e);
            throw e;
        }
    }

    /**
     * 优先取 {@code @Timed} 上声明的指标名
     */
    private String resolveMetricName(ProceedingJoinPoint joinPoint, String className, String methodName) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getDeclaringType()
                    .getMethod(signature.getName(), signature.getParameterTypes());
            Timed timed = method.getAnnotation(Timed.class);
            if (timed != null && !timed.value().isBlank()) {
                return timed.value();
            }
        } catch (NoSuchMethodException | ClassCastException e) {
            // 拿不到就退化为类名.方法名
        }
        return className + "." + methodName;
    }

    /**
     * 记录执行时间到Micrometer
     */
    private void recordExecutionTime(String metricName, long durationMs) {
        Timer.builder(metricName)
                .description("Method execution time")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }
}
