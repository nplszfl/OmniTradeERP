package com.crossborder.recommend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.crossborder.recommend.dto.RecommendRequest;
import com.crossborder.recommend.dto.RecommendResponse;
import com.crossborder.recommend.entity.RecommendCandidate;
import com.crossborder.recommend.entity.RecommendResult;
import com.crossborder.recommend.repository.RecommendCandidateRepository;
import com.crossborder.recommend.repository.RecommendResultRepository;
import com.crossborder.recommend.service.ProductRecommendationService;
import com.crossborder.recommend.vo.RecommendVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 选品推荐 —— 前端契约适配层
 *
 * <p>⚠️ 之前这个服务只有 {@code POST /api/v1/recommend/run} 和
 * {@code GET /api/v1/recommend/results}，而前端调的是：</p>
 * <pre>
 *   GET  /api/v1/recommend            列表
 *   GET  /api/v1/recommend/{sku}      详情
 *   POST /api/v1/recommend/refresh    刷新（重新跑一遍推荐）
 * </pre>
 * 路径全对不上 → 前端一进去就 404。这层就是把这三个路径补上。
 *
 * <p>⚠️ 返回<b>裸数组</b>：前端是 {@code list.value = await recommendApi.list({})}，
 * 之后直接 {@code :data="list"}，包 Result 的话 el-table 渲染不出行。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/recommend")
@RequiredArgsConstructor
@Tag(name = "Product Recommendation (前端契约)", description = "选品推荐前端契约适配")
public class RecommendFrontendController {

    private final ProductRecommendationService recommendationService;
    private final RecommendResultRepository resultRepository;
    private final RecommendCandidateRepository candidateRepository;

    /**
     * 推荐结果列表（前端：recommendApi.list）
     */
    @GetMapping
    @Operation(summary = "推荐结果列表")
    public List<RecommendVO> list(@RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit,
                                   @RequestParam(value = "platform", required = false) String platform,
                                   @RequestParam(value = "category", required = false) String category) {
        QueryWrapper<RecommendResult> q = new QueryWrapper<>();
        q.orderByDesc("score").last("LIMIT " + (limit == null ? 50 : Math.max(1, limit)));
        List<RecommendResult> results = resultRepository.selectList(q);
        return enrich(results);
    }

    /**
     * 单个推荐详情（前端：recommendApi.detail）
     */
    @GetMapping("/{sku}")
    @Operation(summary = "推荐详情")
    public RecommendVO detail(@PathVariable String sku) {
        QueryWrapper<RecommendResult> q = new QueryWrapper<>();
        q.eq("sku", sku).orderByDesc("score").last("LIMIT 1");
        List<RecommendResult> results = resultRepository.selectList(q);
        if (results.isEmpty()) {
            return null;
        }
        List<RecommendVO> voList = enrich(results);
        return voList.isEmpty() ? null : voList.get(0);
    }

    /**
     * 刷新推荐（前端：recommendApi.refresh）—— 重新跑一遍推荐并落库
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新推荐结果")
    public Map<String, Object> refresh(@RequestBody(required = false) RecommendRequest request) {
        RecommendRequest req = request == null ? new RecommendRequest() : request;
        req.setRegenerate(true);
        RecommendResponse response = recommendationService.recommend(req);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "推荐结果已刷新");
        result.put("data", response);
        return result;
    }

    /**
     * 用候选商品表补齐 productName / category / expectedMargin / reason
     */
    private List<RecommendVO> enrich(List<RecommendResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<String> skus = results.stream()
                .map(RecommendResult::getSku)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();

        Map<String, RecommendCandidate> candidateMap;
        if (!skus.isEmpty()) {
            QueryWrapper<RecommendCandidate> cq = new QueryWrapper<>();
            cq.in("sku", skus);
            candidateMap = candidateRepository.selectList(cq).stream()
                    .collect(Collectors.toMap(RecommendCandidate::getSku, Function.identity(), (a, b) -> a));
        } else {
            candidateMap = Map.of();
        }

        return results.stream().map(r -> {
            RecommendVO vo = new RecommendVO();
            vo.setId(r.getId());
            vo.setSku(r.getSku());
            vo.setScore(r.getScore());
            vo.setRecommendLevel(r.getRecommendLevel());
            vo.setExpectedMonthlyProfit(r.getExpectedMonthlyProfit());
            vo.setRiskScore(r.getRiskScore());
            vo.setAdoptionStatus(r.getAdoptionStatus());
            vo.setCreatedAt(r.getCreatedAt());

            RecommendCandidate c = r.getSku() == null ? null : candidateMap.get(r.getSku());
            if (c != null) {
                vo.setProductName(c.getTitle());
                vo.setCategory(c.getCategory());
                // 毛利率就是「预期利润率」，前端按百分比展示
                vo.setExpectedMargin(c.getGrossMargin() == null ? null : c.getGrossMargin() * 100);
                vo.setReason(c.getRemark() == null || c.getRemark().isBlank()
                        ? joinStrengths(r.getKeyStrengths())
                        : c.getRemark());
            } else {
                vo.setExpectedMargin(null);
                vo.setReason(joinStrengths(r.getKeyStrengths()));
            }
            return vo;
        }).toList();
    }

    /**
     * keyStrengths 存的是「用 | 分隔的字符串」，不是 List
     */
    private static String joinStrengths(String keyStrengths) {
        if (keyStrengths == null || keyStrengths.isBlank()) {
            return null;
        }
        return String.join("；", keyStrengths.split("\\|"));
    }
}
