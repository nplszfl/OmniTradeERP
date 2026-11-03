package com.crossborder.erp.customer.controller;

import com.crossborder.erp.customer.entity.CustomerTag;
import com.crossborder.erp.customer.service.CustomerTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户标签管理控制器
 */
@RestController
@RequestMapping("/customer/tag")
@RequiredArgsConstructor
public class CustomerTagController {

    private final CustomerTagService customerTagService;

    /**
     * 创建标签
     */
    /**
     * 📌 <b>返回字符串而不是 Long</b>：主键是雪花 ID（19 位），JS Number 承载不了。
     */
    @PostMapping("/create")
    public String create(@RequestBody CustomerTag tag) {
        Long id = customerTagService.createTag(tag);
        return id == null ? null : id.toString();
    }

    /**
     * 更新标签
     */
    @PutMapping("/{id}")
    public void update(@PathVariable Long id, @RequestBody CustomerTag tag) {
        tag.setId(id);
        customerTagService.updateTag(tag);
    }

    /**
     * 获取标签详情
     */
    @GetMapping("/{id}")
    public CustomerTag get(@PathVariable Long id) {
        return customerTagService.getById(id);
    }

    /**
     * 获取所有启用的标签
     */
    @GetMapping("/list")
    public List<CustomerTag> list(@RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.isEmpty()) {
            return customerTagService.listTags(keyword);
        }
        return customerTagService.getActiveTags();
    }

    /**
     * 删除标签
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        customerTagService.deleteTag(id);
    }

    /**
     * 获取客户的所有标签
     */
    @GetMapping("/customer/{customerId}")
    public List<CustomerTag> getCustomerTags(@PathVariable Long customerId) {
        return customerTagService.getTagsByCustomerId(customerId);
    }

    /**
     * 给客户添加标签
     */
    @PostMapping("/customer/{customerId}/add")
    public void addTagToCustomer(
            @PathVariable Long customerId,
            @RequestParam Long tagId) {
        customerTagService.addTagToCustomer(customerId, tagId);
    }

    /**
     * 批量添加标签
     */
    @PostMapping("/customer/{customerId}/batch-add")
    public void batchAddTags(
            @PathVariable Long customerId,
            @RequestBody List<Long> tagIds) {
        customerTagService.addTagsToCustomer(customerId, tagIds);
    }

    /**
     * 移除客户标签
     */
    @DeleteMapping("/customer/{customerId}/tag/{tagId}")
    public void removeTag(
            @PathVariable Long customerId,
            @PathVariable Long tagId) {
        customerTagService.removeTagFromCustomer(customerId, tagId);
    }

    /**
     * 清除客户所有标签
     */
    @DeleteMapping("/customer/{customerId}/clear")
    public void clearCustomerTags(@PathVariable Long customerId) {
        customerTagService.clearCustomerTags(customerId);
    }
}