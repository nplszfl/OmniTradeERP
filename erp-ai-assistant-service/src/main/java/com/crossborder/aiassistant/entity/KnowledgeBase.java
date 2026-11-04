package com.crossborder.aiassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库条目（问答对）—— 落库实体
 *
 * <p>对应表 {@code knowledge_base}（schema {@code erp_ai_assistant_db}）。
 *
 * <p>📌 为什么要有这个实体：
 *    原来的 {@code AIAssistantServiceImpl} 用的是
 *    {@code private final List<Knowledge> knowledgeBase = new ArrayList<>()}
 *    —— 注释直接写着「模拟知识库」。
 *    后果是：
 *    <ul>
 *      <li>数据库里 {@code knowledge_base} 表的种子数据**从来没人读**；</li>
 *      <li>通过接口新增的知识**重启就没了**；</li>
 *      <li>前端根本没有管理界面 → 知识库永远只有代码里硬编码的 4 条。</li>
 *    </ul>
 *    现在聊天检索和前端管理都读这张表，知识库才真正是「持久化 + 可管理」的。
 *
 * @since 1.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "knowledge_base", indexes = {
        @Index(name = "idx_kb_category", columnList = "category"),
        @Index(name = "idx_kb_language", columnList = "language"),
        @Index(name = "idx_kb_active", columnList = "active")
})
public class KnowledgeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 知识分类：订单/物流/产品/退货… */
    @Column(nullable = false, length = 50)
    private String category;

    /** 问题 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    /** 答案 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    /** 语言代码，默认 zh */
    @Column(length = 10)
    private String language;

    /** 标签（数据库是 JSON 列，用转换器存 List） */
    @Convert(converter = com.crossborder.aiassistant.converter.StringListJsonConverter.class)
    @Column(columnDefinition = "json")
    private List<String> tags;

    /** 优先级，数值大的优先命中 */
    private Integer priority;

    /** 查看次数 */
    private Integer viewCount;

    /** 「有帮助」次数 */
    private Integer helpfulCount;

    /** 是否启用 */
    private Boolean active;

    @CreationTimestamp
    @Column(name = "create_time")
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
