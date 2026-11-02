package com.crossborder.pricing.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller 路由冲突检测
 *
 * <p><b>为什么需要这个测试</b>：Spring 在启动期发现两个 Controller 映射到
 * 同一个 {@code HTTP方法 + 路径} 时会抛
 * {@code Ambiguous mapping. Cannot map 'xxxController' method ...}，
 * <b>整个服务直接起不来</b>。</p>
 *
 * <p>但纯 Mockito 单测（不启动 Spring 上下文）<b>完全测不出来</b> ——
 * 本仓库就真实踩过：{@code PricingController#health()} 和
 * {@code HealthController#health()} 都声明了
 * {@code GET /api/v1/pricing/health}，单元测试全绿，
 * 真起服务才发现 pricing 根本启动不了。</p>
 *
 * <p>所以这里用反射把模块里所有 Controller 的映射扫一遍，
 * 静态检测冲突 —— 不需要 Spring 上下文，跑得极快。</p>
 */
@DisplayName("Controller 路由冲突检测")
class ControllerMappingConflictTest {

    /** 模块内所有 Controller 的包 */
    private static final String CONTROLLER_PACKAGE = "com.crossborder.pricing.controller";

    /**
     * 收集模块内所有 Controller 声明的「HTTP方法 + 完整路径」。
     *
     * <p>路径 = 类级 {@code @RequestMapping} 前缀 + 方法级 {@code @GetMapping} 等的路径。
     * 注意 {@code @RequestMapping} 的 value 可以是数组（多个路径），
     * 这里按逗号拆开逐个处理。</p>
     */
    private static Map<String, String> collectMappings() {
        Map<String, String> mappingToOwner = new HashMap<>();

        for (Class<?> clazz : listControllerClasses()) {
            String prefix = classLevelPrefix(clazz);

            for (Method method : clazz.getDeclaredMethods()) {
                for (String path : methodLevelPaths(method)) {
                    String httpMethod = httpMethodOf(method);
                    if (httpMethod == null) {
                        continue;
                    }
                    String fullPath = normalize(prefix + path);
                    String key = httpMethod + " " + fullPath;

                    String owner = clazz.getSimpleName() + "#" + method.getName();
                    String existing = mappingToOwner.put(key, owner);
                    assertTrue(existing == null,
                            "路由冲突：" + key + " 被 [" + existing + "] 和 [" + owner + "] 同时声明"
                                    + " —— Spring 启动期会抛 Ambiguous mapping，整个服务起不来");
                }
            }
        }
        return mappingToOwner;
    }

    @Test
    @DisplayName("所有 Controller 的路由不冲突")
    void noDuplicateMappings() {
        Map<String, String> mappings = collectMappings();
        assertTrue(!mappings.isEmpty(), "没有扫到任何 Controller 映射 —— 扫描逻辑本身坏了");
    }

    @Test
    @DisplayName("健康检查路径只有一处声明")
    void healthEndpointDeclaredOnce() {
        Map<String, String> mappings = collectMappings();

        List<String> healthMappings = mappings.keySet().stream()
                .filter(k -> k.endsWith("/health"))
                .toList();

        assertTrue(healthMappings.size() <= 1,
                "/health 被声明了 " + healthMappings.size() + " 次：" + healthMappings);
    }

    // ------------------------------------------------------------------ 辅助

    private static List<Class<?>> listControllerClasses() {
        List<Class<?>> classes = new ArrayList<>();
        // 显式列出模块内的 Controller，避免依赖 classpath 扫描的稳定性
        classes.add(PricingController.class);
        classes.add(HealthController.class);
        return classes;
    }

    private static String classLevelPrefix(Class<?> clazz) {
        RequestMapping rm = clazz.getAnnotation(RequestMapping.class);
        if (rm == null || rm.value().length == 0) {
            return "";
        }
        return rm.value()[0];
    }

    private static List<String> methodLevelPaths(Method method) {
        List<String> paths = new ArrayList<>();

        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null) {
            paths.addAll(List.of(get.value()));
            return paths;
        }
        PostMapping post = method.getAnnotation(PostMapping.class);
        if (post != null) {
            paths.addAll(List.of(post.value()));
            return paths;
        }
        PutMapping put = method.getAnnotation(PutMapping.class);
        if (put != null) {
            paths.addAll(List.of(put.value()));
            return paths;
        }
        DeleteMapping del = method.getAnnotation(DeleteMapping.class);
        if (del != null) {
            paths.addAll(List.of(del.value()));
            return paths;
        }
        RequestMapping rm = method.getAnnotation(RequestMapping.class);
        if (rm != null) {
            paths.addAll(List.of(rm.value()));
        }
        return paths;
    }

    private static String httpMethodOf(Method method) {
        if (method.isAnnotationPresent(GetMapping.class)) return "GET";
        if (method.isAnnotationPresent(PostMapping.class)) return "POST";
        if (method.isAnnotationPresent(PutMapping.class)) return "PUT";
        if (method.isAnnotationPresent(DeleteMapping.class)) return "DELETE";
        if (method.isAnnotationPresent(RequestMapping.class)) return "ANY";
        return null;
    }

    /** 去掉首尾斜杠并补上开头的斜杠，保证同一路径只有一种写法 */
    private static String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        String p = path.startsWith("/") ? path : "/" + path;
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
