package com.crossborder.erp.customer.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.customer.entity.Customer;
import com.crossborder.erp.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户管理控制器
 */
@RestController
@RequestMapping("/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    /**
     * 创建客户
     */
    /**
     * 📌 <b>返回字符串而不是 Long</b>：主键是雪花 ID（19 位），JS Number 承载不了。
     */
    @PostMapping("/create")
    public String createCustomer(@RequestBody Customer customer) {
        Long id = customerService.createCustomer(customer);
        return id == null ? null : id.toString();
    }

    /**
     * 获取客户详情
     */
    @GetMapping("/{id}")
    public Customer getCustomer(@PathVariable Long id) {
        return customerService.getById(id);
    }

    /**
     * 分页查询客户
     */
    @GetMapping("/list")
    public IPage<Customer> pageCustomers(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String country) {
        Page<Customer> page = new Page<>(current, size);
        return customerService.pageCustomers(page, keyword, platform, level, country);
    }

    /**
     * 获取客户统计
     */
    @GetMapping("/stats")
    public CustomerService.CustomerStats getStats() {
        return customerService.getStats();
    }

    /**
     * 获取VIP客户列表
     */
    @GetMapping("/vip")
    public List<Customer> getVipCustomers() {
        return customerService.lambdaQuery()
            .eq(Customer::getLevel, "VIP")
            .orderByDesc(Customer::getTotalSpent)
            .list();
    }

    /**
     * 更新客户信息
     */
    @PutMapping("/{id}")
    public void updateCustomer(@PathVariable Long id, @RequestBody Customer customer) {
        customer.setId(id);
        customerService.updateById(customer);
    }

    /**
     * 删除客户
     */
    @DeleteMapping("/{id}")
    public void deleteCustomer(@PathVariable Long id) {
        customerService.removeById(id);
    }
}