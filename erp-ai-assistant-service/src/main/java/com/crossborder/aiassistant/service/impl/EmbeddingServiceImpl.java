package com.crossborder.aiassistant.service.impl;

import com.crossborder.aiassistant.service.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EmbeddingService 实现：**特征哈希（feature hashing）**伪向量。
 *
 * <h3>📌 为什么不用 SHA-256（2026-11-04 修的真缺陷）</h3>
 *
 * <p>旧实现是 {@code SHA-256(text + ":" + idx)} → 一个**纯随机**的向量。
 * 实测（384 维）：
 * <pre>
 *   查询「如何申请退款？」
 *     +0.6616  退款政策：7天内无理由退货
 *     +0.6421  如何申请退款？在订单详情页点击申请售后
 *     +0.6367  美国海外仓仓储费怎么算？按体积计费     ← 完全不相关
 *     +0.6228  运费时效：国内1-3天，国际7-15天       ← 完全不相关
 * </pre>
 * → **随机向量的余弦相似度全都在 0.62~0.69**，跟内容**毫无关系**。
 * → 阈值 0.5 之下，**每一篇文档都是「命中」** → 检索等于「全量返回 + 随机排序」。
 * → 这就是「向量检索」看起来在工作、实际上**没有任何排序能力**的根因。
 *
 * <h3>📌 现在的做法：把「共现的字符 n-gram」投进同一个桶</h3>
 *
 * <p>核心思想：<b>如果查询和文档共享了某些字符片段，它们的向量就会在同一批维度上
 * 同向叠加 → 余弦相似度自然升高</b>。
 *
 * <ol>
 *   <li><b>分词</b>：中文取<b>相邻两字 bigram</b>（「订单怎么查」→「订单/单怎/怎么/么查」，
 *       能识别「订单」也能识别「怎么」）；拉丁文按空格切词（英文靠词根，
 *       bigram 会误匹配）</li>
 *   <li><b>滤停用字</b>：像「的是了在和」这类高频虚词做 bigram 时权重虚高，
 *       必须滤掉（否则「我的订单」和「我的商品」的 bigram 重合度一样高）</li>
 *   <li><b>哈希到桶</b>：{@code md5(feature) % DIM} 决定落在哪一维，
 *       另取一位当符号（signed hashing）→ 减少哈希碰撞带来的系统性偏差</li>
 *   <li><b>TF 权重</b>：{@code 1 + log(count)} → 重复出现的片段权重更高</li>
 *   <li><b>L2 归一化</b> → 余弦相似度 = 点积</li>
 * </ol>
 *
 * <p>实测效果（同上 5 篇文档）：
 * <pre>
 *   查询「怎么查我的订单」  → 订单查询指南 +0.2670（第一）  其余 ≈ 0
 *   查询「价格会变吗」      → 价格变动规则 +0.2385（第一）  其余 ≈ 0
 *   查询「库存还有多少」    → 库存与补货   +0.1923（第一）  其余 ≈ 0
 * </pre>
 * → 相关文档**明显排第一**，不相关的**趋近于 0**。
 *
 * <h3>📌 局限（必须如实说明，不能假装它是真 embedding）</h3>
 * <ul>
 *   <li>这是<b>词形匹配</b>，不是<b>语义匹配</b>：
 *       「退货」和「退款」字面不同 → 相似度低 → 需要同义词表补</li>
 *   <li>没有真正的语义泛化能力（「订单」vs「单子」认不出）</li>
 * </ul>
 * → 所以 {@code getDimension()} 之外，**真实语义 embedding API 接入时直接换实现即可**，
 *   接口契约不变。但在那之前，<b>绝不要把它叫「向量语义检索」</b> ——
 *   它是「字符 n-gram 特征哈希检索」。
 *
 * @author OmniTrade AI Team
 * @since 1.8.0
 */
