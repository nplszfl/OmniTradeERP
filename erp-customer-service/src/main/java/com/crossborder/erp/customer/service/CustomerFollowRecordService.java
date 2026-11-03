package com.crossborder.erp.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.customer.entity.CustomerFollowRecord;
import com.crossborder.erp.customer.mapper.CustomerFollowRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户服务 - 客户跟进记录管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerFollowRecordService extends ServiceImpl<CustomerFollowRecordMapper, CustomerFollowRecord> {

    /**
     * 创建跟进记录
     */
    public Long createRecord(CustomerFollowRecord record) {
        // 🔴 必填校验：`t_customer_follow_record.customer_id` 是 NOT NULL 且**无默认值**，
        //    MyBatis-Plus 的 insert 会**跳过 null 字段** → INSERT 里没有这一列
        //    → MySQL 报 "Field 'customer_id' doesn't have a default value" → 插入失败，
        //    错误信息还会**泄露原始 SQL 栈**给前端。
        if (record == null) {
            throw new BizException("跟进记录不能为空");
        }
        if (record.getCustomerId() == null) {
            throw new BizException("客户不能为空");
        }
        if (record.getResult() == null) {
            record.setResult("PENDING");
        }
        if (record.getFollowType() == null) {
            record.setFollowType("OTHER");
        }
        save(record);
        log.info("创建客户跟进记录: customerId={}", record.getCustomerId());
        return record.getId();
    }

    /**
     * 更新跟进记录
     */
    public void updateRecord(CustomerFollowRecord record) {
        updateById(record);
        log.info("更新客户跟进记录: id={}", record.getId());
    }

    /**
     * 获取客户的跟进记录
     */
    public List<CustomerFollowRecord> getByCustomerId(Long customerId) {
        return lambdaQuery()
            .eq(CustomerFollowRecord::getCustomerId, customerId)
            .orderByDesc(CustomerFollowRecord::getCreateTime)
            .list();
    }

    /**
     * 分页查询跟进记录
     */
    public IPage<CustomerFollowRecord> pageRecords(Page<CustomerFollowRecord> page, 
                                                    Long customerId, 
                                                    String followType, 
                                                    String result) {
        LambdaQueryWrapper<CustomerFollowRecord> wrapper = new LambdaQueryWrapper<>();
        if (customerId != null) {
            wrapper.eq(CustomerFollowRecord::getCustomerId, customerId);
        }
        if (StringUtils.hasText(followType)) {
            wrapper.eq(CustomerFollowRecord::getFollowType, followType);
        }
        if (StringUtils.hasText(result)) {
            wrapper.eq(CustomerFollowRecord::getResult, result);
        }
        wrapper.orderByDesc(CustomerFollowRecord::getCreateTime);
        return page(page, wrapper);
    }

    /**
     * 获取待跟进记录（下次跟进时间在指定时间之前）
     */
    public List<CustomerFollowRecord> getPendingFollows(LocalDateTime before) {
        return lambdaQuery()
            .eq(CustomerFollowRecord::getResult, "PENDING")
            .lt(CustomerFollowRecord::getNextFollowTime, before)
            .orderByAsc(CustomerFollowRecord::getNextFollowTime)
            .list();
    }

    /**
     * 获取今日待跟进
     */
    public List<CustomerFollowRecord> getTodayPending() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endOfDay = now.toLocalDate().atTime(23, 59, 59);
        return getPendingFollows(endOfDay);
    }

    /**
     * 标记跟进完成
     */
    public void markAsCompleted(Long id, String result, String remark) {
        CustomerFollowRecord record = getById(id);
        if (record != null) {
            record.setResult(result);
            if (remark != null) {
                record.setRemark(remark);
            }
            updateById(record);
            log.info("标记跟进完成: id={}, result={}", id, result);
        }
    }

    /**
     * 删除跟进记录
     */
    public void deleteRecord(Long id) {
        removeById(id);
        log.info("删除客户跟进记录: id={}", id);
    }

    /**
     * 统计客户跟进次数
     */
    public long countByCustomerId(Long customerId) {
        return lambdaQuery()
            .eq(CustomerFollowRecord::getCustomerId, customerId)
            .count();
    }

    /**
     * 获取客户最近一次跟进
     */
    public CustomerFollowRecord getLatestRecord(Long customerId) {
        return lambdaQuery()
            .eq(CustomerFollowRecord::getCustomerId, customerId)
            .orderByDesc(CustomerFollowRecord::getCreateTime)
            .last("LIMIT 1")
            .one();
    }
}