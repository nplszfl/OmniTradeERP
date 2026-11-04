package com.crossborder.aiassistant.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 证明 provider 校验<b>真的在 Spring 启动时触发</b>
 *
 * <p>🔴 <b>为什么必须是 Spring 上下文测试</b>（知识库规则 46）：
 * 单独 {@code new LLMConfig()} 再调 {@code validateProviderOnStartup()}
 * 只证明了「这个方法存在且能抛异常」，
 * <b>不能证明 Spring 容器启动时真的调了它</b> ——
 * 而「@PostConstruct 没被调用」正是这类缺陷最常见的形态：
 * 方法写了、注释也写了、单测也绿了，但<b>运行期从来没执行过</b>。
 *
 * <p>📌 <b>反例就在隔壁</b>：{@code isSupportedProvider()} 就是这么变成死代码的 ——
 * 它被单测调用过，但<b>没有任何运行期调用点</b>。
 *
 * <p>📌 <b>为什么用 ApplicationContextRunner 而不是 @SpringBootTest</b>：
 * 本服务启动需要 Nacos + MySQL（{@code @SpringBootTest} 会去连真库），
 * 而这里要验的是「<b>配置类</b>的 @PostConstruct 有没有被调用」——
 * 用最小上下文即可，不必把整个应用（含数据库）拖起来。
 * 📌 本仓库 {@code erp-common} 的自动装配测试用的也是这个模式。
 */
class LLMConfigStartupValidationTest {

    /**
     * 📌 **必须引入 ConfigurationPropertiesAutoConfiguration**：
     * 没有它，{@code @ConfigurationProperties} 的绑定器根本不会注册 →
     * 属性根本没绑上（读到的是类默认值）→ 测试会假绿。
     * 📌 这是本项目踩过的「测试写得太轻，验不到真东西」的典型：
     * 第一次写这个测试时漏了这一行 → 断言全部失败，
     * 查下去才发现是**测试脚手架**没配全，不是被测代码有问题。
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(LLMConfig.class);

    @Test
    @DisplayName("provider 拼错 → Spring 启动必须失败（证明 @PostConstruct 真的被调用）")
    void typoProviderMustFailContextStartup() {
        runner.withPropertyValues(
                        "llm.enabled=true",
                        "llm.api-key=sk-test-key",
                        "llm.provider=deepseel")   // ← 注入的缺陷
                .run(context -> {
                    assertTrue(context.getStartupFailure() != null,
                            "provider 拼错时上下文必须启动失败 —— 说明 @PostConstruct 校验没生效");
                    String msg = collectMessages(context.getStartupFailure());
                    assertTrue(msg.contains("deepseel"),
                            "启动失败的原因要说清是 provider 的问题，实际：\n" + msg);
                });
    }

    @Test
    @DisplayName("provider 显式为空 → Spring 启动必须失败")
    void blankProviderMustFailContextStartup() {
        // 📌 **注意**：不设 llm.provider 时会拿到**类默认值 deepseek**（合法）——
        //    所以「空」必须**显式**设成空串才能触发。
        //    （yml 里是 `${LLM_PROVIDER:deepseek}`，默认也是 deepseek）
        runner.withPropertyValues(
                        "llm.enabled=true",
                        "llm.api-key=sk-test-key",
                        "llm.provider=")
                .run(context -> {
                    assertTrue(context.getStartupFailure() != null,
                            "provider 显式为空时上下文必须启动失败");
                    String msg = collectMessages(context.getStartupFailure());
                    assertTrue(msg.contains("provider"),
                            "启动失败的原因要说清是 provider 的问题，实际：\n" + msg);
                });
    }

    @Test
    @DisplayName("provider=azure 缺 deployment → Spring 启动必须失败")
    void azureMissingDeploymentMustFailContextStartup() {
        runner.withPropertyValues(
                        "llm.enabled=true",
                        "llm.api-key=sk-test-key",
                        "llm.provider=azure")
                .run(context -> {
                    assertTrue(context.getStartupFailure() != null,
                            "azure 缺 deployment 时上下文必须启动失败");
                    String msg = collectMessages(context.getStartupFailure());
                    assertTrue(msg.contains("deployment"),
                            "要说清缺的是 deployment，实际：\n" + msg);
                });
    }

    /**
     * 📌 **为什么要递归收集 cause 链**：
     * Spring 会把 {@code @PostConstruct} 抛出的异常包一层
     * （{@code BeanCreationException: Invocation of init method failed}），
     * 直接看最外层 message 会找不到真正的原因。
     * 📌 这正是排查「启动失败」时最常踩的一脚 ——
     *    报错信息指向 {@code init method failed}，真正原因在 cause 链里。
     */
    private String collectMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 20) {
            if (cur.getMessage() != null) {
                sb.append(cur.getMessage()).append('\n');
            }
            cur = cur.getCause();
            depth++;
        }
        return sb.toString();
    }

    @Test
    @DisplayName("provider 正确 → Spring 启动成功")
    void correctProviderMustStartSuccessfully() {
        runner.withPropertyValues(
                        "llm.enabled=true",
                        "llm.api-key=sk-test-key",
                        "llm.provider=deepseek")
                .run(context -> {
                    assertTrue(context.getStartupFailure() == null,
                            "provider 正确时不该启动失败，实际：\n"
                                    + context.getStartupFailure());
                    assertTrue(context.getBean(LLMConfig.class) != null,
                            "LLMConfig 必须被注册成 bean");
                });
    }

    @Test
    @DisplayName("没启用真实调用时，provider 拼错也不拦启动")
    void disabledDoesNotBlockStartup() {
        runner.withPropertyValues(
                        "llm.enabled=false",
                        "llm.provider=deepseel")
                .run(context -> assertTrue(context.getStartupFailure() == null,
                        "enabled=false 时不该拦启动，实际：\n"
                                + context.getStartupFailure()));
    }
}
