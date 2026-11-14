package com.crossborder.erp.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「本项目自定义的 Bean 名 ↔ Spring Boot 自动装配的 Bean 名」**冲突巡检**。
 *
 * <h3>为什么需要这个测试</h3>
 *
 * <p>2026-11-14 第一次<b>真的把 ERP 跑起来</b>时（此前从未真正部署过），
 * 抓到一个让<b>网关完全起不来</b>的缺陷：</p>
 *
 * <pre>
 *   erp-common 的 RedisConfig#redisTemplate  （Bean 名: redisTemplate）
 *   Boot 的 RedisAutoConfiguration#redisTemplate （Bean 名: redisTemplate，同名！）
 *
 *   → BeanDefinitionOverrideException:
 *     Invalid bean definition with name 'redisTemplate' ... overriding is disabled
 *   → APPLICATION FAILED TO START → **erp-gateway 根本起不来**
 * </pre>
 *
 * <p>🔴 <b>迷惑性极强</b>：</p>
 * <ul>
 *   <li>{@code erp-user-service} / {@code erp-order-service} 等 <b>servlet</b> 服务
 *       <b>全都正常启动</b>（Boot 的自动装配顺序恰好让 erp-common 先注册），
 *       <b>只有 reactive 的 gateway 挂</b> → 「大部分服务好好的」掩盖了「网关根本起不来」；</li>
 *   <li>1047 个用例全绿、70+ 道闸门全绿、CI 双绿 —— <b>没有一道闸门能看见它</b>
 *       （因为单测不启动整个上下文，闸门是静态扫描）；</li>
 *   <li>文档、编排、k8s 清单全都「看着对」—— 缺陷只在<b>真起进程</b>时才暴露。</li>
 * </ul>
 *
 * <h3>为什么不能靠「类名字母序」</h3>
 *
 * <p>原代码的注释写着「自动装配按类名排序，{@code com.crossborder.*} 排在
 * {@code org.springframework.*} 之前，因此本配置优先生效」——
 * <b>这句话是错的</b>：{@code @AutoConfigureBefore/@AutoConfigureAfter} 会重排整张图，
 * 字母序只在「没有任何排序注解」时才成立。erp-gateway 上实测被证伪。</p>
 *
 * <h3>本测试查什么</h3>
 *
 * <ol>
 *   <li><b>运行时</b>读出 Spring Boot 所有自动装配类
 *       （{@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}）
 *       声明的 {@code @Bean} 方法名 —— <b>不硬编码</b>，Boot 升级后自动跟随；</li>
 *   <li>读出本项目（erp-common）自定义的 {@code @Bean} 方法名；</li>
 *   <li>两者<b>同名</b> → 必须有<b>冲突消解机制</b>，否则应用起不来：
 *       <ul>
 *         <li>本项目的 {@code @Bean} 上有 {@code @ConditionalOnMissingBean}（后到者退让），<b>或</b></li>
 *         <li>本项目的自动装配类上有 {@code @AutoConfigureBefore(Boot 的那个类)}（顺序确定，谁先谁后固定）。</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>📌 两个条件<b>至少满足一个</b>才算通过 —— 只满足一个仍然可能是「碰巧对」，
 * 但两者都满足时：无论自动装配顺序如何，都只有<b>一个</b> {@code redisTemplate}，
 * <b>且一定是 erp-common 的 FastJSON2 版本</b>（序列化行为跨服务一致）。</p>
 *
 * <p>📌 <b>反向验证</b>：把 {@code CommonRedisAutoConfiguration} 上的
 * {@code @AutoConfigureBefore} 去掉、并把 {@code RedisConfig#redisTemplate} 上的
 * {@code @ConditionalOnMissingBean} 去掉 → 本测试必须变红。</p>
 */
class AutoConfigurationBeanNameConflictTest {

    /** Boot 自动装配类清单的位置（运行时读，Boot 升级自动跟随）。 */
    private static final String IMPORTS_RESOURCE =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    /** 本项目自己的包前缀 —— 用于排除「自己跟自己比」的假冲突。 */
    private static final String PROJECT_PACKAGE_PREFIX = "com.crossborder.erp.";

    /** erp-common 里提供 Bean 的配置类（人工枚举 —— 本项目自研的自动装配就这几个）。 */
    private static final List<Class<?>> PROJECT_CONFIG_CLASSES = Arrays.asList(
            CommonRedisAutoConfiguration.class,
            CommonWebAutoConfiguration.class,
            CommonFeignAutoConfiguration.class,
            CommonMetricsAutoConfiguration.class,
            CommonJacksonAutoConfiguration.class,
            RedisConfig.class
    );

    /** Boot 自动装配类里，已知与本项目 Bean 同名的那些（由下面反射自动补全，这里是人工声明的兜底清单）。 */
    @Test
    @DisplayName("自动装配 Bean 名冲突：本项目与 Boot 同名的 Bean 必须有冲突消解机制")
    void noUnresolvedBeanNameConflict() throws Exception {
        // ── 1. 读出 Boot 所有自动装配类的 @Bean 方法名 ──────────────
        Map<String, String> bootBeanNames = new LinkedHashMap<>();
        List<String> autoConfigClasses = readBootAutoConfigurationClasses();
        assertThat(autoConfigClasses)
                .as("必须能从 classpath 读出 Boot 的自动装配清单（%s）", IMPORTS_RESOURCE)
                .isNotEmpty();

        for (String className : autoConfigClasses) {
            // 📌 **排除与本项目自身比较**：erp-common 自己也注册在 Boot 的
            //    AutoConfiguration.imports 里（它就是个 @AutoConfiguration）。
            //    把「本项目的类」和「Boot 的类」两个集合混在一起，
            //    会把自己跟自己比 → **假红**（实测踩到：
            //    CommonJacksonAutoConfiguration#erpLongIdAsStringModule
            //    被判成「与 Boot 同名冲突」→ 实际是同一个类 → 永远不会冲突）。
            if (className.startsWith(PROJECT_PACKAGE_PREFIX)) {
                continue;
            }
            Class<?> clazz;
            try {
                clazz = Class.forName(className, false,
                        AutoConfigurationBeanNameConflictTest.class.getClassLoader());
            } catch (Throwable ignored) {
                continue;   // 该自动装配的依赖不在 classpath 上 → 无关
            }
            for (Method m : declaredMethodsSafely(clazz)) {
                if (m.getAnnotation(Bean.class) == null) {
                    continue;
                }
                // @Bean(name=...) 优先，否则用方法名
                Bean bean = m.getAnnotation(Bean.class);
                String[] names = bean.name();
                String beanName = (names.length > 0 && !names[0].isEmpty()) ? names[0] : m.getName();
                bootBeanNames.putIfAbsent(beanName, className);
            }
        }
        assertThat(bootBeanNames)
                .as("必须能从 Boot 自动装配类里读出 @Bean 名 —— 判据可能没在读 classpath")
                .isNotEmpty();

        // ── 2. 读出本项目自定义的 @Bean 名 ─────────────────────────
        Map<String, List<Class<?>>> projectBeanNames = new LinkedHashMap<>();
        for (Class<?> clazz : PROJECT_CONFIG_CLASSES) {
            for (Method m : declaredMethodsSafely(clazz)) {
                if (m.getAnnotation(Bean.class) == null) {
                    continue;
                }
                Bean bean = m.getAnnotation(Bean.class);
                String[] names = bean.name();
                String beanName = (names.length > 0 && !names[0].isEmpty()) ? names[0] : m.getName();
                projectBeanNames.computeIfAbsent(beanName, k -> new ArrayList<>()).add(clazz);
            }
        }
        assertThat(projectBeanNames)
                .as("必须能读出本项目 erp-common 的 @Bean 名 —— 判据可能没在读本项目的类")
                .isNotEmpty();

        // ── 3. 找同名冲突 ────────────────────────────────────────
        List<String> unresolved = new ArrayList<>();
        for (Map.Entry<String, List<Class<?>>> e : projectBeanNames.entrySet()) {
            String beanName = e.getKey();
            String bootClass = bootBeanNames.get(beanName);
            if (bootClass == null) {
                continue;   // 不同名 → 无冲突
            }
            for (Class<?> projectClass : e.getValue()) {
                Method beanMethod = findBeanMethod(projectClass, beanName);
                boolean hasMissingBeanGuard = beanMethod != null
                        && beanMethod.getAnnotation(ConditionalOnMissingBean.class) != null;
                boolean hasOrdering = declaresBefore(projectClass, bootClass);

                if (!hasMissingBeanGuard && !hasOrdering) {
                    unresolved.add(String.format(
                            "Bean 名冲突 '%s'：本项目 %s#%s 与 Boot 的 %s 同名，"
                                    + "且既没有 @ConditionalOnMissingBean（后到者退让），"
                                    + "也没有 @AutoConfigureBefore（顺序不确定）"
                                    + " → 应用启动时会抛 BeanDefinitionOverrideException，服务起不来",
                            beanName,
                            projectClass.getSimpleName(),
                            beanName,
                            bootClass));
                }
            }
        }

        assertThat(unresolved)
                .as("发现 %d 处未消解的自动装配 Bean 名冲突（会导致服务起不来）", unresolved.size())
                .isEmpty();
    }

    /** 反向验证：把两处保护都去掉 → 判据必须报红（否则这道闸门形同虚设）。 */
    @Test
    @DisplayName("反向验证：判据本身能识别出「无保护的重名」")
    void detectsUnguardedConflict() throws Exception {
        // 造一个「和 Boot 同名、又没有保护」的假配置类
        class RogueConfig {
            @Bean
            public org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate(
                    org.springframework.data.redis.connection.RedisConnectionFactory cf) {
                return null;
            }
        }
        // 判据内部逻辑：查同名 + 查两种保护
        Method m = findBeanMethod(RogueConfig.class, "redisTemplate");
        assertThat(m).as("必须能定位到 RogueConfig 的 @Bean 方法").isNotNull();
        boolean guard = m.getAnnotation(ConditionalOnMissingBean.class) != null;
        assertThat(guard).as("RogueConfig 故意没有保护 → 判据必须识别为「无保护」").isFalse();
        assertThat(declaresBefore(RogueConfig.class,
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"))
                .as("RogueConfig 故意没有排序注解 → 判据必须识别为「无排序」").isFalse();
    }

    // ────────────────────────────── 工具方法 ──────────────────────────────

    /**
     * 安全地拿 {@code getDeclaredMethods()}。
     *
     * <p>📌 有些 Boot 自动装配类引用了**本模块 classpath 上没有**的类
     * （如 actuator 的 {@code HealthIndicator}）→
     * {@code getDeclaredMethods()} 会抛 {@link NoClassDefFoundError}
     * （它抛在解析方法签名时，不是 {@link ClassNotFoundException}）。
     * 不抦的话整个巡检直接崩 → 永远「红」→ 变成噪音闸门。</p>
     */
    private static Method[] declaredMethodsSafely(Class<?> clazz) {
        try {
            return clazz.getDeclaredMethods();
        } catch (Throwable t) {
            return new Method[0];
        }
    }

    private static List<String> readBootAutoConfigurationClasses() throws IOException {
        List<String> out = new ArrayList<>();
        // 📌 **必须用 getResources() 读全部，不能用 getResourceAsStream()**：
        //    erp-common **自己**也注册了一个同名文件
        //    （erp-common/src/main/resources/META-INF/spring/
        //      org.springframework.boot.autoconfigure.AutoConfiguration.imports，只有 6 行）。
        //    `getResourceAsStream()` 只返回**第一个**命中 → 拿到的是 erp-common 自己那份
        //    → Boot 的 100+ 个自动装配类**一个都读不到** → 判据「扫不到任何 @Bean 名」。
        //    （实测踩过：第一次写成 getResourceAsStream → 假红「必须能从 classpath 读出 @Bean 名」。）
        //    `getResources()` 会返回 classpath 上**所有**同名资源 → 必须逐个读完。
        Enumeration<java.net.URL> urls = AutoConfigurationBeanNameConflictTest.class.getClassLoader()
                .getResources(IMPORTS_RESOURCE);
        while (urls.hasMoreElements()) {
            java.net.URL url = urls.nextElement();
            try (InputStream in = url.openStream()) {
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                for (String line : content.split("\\R")) {
                    String s = line.trim();
                    if (s.isEmpty() || s.startsWith("#")) {
                        continue;
                    }
                    out.add(s);
                }
            }
        }
        return out;
    }

    private static Method findBeanMethod(Class<?> clazz, String beanName) {
        for (Method m : declaredMethodsSafely(clazz)) {
            if (m.getAnnotation(Bean.class) == null) {
                continue;
            }
            Bean bean = m.getAnnotation(Bean.class);
            String[] names = bean.name();
            String n = (names.length > 0 && !names[0].isEmpty()) ? names[0] : m.getName();
            if (n.equals(beanName)) {
                return m;
            }
        }
        return null;
    }

    /** 本类（含其父类）是否声明了 {@code @AutoConfigureBefore(bootClass)}。 */
    private static boolean declaresBefore(Class<?> clazz, String bootClassName) {
        // @AutoConfiguration 本体携带 @AutoConfigureBefore 作为元注解 → 读它的 before()
        AutoConfiguration ac = clazz.getAnnotation(AutoConfiguration.class);
        if (ac != null) {
            for (Class<?> c : ac.before()) {
                if (c.getName().equals(bootClassName)) {
                    return true;
                }
            }
        }
        // 也可能直接标在 @Configuration 类上
        AutoConfigureBefore before = clazz.getAnnotation(AutoConfigureBefore.class);
        if (before != null) {
            for (Class<?> c : before.value()) {
                if (c.getName().equals(bootClassName)) {
                    return true;
                }
            }
        }
        return false;
    }
}
