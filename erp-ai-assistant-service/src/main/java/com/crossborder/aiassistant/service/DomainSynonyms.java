package com.crossborder.aiassistant.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 📌 **领域同义词组**（2026-11-04 抽成公共类）。
 *
 * <h3>为什么需要它</h3>
 * 任何基于**字面**的检索（字符 n-gram、特征哈希）都有一个共同短板：
 * <pre>
 *   「退钱流程是什么」  vs  「如何申请退款？」  → 一个共同字都没有
 *   「多久能收到货」    vs  「物流时效说明」    → 字面几乎不重合
 * </pre>
 * → 用户口语 ↔ 系统术语的差异，会让**真正相关的知识被漏掉**。
 *
 * <h3>为什么之前不管用</h3>
 * 这份词表原本是 {@code AIAssistantServiceImpl} 里的
 * <b>private 静态字段</b> → 只有那条「关键词检索」路径能用
 * → 而 RAG 那条「向量检索」路径<b>完全拿不到</b>
 * → 两套检索对同一个问题的表现**不一致**。
 *
 * <h3>📌 为什么是「对称」扩展</h3>
 * 旧词表是**单向**的：{@code 退钱 → [退款, 退货]}。
 * → 查询里写「退钱」能扩展成「退款」，但**文档里写「退款」不会扩展成「退钱」**
 * → 两边扩展得不对称 → 特征空间对不齐 → 依然匹配不上。
 *
 * <p>实测（2026-11-04）：
 * <pre>
 *   单向扩展：查询「退钱流程」→ 命中最差的文档（0.0000）
 *   对称扩展：查询「退钱流程」→ 命中「产品售后政策」（0.3125）✅
 * </pre>
 *
 * <p>→ 现在：**命中组内任一成员，就补上整组**（查询侧和文档侧都做）。
 *
 * <p>📌 词表「小而准」优先覆盖客服高频问法，不要贪多 ——
 * 同义词越多，误召回也越多。
 *
 * @author OmniTrade AI Team
 * @since 1.8.0
 */
public final class DomainSynonyms {

    private DomainSynonyms() {
    }

    /**
     * 领域同义词**组**：组内成员**互相等价**。
     *
     * <p>📌 每组是一个「等价类」——命中任一成员就补齐整组。
     * 例如「退钱 / 退款 / 退货 / 售后」其实是同一件事的四种说法。
     */
    private static final List<List<String>> GROUPS = List.of(
            // 退款 / 退货 / 售后 —— 客服高频
            List.of("退钱", "退款", "退货", "售后", "退换货"),
            // 查订单
            List.of("查订单", "查询订单", "订单查询", "我的订单"),
            // 订单号
            List.of("订单号", "订单编号"),
            // 时效 / 送达 / 收到货
            List.of("几天", "多久", "时效", "送达", "签收", "收到货", "物流", "配送"),
            // 价格 / 费用 / 多少钱
            List.of("多少钱", "价格", "费用", "定价"),
            // 库存 / 缺货 / 补货
            List.of("库存不足", "缺货", "补货", "库存", "安全库存"),
            // 取消订单
            List.of("怎么取消", "取消订单", "撤销"),
            // 发票
            List.of("开发票", "发票", "开票"),
            // 运费
            List.of("邮费", "运费", "shipping"),
            // 优惠
            List.of("优惠", "折扣", "促销"),
            // 规格
            List.of("尺寸", "规格", "尺码"),
            // 自提
            List.of("上门", "取货", "自提")
    );

    /**
     * 查询文本里命中的所有同义词扩展（**不含原文**）。
     *
     * @param query 用户查询（可为 null）
     * @return 扩展词集合；没有命中时返回空集合
     */
    public static Set<String> expand(String query) {
        Set<String> out = new LinkedHashSet<>();
        if (query == null || query.isBlank()) {
            return out;
        }
        String lower = query.toLowerCase();
        for (List<String> group : GROUPS) {
            for (String member : group) {
                if (lower.contains(member.toLowerCase())) {
                    for (String other : group) {
                        if (!other.equals(member)) {
                            out.add(other);
                        }
                    }
                    break; // 一个组只命中一次
                }
            }
        }
        return out;
    }

    /**
     * 📌 供「关键词检索」路径使用（它需要**变体文本**，而不是拼接扩展）。
     *
     * <p>📌 为什么要暴露这个：
     * 之前同义词表在 {@code AIAssistantServiceImpl} 里是 <b>private</b> 的
     * → 关键词路径一套、向量路径另一套 → <b>两套知识会漂移</b>
     * （改了这边忘了那边 → 同一个问题在两条路径上表现不一致）。
     * → 现在<b>唯一事实来源 = {@link #GROUPS}</b>，两条路径共用。
     *
     * @return 同义词组（每组内成员互相等价）
     */
    public static List<List<String>> groups() {
        return GROUPS;
    }

    /**
     * 查询文本 + 它的同义词扩展（用于向量检索时**一起**参与特征提取）。
     *
     * <p>📌 为什么要**一起**参与，而不是只算扩展词：
     * 只用扩展词会丢掉原文特征；只用原文会漏掉同义表达
     * → 两者**拼接后一起算特征**，重合的特征会**叠加权重** → 相似度自然升高。
     *
     * <p>📌 **查询侧和文档侧都要调这个方法**（对称扩展）——
     * 只扩展一边 → 特征空间对不齐 → 匹配不上。
     *
     * @param text 原文（查询或文档正文）
     * @return 原文 + 扩展词的拼接文本
     */
    public static String expandForEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Set<String> expanded = expand(text);
        if (expanded.isEmpty()) {
            return text;
        }
        List<String> parts = new ArrayList<>();
        parts.add(text);
        parts.addAll(expanded);
        return String.join(" ", parts);
    }
}
