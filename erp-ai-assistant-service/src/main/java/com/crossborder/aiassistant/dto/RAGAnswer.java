package com.crossborder.aiassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG 回答 DTO
 *
 * @author OmniTrade AI Team
 * @since 1.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RAGAnswer {
    private String query;
    private String answer;
    private List<RetrievalResult> retrievedDocs;
    private boolean hasContext;
    private long responseTime;

    /**
     * 📌 **这个回答是不是「模拟/降级」出来的**（2026-11-04 新增）。
     *
     * <p>⚠️ **为什么必须有这个字段**（规则 19）：
     * 旧实现 {@code answer()} 根本没调 LLM，只是把上下文**拼成一段提示词**就返回了
     * → 界面上显示的是「根据以下知识库内容回答用户问题：……」这种**给 LLM 的指令**，
     * 而 {@code hasContext=true} 又让人以为「AI 真的基于知识库回答了」→
     * <b>一个假的成功信号</b>。
     *
     * <p>→ 现在：真实调 LLM 失败或未配置时，{@code mock=true} + {@code mockReason} 有值，
     * 调用方/界面**能分辨真假**。
     */
    private Boolean mock = false;

    /**
     * 降级原因（{@code mock=true} 时有值）。
     */
    private String mockReason;
}
