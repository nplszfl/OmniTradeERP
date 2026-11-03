package com.crossborder.erp.customer.service;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.customer.entity.CustomerFollowRecord;
import com.crossborder.erp.customer.entity.CustomerGroup;
import com.crossborder.erp.customer.entity.CustomerTag;
import com.crossborder.erp.customer.mapper.CustomerFollowRecordMapper;
import com.crossborder.erp.customer.mapper.CustomerGroupMapper;
import com.crossborder.erp.customer.mapper.CustomerTagMapper;
import com.crossborder.erp.customer.mapper.CustomerTagRelationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 🔴 <b>NOT NULL 无默认值 → 插入必失败的回归测试</b>（customer 模块）。
 *
 * <p><b>缺陷本体</b>：{@code t_customer_group.name} /
 * {@code t_customer_tag.name} / {@code t_customer_follow_record.customer_id}
 * 都是 {@code NOT NULL} 且<b>无默认值</b>，
 * 而 MyBatis-Plus 的 insert 默认 {@code FieldStrategy.NOT_NULL}
 * 会<b>跳过 null 字段</b> → INSERT 语句里根本没有这一列 →
 * MySQL 报 {@code Field 'name' doesn't have a default value}
 * → <b>整个插入失败</b>，错误信息还是<b>原始 SQL 栈</b>直接泄露给前端。</p>
 *
 * <p>📌 这些 Service 是 {@code @RequestBody} 直传实体，客户端漏传字段就是 500。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("客户模块：NOT NULL 无默认值列的必填校验")
class CustomerRequiredFieldTest {

    @Mock
    private CustomerGroupMapper groupMapper;
    @Mock
    private CustomerTagMapper tagMapper;
    @Mock
    private CustomerTagRelationMapper tagRelationMapper;
    @Mock
    private CustomerFollowRecordMapper followRecordMapper;

    private CustomerGroupService groupService;
    private CustomerTagService tagService;
    private CustomerFollowRecordService followRecordService;

    @BeforeEach
    void setUp() {
        groupService = new CustomerGroupService();
        ReflectionTestUtils.setField(groupService, "baseMapper", groupMapper);

        tagService = new CustomerTagService(tagRelationMapper);
        ReflectionTestUtils.setField(tagService, "baseMapper", tagMapper);

        followRecordService = new CustomerFollowRecordService();
        ReflectionTestUtils.setField(followRecordService, "baseMapper", followRecordMapper);
    }

    // ─────────── 客户分组 ───────────

    @Test
    @DisplayName("🔴 分组名为空 → 报业务异常（不是 SQL 500）")
    void groupNameRequired() {
        CustomerGroup group = new CustomerGroup();
        // 📌 刻意不设 name（t_customer_group.name NOT NULL 无默认值）

        assertThatThrownBy(() -> groupService.createGroup(group))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("分组名称不能为空");

        verify(groupMapper, never()).insert(any());
    }

    @Test
    @DisplayName("🔴 分组名为空白串 → 同样拦截")
    void groupNameBlankRejected() {
        CustomerGroup group = new CustomerGroup();
        group.setName("   ");

        assertThatThrownBy(() -> groupService.createGroup(group))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("分组名称不能为空");

        verify(groupMapper, never()).insert(any());
    }

    @Test
    @DisplayName("✅ 分组名正常 → 正常插入")
    void groupWithNameOk() {
        CustomerGroup group = new CustomerGroup();
        group.setName("高价值客户");

        groupService.createGroup(group);

        verify(groupMapper).insert(any(CustomerGroup.class));
    }

    // ─────────── 客户标签 ───────────

    @Test
    @DisplayName("🔴 标签名为空 → 报业务异常")
    void tagNameRequired() {
        CustomerTag tag = new CustomerTag();

        assertThatThrownBy(() -> tagService.createTag(tag))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("标签名称不能为空");

        verify(tagMapper, never()).insert(any());
    }

    @Test
    @DisplayName("✅ 标签名正常 → 正常插入")
    void tagWithNameOk() {
        CustomerTag tag = new CustomerTag();
        tag.setName("意向客户");

        tagService.createTag(tag);

        verify(tagMapper).insert(any(CustomerTag.class));
    }

    // ─────────── 跟进记录 ───────────

    @Test
    @DisplayName("🔴 跟进记录缺客户 → 报业务异常")
    void followRecordCustomerRequired() {
        CustomerFollowRecord record = new CustomerFollowRecord();
        // 📌 刻意不设 customerId（t_customer_follow_record.customer_id NOT NULL 无默认值）

        assertThatThrownBy(() -> followRecordService.createRecord(record))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("客户不能为空");

        verify(followRecordMapper, never()).insert(any());
    }

    @Test
    @DisplayName("✅ 跟进记录字段齐全 → 正常插入")
    void followRecordOk() {
        CustomerFollowRecord record = new CustomerFollowRecord();
        record.setCustomerId(1001L);
        record.setFollowType("PHONE");

        followRecordService.createRecord(record);

        verify(followRecordMapper).insert(any(CustomerFollowRecord.class));
    }
}
