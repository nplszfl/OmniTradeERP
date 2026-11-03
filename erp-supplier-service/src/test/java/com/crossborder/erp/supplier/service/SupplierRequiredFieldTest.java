package com.crossborder.erp.supplier.service;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.supplier.entity.Supplier;
import com.crossborder.erp.supplier.entity.SupplierEvaluation;
import com.crossborder.erp.supplier.mapper.SupplierEvaluationMapper;
import com.crossborder.erp.supplier.mapper.SupplierMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 🔴 <b>NOT NULL 无默认值 → 插入必失败的回归测试</b>（supplier 模块）。
 *
 * <p><b>缺陷本体</b>：{@code t_supplier.name} /
 * {@code t_supplier_evaluation.supplier_id} 都是 {@code NOT NULL} 且<b>无默认值</b>，
 * 而 MyBatis-Plus 的 insert 默认 {@code FieldStrategy.NOT_NULL}
 * 会<b>跳过 null 字段</b> → INSERT 语句里根本没有这一列 →
 * MySQL 报 {@code Field 'name' doesn't have a default value}
 * → <b>整个插入失败</b>，错误信息还是<b>原始 SQL 栈</b>直接泄露给前端。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("供应商模块：NOT NULL 无默认值列的必填校验")
class SupplierRequiredFieldTest {

    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private SupplierEvaluationMapper evaluationMapper;
    @Mock
    private SupplierService supplierService;

    private SupplierService service;
    private SupplierEvaluationService evaluationSvc;

    @BeforeEach
    void setUp() {
        service = new SupplierService();
        ReflectionTestUtils.setField(service, "baseMapper", supplierMapper);

        evaluationSvc = new SupplierEvaluationService(supplierService);
        ReflectionTestUtils.setField(evaluationSvc, "baseMapper", evaluationMapper);
    }

    // ─────────── 供应商 ───────────

    @Test
    @DisplayName("🔴 供应商名为空 → 报业务异常（不是 SQL 500）")
    void supplierNameRequired() {
        Supplier supplier = new Supplier();
        // 📌 刻意不设 name（t_supplier.name NOT NULL 无默认值）

        assertThatThrownBy(() -> service.createSupplier(supplier))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("供应商名称不能为空");

        verify(supplierMapper, never()).insert(any());
    }

    @Test
    @DisplayName("🔴 供应商名为空白串 → 同样拦截")
    void supplierNameBlankRejected() {
        Supplier supplier = new Supplier();
        supplier.setName("  ");

        assertThatThrownBy(() -> service.createSupplier(supplier))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("供应商名称不能为空");

        verify(supplierMapper, never()).insert(any());
    }

    @Test
    @DisplayName("✅ 供应商名正常 → 正常插入")
    void supplierWithNameOk() {
        Supplier supplier = new Supplier();
        supplier.setName("深圳华强电子");

        service.createSupplier(supplier);

        verify(supplierMapper).insert(any(Supplier.class));
    }

    // ─────────── 供应商评估 ───────────

    @Test
    @DisplayName("🔴 评估缺供应商 → 报业务异常")
    void evaluationSupplierRequired() {
        SupplierEvaluation evaluation = new SupplierEvaluation();
        // 📌 刻意不设 supplierId（t_supplier_evaluation.supplier_id NOT NULL 无默认值）

        assertThatThrownBy(() -> evaluationSvc.createEvaluation(evaluation))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("供应商不能为空");

        verify(evaluationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("✅ 评估字段齐全 → 正常插入")
    void evaluationOk() {
        SupplierEvaluation evaluation = new SupplierEvaluation();
        evaluation.setSupplierId(2001L);
        evaluation.setQualityScore(5);

        evaluationSvc.createEvaluation(evaluation);

        verify(evaluationMapper).insert(any(SupplierEvaluation.class));
    }
}
