package com.crossborder.aiassistant.service;

import java.util.List;

/**
 * 文本嵌入服务
 * <p>
 * 负责把文本转成定长浮点向量，用于后续相似度计算。
 *
 * <h3>📌 当前实现（v1.8.0）：字符 n-gram 特征哈希</h3>
 * <ul>
 *   <li>中文取相邻两字 bigram，拉丁文按空格切词</li>
 *   <li>滤掉高频虚词 bigram（如「的是」「我的」）</li>
 *   <li>md5 哈希到桶 + signed hashing，TF 权重 = 1 + log(count)</li>
 *   <li>1024 维、L2 归一化</li>
 * </ul>
 * 保证：
 * <ul>
 *   <li>相同输入 → 完全相同输出（测试可重现）</li>
 *   <li><b>共享字符片段的文本 → 相似度显著升高</b>（有排序能力）</li>
 *   <li><b>局限</b>：这是<b>词形匹配</b>，不是<b>语义匹配</b> ——
 *       「退货」vs「退款」字面不同 → 需要同义词表补（见 {@code DomainSynonyms}）</li>
 * </ul>
 *
 * <p>⚠️ 真实语义 embedding API 接入时直接换实现，接口契约不变。
 * 但在那之前，<b>不要把它叫「向量语义检索」</b> ——
 * 它是「字符 n-gram 特征哈希检索」。
 *
 * @author OmniTrade AI Team
 * @since 1.8.0
 */
public interface EmbeddingService {

    /**
     * 把单段文本编码为向量
     */
    List<Double> embed(String text);

    /**
     * 批量编码
     */
    List<List<Double>> embedBatch(List<String> texts);

    /**
     * 向量维度
     */
    int getDimension();
}
