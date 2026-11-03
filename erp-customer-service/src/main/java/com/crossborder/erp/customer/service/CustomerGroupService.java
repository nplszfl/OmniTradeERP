package com.crossborder.erp.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.customer.entity.CustomerGroup;
import com.crossborder.erp.customer.mapper.CustomerGroupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 客户服务 - 客户分组管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerGroupService extends ServiceImpl<CustomerGroupMapper, CustomerGroup> {

    /**
     * 创建客户分组
     */
    public Long createGroup(CustomerGroup group) {
        // 🔴 必填校验：`t_customer_group.name` 是 NOT NULL 且**无默认值**，
        //    MyBatis-Plus 的 insert 默认 FieldStrategy.NOT_NULL 会**跳过 null 字段**
        //    → INSERT 里根本没有这一列 → MySQL 报
        //    "Field 'name' doesn't have a default value" → 整个插入失败，
        //    而且错误信息是**原始 SQL 栈**直接泄露给前端。
        if (group == null) {
            throw new BizException("客户分组不能为空");
        }
        if (group.getName() == null || group.getName().trim().isEmpty()) {
            throw new BizException("分组名称不能为空");
        }
        if (group.getStatus() == null) {
            group.setStatus("ACTIVE");
        }
        if (group.getSortOrder() == null) {
            group.setSortOrder(0);
        }
        save(group);
        log.info("创建客户分组: {}", group.getName());
        return group.getId();
    }

    /**
     * 更新客户分组
     */
    public void updateGroup(CustomerGroup group) {
        updateById(group);
        log.info("更新客户分组: {}", group.getName());
    }

    /**
     * 获取所有启用的分组
     */
    public List<CustomerGroup> getActiveGroups() {
        return lambdaQuery()
            .eq(CustomerGroup::getStatus, "ACTIVE")
            .orderByAsc(CustomerGroup::getSortOrder)
            .list();
    }

    /**
     * 根据名称查询分组
     */
    public CustomerGroup getByName(String name) {
        return lambdaQuery()
            .eq(CustomerGroup::getName, name)
            .one();
    }

    /**
     * 分页查询分组
     */
    public List<CustomerGroup> listGroups(String keyword) {
        LambdaQueryWrapper<CustomerGroup> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(CustomerGroup::getName, keyword)
                   .or().like(CustomerGroup::getDescription, keyword);
        }
        wrapper.orderByAsc(CustomerGroup::getSortOrder);
        return list(wrapper);
    }

    /**
     * 删除分组
     */
    public void deleteGroup(Long id) {
        removeById(id);
        log.info("删除客户分组: id={}", id);
    }
}