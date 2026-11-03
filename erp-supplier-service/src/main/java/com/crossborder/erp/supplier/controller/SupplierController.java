package com.crossborder.erp.supplier.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.supplier.entity.Supplier;
import com.crossborder.erp.supplier.service.SupplierService;
import lombok.RequiredArgsConstructor;
import com.crossborder.erp.common.result.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 供应商管理控制器
 */
@RestController
@RequestMapping("/supplier")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    /**
     * 创建供应商
     */
    /**
     * 📌 <b>返回字符串而不是 Long</b>：主键是雪花 ID（19 位），
     * JS Number 承载不了 → 前端回传时 id 被截断 → 匹配不到行 → 静默失败。
     */
    @PostMapping("/create")
    public Result<String> createSupplier(@RequestBody Supplier supplier) {
        Long id = supplierService.createSupplier(supplier);
        return Result.successId(id);
    }

    /**
     * 获取供应商详情
     */
    @GetMapping("/{id}")
    public Supplier getSupplier(@PathVariable Long id) {
        return supplierService.getById(id);
    }

    /**
     * 分页查询供应商
     */
    @GetMapping("/list")
    public IPage<Supplier> pageSuppliers(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String country) {
        Page<Supplier> page = new Page<>(current, size);
        return supplierService.pageSuppliers(page, keyword, type, level, country);
    }

    /**
     * 获取A级供应商
     */
    @GetMapping("/top")
    public List<Supplier> getTopSuppliers() {
        return supplierService.getTopSuppliers();
    }

    /**
     * 获取供应商统计
     */
    @GetMapping("/stats")
    public SupplierService.SupplierStats getStats() {
        return supplierService.getStats();
    }

    /**
     * 更新供应商
     */
    @PutMapping("/{id}")
    public void updateSupplier(@PathVariable Long id, @RequestBody Supplier supplier) {
        supplier.setId(id);
        supplierService.updateById(supplier);
    }

    /**
     * 删除供应商
     */
    @DeleteMapping("/{id}")
    public void deleteSupplier(@PathVariable Long id) {
        supplierService.removeById(id);
    }
}