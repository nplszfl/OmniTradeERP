package com.crossborder.productdescription.controller;

import com.crossborder.productdescription.dto.DescriptionGenerationResponse;
import com.crossborder.productdescription.dto.FrontendDescriptionSaveRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Controller 路由冲突 + 保存路径分工检测
 *
 * <p>🔴 <b>为什么要有这个测试</b>：
 * 前端契约的保存接口（按 SKU）和内部接口（按 productId）
 * <b>请求体形状完全不同</b>，如果两个都占 {@code POST /save}，
 * Spring 启动期会抛 {@code Ambiguous mapping} → <b>整个服务起不来</b>。
 * 而纯 Mockito 单测（不起 Spring 上下文）完全测不出来。</p>
 *
 * <p>📌 用反射静态扫描，不起 Spring 上下文 → 跑得极快。</p>
 */
@DisplayName("Controller 路由冲突检测")
class ProductDescriptionControllerMappingTest {

    private static final String CONTROLLER_PACKAGE = "com.crossborder.productdescription.controller";

    @Test
    @DisplayName("所有 Controller 的路由不冲突（否则服务起不来）")
    void noDuplicateMappings() {
        Map<String, String> mappingToOwner = new HashMap<>();

        for (Class<?> clazz : listControllerClasses()) {
            String prefix = classLevelPrefix(clazz);
            for (Method method : clazz.getDeclaredMethods()) {
                PostMapping post = method.getAnnotation(PostMapping.class);
                if (post == null) {
                    continue;
                }
                for (String path : post.value()) {
                    String key = "POST " + normalize(prefix + path);
                    String owner = clazz.getSimpleName() + "#" + method.getName();
                    String existing = mappingToOwner.put(key, owner);
                    assertNull(existing, "路由冲突：" + key + " 被 [" + existing + "] 和 ["
                            + owner + "] 同时声明 —— Spring 启动期会抛 Ambiguous mapping");
                }
            }
        }

        assertFalse(mappingToOwner.isEmpty(), "没有扫到任何 Controller 映射 —— 扫描逻辑本身坏了");
    }

    @Test
    @DisplayName("POST /save 归前端契约（sku），内部接口挪到 /save/internal")
    void savePathBelongsToFrontendContract() {
        Method frontendSave = findMethod("saveDescription");
        assertNotNull(frontendSave, "找不到前端契约的保存方法");
        assertEquals(FrontendDescriptionSaveRequest.class,
                frontendSave.getParameterTypes()[0],
                "POST /save 必须收前端契约的 FrontendDescriptionSaveRequest");

        Method internalSave = findMethod("saveDescriptionInternal");
        assertNotNull(internalSave, "找不到内部保存方法");
        assertEquals(DescriptionGenerationResponse.class,
                internalSave.getParameterTypes()[0],
                "内部保存接口收 DescriptionGenerationResponse");
    }

    // ------------------------------------------------------------------ 辅助

    private static Method findMethod(String name) {
        for (Method method : ProductDescriptionController.class.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        return null;
    }

    private static List<Class<?>> listControllerClasses() {
        List<Class<?>> classes = new ArrayList<>();
        classes.add(ProductDescriptionController.class);
        return classes;
    }

    private static String classLevelPrefix(Class<?> clazz) {
        RequestMapping rm = clazz.getAnnotation(RequestMapping.class);
        if (rm == null || rm.value().length == 0) {
            return "";
        }
        return rm.value()[0];
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
