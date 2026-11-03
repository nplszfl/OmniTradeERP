package com.crossborder.erp.customer.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.customer.entity.CustomerFollowRecord;
import com.crossborder.erp.customer.service.CustomerFollowRecordService;
import lombok.RequiredArgsConstructor;
import com.crossborder.erp.common.result.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户跟进记录控制器
 */
@RestController
@RequestMapping("/customer/follow")
@RequiredArgsConstructor
public class CustomerFollowRecordController {

    private final CustomerFollowRecordService followRecordService;

    /**
     * 创建跟进记录
     */
    /**
     * 📌 <b>返回字符串而不是 Long</b>：主键是雪花 ID（19 位），JS Number 承载不了。
     */
    @PostMapping("/create")
    public Result<String> create(@RequestBody CustomerFollowRecord record) {
        Long id = followRecordService.createRecord(record);
        return Result.successId(id);
    }

    /**
     * 更新跟进记录
     */
    @PutMapping("/{id}")
    public void update(@PathVariable Long id, @RequestBody CustomerFollowRecord record) {
        record.setId(id);
        followRecordService.updateRecord(record);
    }

    /**
     * 获取跟进记录详情
     */
    @GetMapping("/{id}")
    public CustomerFollowRecord get(@PathVariable Long id) {
        return followRecordService.getById(id);
    }

    /**
     * 获取客户的所有跟进记录
     */
    @GetMapping("/customer/{customerId}")
    public List<CustomerFollowRecord> getByCustomerId(@PathVariable Long customerId) {
        return followRecordService.getByCustomerId(customerId);
    }

    /**
     * 获取客户最近一次跟进
     */
    @GetMapping("/customer/{customerId}/latest")
    public CustomerFollowRecord getLatest(@PathVariable Long customerId) {
        return followRecordService.getLatestRecord(customerId);
    }

    /**
     * 分页查询跟进记录
     */
    @GetMapping("/list")
    public IPage<CustomerFollowRecord> page(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String followType,
            @RequestParam(required = false) String result) {
        Page<CustomerFollowRecord> page = new Page<>(current, size);
        return followRecordService.pageRecords(page, customerId, followType, result);
    }

    /**
     * 获取今日待跟进列表
     */
    @GetMapping("/pending/today")
    public List<CustomerFollowRecord> getTodayPending() {
        return followRecordService.getTodayPending();
    }

    /**
     * 标记跟进完成
     */
    @PostMapping("/{id}/complete")
    public void complete(
            @PathVariable Long id,
            @RequestParam String result,
            @RequestParam(required = false) String remark) {
        followRecordService.markAsCompleted(id, result, remark);
    }

    /**
     * 删除跟进记录
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        followRecordService.deleteRecord(id);
    }

    /**
     * 统计客户跟进次数
     */
    @GetMapping("/customer/{customerId}/count")
    public long count(@PathVariable Long customerId) {
        return followRecordService.countByCustomerId(customerId);
    }
}