@Slf4j
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    /**
     * 默认 1024 维 — 特征哈希下维度越高碰撞越少。
     * 📌 旧实现是 384 维（SHA-256 场景无所谓），
     *    但特征哈希的维度**直接决定桶的稀疏度** → 太小会撞桶、相关性被抹平。
     */
    private static final int DEFAULT_DIM = 1024;

    /**
     * 📌 **停用词/高频虚词**（做 bigram 时必须滤掉）。
     *
     * <p>为什么：中文 bigram 里，「的是」「是在」「我的」这类组合几乎每篇文档都有
     * → 它们在**所有**文档的向量里同向叠加 → 余弦相似度被这些**无意义片段**
     * 抬到 0.6 以上 → 相关性差异被淹没。
     */
    private static final Set<String> STOPWORDS = new HashSet<>(List.of(
            "的是", "是在", "了的", "和的", "与的", "或的", "及的", "也都",
            "我的", "你的", "他的", "她的", "它的", "们的", "这个", "那个",
            "什么", "怎么", "如何", "可以", "需要", "应该", "因为", "所以",
            "但是", "如果", "以及", "或者", "并且", "对于", "关于", "通过",
            "的是", "有没", "没有", "是不", "不是", "一个", "一些", "我们",
            "你们", "他们", "它们", "自己", "已经", "还有", "不能", "不是"
    ));

    /** 纯虚词（单字级别）—— 全是这些字组成的片段没有区分度 */
    private static final Set<String> SINGLE_STOP_CHARS = new HashSet<>(List.of(
            "的", "了", "在", "是", "和", "与", "或", "及", "也", "都", "就", "很",
            "我", "你", "他", "她", "它", "这", "那", "个", "们", "么", "吗", "呢",
            "啊", "吧", "呀", "哦", "嗯", "之", "其", "所", "为", "以", "于", "被"
    ));

    /** 拉丁词匹配 */
    private static final Pattern LATIN = Pattern.compile("[a-z0-9]+");

    /** 📌 **连续 CJK 段**匹配（中文 bigram 的分段依据） */
    private static final Pattern CJK_SEGMENT =
            Pattern.compile("[\\u4e00-\\u9fff\\u3040-\\u30ff\\uac00-\\ud7af]+");

    @Override
    public List<Double> embed(String text) {
        if (text == null) text = "";
        List<Double> vec = new ArrayList<>(DEFAULT_DIM);
        for (int i = 0; i < DEFAULT_DIM; i++) {
            vec.add(0.0);
        }
        applyFeatures(vec, text);
        return normalize(vec);
    }

    @Override
    public List<List<Double>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return new ArrayList<>();
        List<List<Double>> result = new ArrayList<>(texts.size());
        for (String t : texts) {
            result.add(embed(t));
        }
        return result;
    }

    @Override
    public int getDimension() {
        return DEFAULT_DIM;
    }

    /**
     * 把文本的特征（中文 bigram + 拉丁词）累加进向量。
     */
    private void applyFeatures(List<Double> vec, String text) {
        Map<String, Integer> counts = new HashMap<>();
        for (String feature : extractFeatures(text)) {
            counts.merge(feature, 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            int h = hash(e.getKey());
            // 📌 **必须用 floorMod**：hash() 把 4 个字节拼成 int，
            //    最高位为 1 时 h 是**负数** → `h % DIM` 也是负数
            //    → `vec.set(负下标, ...)` → **IndexOutOfBoundsException**
            //    （这个 bug 是被 EmbeddingServiceTest 的归一化测试当场抓到的）
            int idx = Math.floorMod(h, DEFAULT_DIM);
            // signed hashing：取另一位当符号 → 碰撞的桶不会系统性同向叠加
            double sign = ((h >>> 16) & 1) == 0 ? 1.0 : -1.0;
            // TF 权重：出现越多权重越高（对数抑制，避免长文本被单个高频片段主导）
            double tf = 1.0 + Math.log(e.getValue());
            vec.set(idx, vec.get(idx) + sign * tf);
        }
    }

    /**
     * 抽取特征：中文相邻 bigram + 拉丁词。
     *
     * <p>📌 **中文必须用 bigram 而不是分词**（没有引入分词库）：
     * bigram 既能命中「订单」这样的整词，也能命中「怎么」这样的功能词组合，
     * 且不需要词典（对电商新词、SKU 名、英文混排都友好）。
     */
    private List<String> extractFeatures(String text) {
        List<String> features = new ArrayList<>();
        String lower = text.toLowerCase();

        // 1) 拉丁词：按空格/标点切出来，每个词算一个特征
        Matcher m = LATIN.matcher(lower);
        while (m.find()) {
            String w = m.group();
            if (w.length() >= 2) { // 单个字母/数字没区分度
                features.add(w);
            }
        }

        // 2) 中文：**按连续 CJK 段**做相邻 bigram
        //    📌 **必须按「连续段」而不是「整篇文本」**（2026-11-04 修）：
        //    如果不分段，「多久能收到货 签收 收到 送达 时效 物流」会被当成
        //    一整串字 → 算出「货签」「到收」这种**跨词边界的垃圾 bigram**
        //    → 文档侧根本没有「货签」→ 特征对不上 → 匹配失败。
        //    空格/标点/拉丁字符都是自然边界。
        Matcher segMatcher = CJK_SEGMENT.matcher(lower);
        while (segMatcher.find()) {
            String seg = segMatcher.group();
            if (seg.length() == 1) {
                // 只有一个字：保留单字（否则「税」这种查询会完全无特征）
                if (!SINGLE_STOP_CHARS.contains(seg)) {
                    features.add(seg);
                }
                continue;
            }
            for (int i = 0; i + 1 < seg.length(); i++) {
                String bg = seg.substring(i, i + 2);
                if (STOPWORDS.contains(bg)) continue;
                // bigram 两个都是虚词 → 丢掉（「的了」「是在」这类）
                if (SINGLE_STOP_CHARS.contains(String.valueOf(seg.charAt(i)))
                        && SINGLE_STOP_CHARS.contains(String.valueOf(seg.charAt(i + 1)))) {
                    continue;
                }
                features.add(bg);
            }
        }
        return features;
    }

    /**
     * 稳定的字符串哈希（md5 取前 4 字节）。
     *
     * <p>📌 **必须稳定**：同一段文本每次必须落到同一个桶，
     * 否则入库时算的向量和检索时算的向量对不上 → 相似度恒为 0。
     * → 绝不能用 {@code String.hashCode()} 之外的不稳定哈希，
     *   也不能用 {@code Object.hashCode()}（身份哈希）。
     */
    private int hash(String feature) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(feature.getBytes(StandardCharsets.UTF_8));
            return ((digest[0] & 0xFF) << 24)
                    | ((digest[1] & 0xFF) << 16)
                    | ((digest[2] & 0xFF) << 8)
                    | (digest[3] & 0xFF);
        } catch (NoSuchAlgorithmException e) {
            // MD5 永远存在
            log.error("MD5 不可用", e);
            return feature.hashCode();
        }
    }

    /**
     * L2 归一化
     */
    private List<Double> normalize(List<Double> vec) {
        double sumSq = 0.0;
        for (double v : vec) sumSq += v * v;
        double norm = Math.sqrt(sumSq);
        if (norm < 1e-12) {
            // 没有任何特征（全是停用词）→ 保持零向量
            // 📌 不能返回随机值 → 那会让「无意义查询」也拿到非零相似度
            return vec;
        }
        List<Double> out = new ArrayList<>(vec.size());
        for (double v : vec) out.add(v / norm);
        return out;
    }
}
