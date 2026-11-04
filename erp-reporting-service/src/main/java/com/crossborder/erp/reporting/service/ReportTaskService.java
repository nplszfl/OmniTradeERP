package com.crossborder.erp.reporting.service;

import com.crossborder.erp.reporting.dto.ReportGenerateRequest;
import com.crossborder.erp.reporting.dto.ReportListItemDTO;

import java.util.List;
import java.util.Map;

/**
 * 报表任务服务
 *
 * <p>⚠️ 这个服务之前不存在：前端 erp-web/src/views/report/index.vue 调用
 * {@code GET /report/list}、{@code POST /report/generate}、{@code GET /report/templates}
 * 三个接口，后端一个都没有 → 页面永远走 catch 分支的 mock 数据，
 * 看起来「有数据」，实际是前端写死的假数据。</p>
 *
 * <p>后端原本只有「按类型算报表」（/report/sales、/report/inventory、/report/finance），
 * 没有「报表台账」—— 生成过哪些报表、什么时候生成的，查询不到。</p>
 */
public interface ReportTaskService {

    /**
     * 报表台账：汇总已生成的销售/库存/财务报表
     */
    List<ReportListItemDTO> list();

    /**
     * 提交一次报表生成
     *
     * @return 生成结果摘要
     */
    ReportListItemDTO generate(ReportGenerateRequest request);

    /**
     * 可用的报表模板
     */
    List<Map<String, Object>> templates();

    /**
     * 删除一条报表台账记录
     *
     * <p>📌 前端 erp-web/src/views/report/index.vue 的「删除」按钮
     * 原来**只改本地数组**（{@code list.value = list.value.filter(...)}）
     * 然后弹「已删除」→ **后端数据一条没动** → 刷新页面又回来了。
     * → 这就是「界面在骗人」（规则 51 / 72）。</p>
     *
     * @param type 报表类型：sales / inventory / finance
     * @param id   报表 ID
     * @throws IllegalArgumentException 类型不合法
     */
    void delete(String type, Long id);
}
