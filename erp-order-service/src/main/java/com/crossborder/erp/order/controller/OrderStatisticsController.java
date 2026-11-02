package com.crossborder.erp.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crossborder.erp.common.result.Result;
import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.mapper.OrderMapper;
import com.crossborder.erp.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单统计Controller
 *
 * <p>⚠️ 这个 Controller 之前的方法**全是假实现**：
 * <ul>
 *   <li>{@code sumOrderAmount()} → {@code return BigDecimal.ZERO}（今日销售额恒为 0）</li>
 *   <li>{@code getPlatformDistribution()} → 写死 {@code {amazon:0, shopee:0}}</li>
 *   <li>{@code getStatusDistribution()} → 写死全 0</li>
 *   <li>{@code getSalesTrend()} → {@code // TODO} 桩，直接返回空列表</li>
 * </ul>
 * 单测全绿是因为压根没人测这些方法。现在全部改成真实聚合查询。</p>
 */
@RestController
@RequestMapping("/order/statistics")
@RequiredArgsConstructor
public class OrderStatisticsController {

    private final OrderService orderService;
    private final OrderMapper orderMapper;

    /**
     * 获取今日订单统计
     */
    @GetMapping("/today")
    public Result<Map<String, Object>> getTodayStatistics() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);

        Map<String, Object> stats = new HashMap<>();

        // 今日订单数
        long orderCount = countOrders(todayStart, todayEnd);
        stats.put("orderCount", orderCount);

        // 今日销售额（排除已取消订单）
        BigDecimal totalAmount = orderMapper.sumOrderAmount(todayStart, todayEnd);
        stats.put("totalAmount", totalAmount);

        // 各平台订单分布（真实 GROUP BY）
        stats.put("platformDistribution", getPlatformDistribution(todayStart, todayEnd));

        // 各状态订单数（真实 GROUP BY）
        stats.put("statusDistribution", getStatusDistribution(todayStart, todayEnd));

        return Result.success(stats);
    }

    /**
     * 获取最近 N 天销售趋势
     *
     * @param days 天数，默认 7
     */
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> getSalesTrend(
            @RequestParam(defaultValue = "7") int days) {
        // 防御：days 传 0 或负数会让时间区间非法，直接钳到 1~365
        int safeDays = Math.max(1, Math.min(days, 365));
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime start = end.minusDays(safeDays);

        List<Map<String, Object>> aggregated = orderMapper.aggregateByDay(start, end);

        // ⚠️ 补齐没有订单的日期：前端画趋势图需要连续 X 轴，
        //    缺一天就会在图上「跳一格」，看起来像数据丢了。
        Map<String, Map<String, Object>> byDate = new HashMap<>();
        for (Map<String, Object> row : aggregated) {
            Object dt = row.get("dt");
            if (dt != null) {
                byDate.put(dt.toString(), row);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(safeDays);
        for (int i = safeDays - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            Map<String, Object> row = byDate.get(date.toString());
            Map<String, Object> item = new HashMap<>();
            item.put("date", date.toString());
            if (row != null) {
                item.put("count", toLong(row.get("cnt")));
                item.put("amount", toBigDecimal(row.get("amount")));
            } else {
                item.put("count", 0L);
                item.put("amount", BigDecimal.ZERO);
            }
            result.add(item);
        }
        return Result.success(result);
    }

    /**
     * 统计订单数量
     */
    private long countOrders(LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Order::getCreateTime, startTime)
                .lt(Order::getCreateTime, endTime);
        return orderService.count(wrapper);
    }

    /**
     * 获取平台订单分布
     *
     * <p>真实 GROUP BY 结果。平台为空（null）的订单归到 {@code unknown}，
     * 不能直接丢 —— 丢了总数就对不上了。</p>
     */
    private Map<String, Long> getPlatformDistribution(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Long> distribution = new HashMap<>();
        for (Map<String, Object> row : orderMapper.countByPlatform(startTime, endTime)) {
            Object platform = row.get("platform");
            String key = platform == null ? "unknown" : platform.toString();
            distribution.merge(key, toLong(row.get("cnt")), Long::sum);
        }
        return distribution;
    }

    /**
     * 获取状态订单分布
     */
    private Map<String, Long> getStatusDistribution(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Long> distribution = new HashMap<>();
        for (Map<String, Object> row : orderMapper.countByStatus(startTime, endTime)) {
            Object status = row.get("status");
            String key = status == null ? "unknown" : status.toString();
            distribution.merge(key, toLong(row.get("cnt")), Long::sum);
        }
        return distribution;
    }

    /**
     * MyBatis 返回的 COUNT(*) 在不同驱动下可能是 Long / BigDecimal / Integer，
     * 这里统一成 long，避免下游拆箱 ClassCastException。
     */
    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
