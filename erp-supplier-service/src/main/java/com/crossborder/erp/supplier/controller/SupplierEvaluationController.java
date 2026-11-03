package com.crossborder.erp.supplier.controller;

import com.crossborder.erp.supplier.entity.SupplierEvaluation;
import com.crossborder.erp.supplier.service.SupplierEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 供应商评估管理控制器
 */
@RestController
@RequestMapping("/supplier/evaluation")
@RequiredArgsConstructor
public class SupplierEvaluationController {

    private final SupplierEvaluationService evaluationService;

    /**
     * 创建评估记录
     */
    /**
     * 📌 <b>返回字符串而不是 Long</b>：主键是雪花 ID（19 位），JS Number 承载不了。
     */
    @PostMapping("/create")
    public String create(@RequestBody SupplierEvaluation evaluation) {
        Long id = evaluationService.createEvaluation(evaluation);
        return id == null ? null : id.toString();
    }

    /**
     * 获取评估记录详情
     */
    @GetMapping("/{id}")
    public SupplierEvaluation get(@PathVariable Long id) {
        return evaluationService.getById(id);
    }

    /**
     * 获取供应商的评估历史
     */
    @GetMapping("/supplier/{supplierId}")
    public List<SupplierEvaluation> getBySupplierId(@PathVariable Long supplierId) {
        return evaluationService.getBySupplierId(supplierId);
    }

    /**
     * 获取供应商最近一次评估
     */
    @GetMapping("/supplier/{supplierId}/latest")
    public SupplierEvaluation getLatest(@PathVariable Long supplierId) {
        return evaluationService.getLatestEvaluation(supplierId);
    }

    /**
     * 分页查询评估记录
     */
    @GetMapping("/list")
    public List<SupplierEvaluation> list(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String conclusion) {
        return evaluationService.listEvaluations(supplierId, conclusion);
    }

    /**
     * 删除评估记录
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        evaluationService.deleteEvaluation(id);
    }
}