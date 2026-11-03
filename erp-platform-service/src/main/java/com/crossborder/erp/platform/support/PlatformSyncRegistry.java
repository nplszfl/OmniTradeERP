package com.crossborder.erp.platform.support;

import com.crossborder.erp.common.constant.PlatformType;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.platform.api.PlatformOrderSync;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台同步器注册表 —— 按**平台码**（amazon / ebay / shopee / lazada / tiktok）索引。
 *
 * <p>🔴 <b>这个类存在的唯一原因，是修一个「静默失效」的全项目级缺陷。</b></p>
 *
 * <p><b>缺陷本体：</b>原代码在三个地方都直接注入
 * {@code Map<String, PlatformOrderSync> platformSyncMap}，
 * 然后用 {@code platformSyncMap.get(platform.toLowerCase())} 查表：</p>
 * <ul>
 *   <li>{@code PlatformOrderSyncServiceImpl}（手动同步接口）</li>
 *   <li>{@code PlatformConfigServiceImpl}（测试连接接口）</li>
 *   <li>{@code OrderSyncSchedule}（定时同步任务）</li>
 * </ul>
 *
 * <p><b>为什么永远查不到：</b>Spring 注入 {@code Map<String, T>} 时，
 * <b>key 是 bean 名</b>（{@code amazonOrderSync} / {@code ebayOrderSync} …），
 * <b>不是</b>你想要的平台码（{@code amazon} / {@code ebay} …）。
 * 本项目<b>没有任何地方</b>定义过这个 Map bean（全局搜 {@code Map<String, PlatformOrderSync>}
 * 只有这三处注入点 + 两个测试），所以 Spring 走的是「多元素收集」分支，
 * key 必然是 bean 名。</p>
 *
 * <p>实测（Spring 6.2.19 真实容器）：</p>
 * <pre>
 * KEYS = [amazonOrderSync, ebayOrderSync]
 * get("amazon")         = null
 * get("amazonOrderSync") = AmazonOrderSync@32a068d1
 * </pre>
 *
 * <p><b>后果（比报错更糟）：</b>三个入口全部 <b>永远</b> 判定「不支持的平台」，
 * 但只有其中一处抛异常，另外两处<b>静默返回</b>：</p>
 * <ul>
 *   <li>定时同步：每个平台都记为「暂不支持平台」→ <b>一条订单都同步不到</b>，
 *       但日志只有一行 warn，没有告警</li>
 *   <li>测试连接：{@code testConnection} 里 {@code sync == null} 抛
 *       「暂不支持该平台」→ 所有店铺连接测试全失败</li>
 *   <li>手动同步接口：返回 400「暂不支持该平台」</li>
 * </ul>
 *
 * <p>📌 <b>为什么单测全绿</b>：两个测试类都<b>手工</b>
 * {@code platformSyncMap.put("amazon", amazonSync)}，
 * 把真实 Spring 的错误行为<b>在测试里人工修正了</b> ——
 * 这正是「mock 不过滤 = 测试假绿」的另一种形态：
 * <b>mock 构造出来的前提，恰好绕过了生产环境真实存在的缺陷。</b>
 * 凡是「测试里手动 put 的 map / list」，都要问一句：
 * <b>生产环境这个容器里真的有这个 key 吗？</b></p>
 *
 * <p>📌 另一个坑：<b>自定义 {@code @Bean Map<String, T>} 也救不了</b>。
 * Spring 解析 Map 依赖时直接走多元素收集分支，会<b>无视</b>你定义的 Map bean
 * （实测：定义了 key 为 "amazon" 的 Map bean，注入进来的仍是
 * {@code [amazonOrderSync, ebayOrderSync]}）。
 * 所以只能<b>换一种类型</b>——用一个专门的 registry 对象来承载。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@Slf4j
@Component
public class PlatformSyncRegistry {

    /** 平台码 → 同步器（不可变） */
    private final Map<String, PlatformOrderSync> byPlatformCode;

    public PlatformSyncRegistry(List<PlatformOrderSync> syncList) {
        Map<String, PlatformOrderSync> map = new LinkedHashMap<>();
        if (syncList != null) {
            for (PlatformOrderSync sync : syncList) {
                if (sync == null) {
                    continue;
                }
                PlatformType type = sync.getPlatformType();
                if (type == null) {
                    log.warn("平台同步器未声明 getPlatformType()，跳过: {}", sync.getClass().getName());
                    continue;
                }
                PlatformOrderSync prev = map.put(type.getCode(), sync);
                if (prev != null) {
                    // 📌 同一平台注册了两个实现 → 后者覆盖前者，且不报错。
                    //    这是「静默覆盖」型缺陷，必须显式告警。
                    log.warn("平台 {} 存在多个同步器实现，后者覆盖前者: {} 覆盖 {}",
                            type.getCode(), sync.getClass().getSimpleName(),
                            prev.getClass().getSimpleName());
                }
            }
        }
        this.byPlatformCode = Collections.unmodifiableMap(map);
        log.info("平台同步器注册完成, 已注册平台: {}", byPlatformCode.keySet());
    }

    /**
     * 按平台码获取同步器（大小写不敏感）
     *
     * @param platform 平台码，如 amazon / AMAZON / Amazon
     * @return 同步器；未注册返回 {@code null}
     */
    public PlatformOrderSync get(String platform) {
        if (platform == null || platform.isBlank()) {
            return null;
        }
        return byPlatformCode.get(platform.trim().toLowerCase());
    }

    /**
     * 按平台码获取同步器，未注册时抛异常
     *
     * @param platform 平台码
     * @return 同步器
     * @throws BizException 平台未注册
     */
    public PlatformOrderSync require(String platform) {
        PlatformOrderSync sync = get(platform);
        if (sync == null) {
            // 📌 用 BizException（业务异常 → 400）而不是 IllegalArgumentException，
            //    否则全局兜底分支会把「不支持的平台」报成「系统繁忙」。
            throw new BizException(
                    "暂不支持该平台: " + platform + "（已支持: " + byPlatformCode.keySet() + "）");
        }
        return sync;
    }

    /**
     * 已注册的平台码集合
     */
    public java.util.Set<String> registeredPlatforms() {
        return byPlatformCode.keySet();
    }
}
