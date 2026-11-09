package com.crossborder.erp.platform.aspect;

import com.crossborder.erp.platform.annotation.ApiMonitor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * API监控切面 - 记录API调用耗时和埋点
 * 集成Micrometer进行指标采集，可导出到Prometheus
 *
 * <p>🔴 <b>这个切面之前是「死的」</b>：它拦截 {@code @ApiMonitor}，
 * 而 {@code @ApiMonitor} <b>全仓没有任何方法用过</b> → 切面永远不触发 →
 * {@code api.calls.total} / {@code api.errors.total} / {@code api.call.duration}
 * <b>三个指标永远停在 0</b>，却看上去「监控已就位」。</p>
 * <p>✅ 现在：5 个平台的订单同步实现类的方法都标了 {@code @ApiMonitor} →
 * 平台 API 的调用次数 / 失败次数 / 耗时<b>真的会被记录</b>。</p>
 * <p>📌 防复发：{@code scripts/check-metrics-truthfulness.py} 的判据 E 会查
 * 「指标的唯一写入点在永不触发的切面里」。</p>
 */
@Slf4j
@Aspect
@Component
public class ApiMonitorAspect {

    private final MeterRegistry meterRegistry;

    public ApiMonitorAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Around("@annotation(com.crossborder.erp.platform.annotation.ApiMonitor)")
    public Object monitorApi(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String signature = className + "." + methodName;
        String platform = resolvePlatform(joinPoint);

        Instant start = Instant.now();

        // 获取或创建Timer
        Timer timer = Timer.builder("api.call.duration")
                .tag("method", signature)
                .description("API调用耗时")
                .register(meterRegistry);

        try {
            Object result = joinPoint.proceed();
            long duration = Duration.between(start, Instant.now()).toMillis();

            // 记录成功指标
            recordSuccessMetrics(signature, platform, duration);

            log.info("API调用成功: {} 耗时: {}ms", signature, duration);
            return result;

        } catch (Exception e) {
            long duration = Duration.between(start, Instant.now()).toMillis();

            // 记录失败指标
            recordFailureMetrics(signature, platform, duration);

            log.error("API调用失败: {} 耗时: {}ms 错误: {}", signature, duration, e.getMessage());
            throw e;
        }
    }

    /**
     * 从 {@code @ApiMonitor} 上读 platform()，作为结构化 tag。
     *
     * <p>为什么必须提出来：之前只有 {@code method="ShopeeOrderSync.syncOrders"} 这种
     * <b>把平台名编在方法名里</b>的写法 → 聚合时只能靠字符串解析 →
     * Prometheus 里做不了 {@code sum by (platform)}。</p>
     */
    private String resolvePlatform(ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature sig = (MethodSignature) joinPoint.getSignature();
            Method method = sig.getMethod();
            ApiMonitor ann = method.getAnnotation(ApiMonitor.class);
            if (ann != null && !ann.platform().isBlank()) {
                return ann.platform();
            }
        } catch (RuntimeException e) {
            // 拿不到注解就退化为类名（拿不到不能影响监控本身）
        }        return joinPoint.getTarget().getClass().getSimpleName();
    }

    /**
     * 记录成功指标
     */
    private void recordSuccessMetrics(String signature, String platform, long duration) {
        // 增加成功计数
        Counter.builder("api.calls.total")
                .tag("platform", platform)
                .tag("method", signature)
                .tag("result", "success")
                .register(meterRegistry)
                .increment();

        // 记录响应时间
        meterRegistry.timer("api.call.duration", "method", signature)
                .record(duration, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录失败指标
     */
    private void recordFailureMetrics(String signature, String platform, long duration) {
        // 增加失败计数
        Counter.builder("api.calls.total")
                .tag("platform", platform)
                .tag("method", signature)
                .tag("result", "failure")
                .register(meterRegistry)
                .increment();

        // 记录错误
        Counter.builder("api.errors.total")
                .tag("platform", platform)
                .tag("method", signature)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录自定义指标（供其他服务调用）
     */
    public void recordCustomMetric(String name, String... tags) {
        Counter.builder(name)
                .tags(tags)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录自定义计时（供其他服务调用）
     */
    public void recordCustomTimer(String name, long duration, TimeUnit unit, String... tags) {
        Timer.builder(name)
                .tags(tags)
                .register(meterRegistry)
                .record(duration, unit);
    }
}