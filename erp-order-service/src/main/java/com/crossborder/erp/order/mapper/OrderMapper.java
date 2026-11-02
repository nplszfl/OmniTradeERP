package com.crossborder.erp.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crossborder.erp.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订单Mapper
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 统计时间区间内的订单总金额
     *
     * <p>注意排除已取消订单 —— 取消的订单不该计入销售额。
     * 之前这个方法在 Controller 里是 {@code return BigDecimal.ZERO} 的假实现，
     * 前端「今日销售额」永远显示 0。</p>
     */
    @Select("""
            SELECT COALESCE(SUM(order_amount), 0)
            FROM t_order
            WHERE deleted = 0
              AND create_time >= #{start}
              AND create_time < #{end}
              AND status != 'CANCELLED'
            """)
    BigDecimal sumOrderAmount(@Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);

    /**
     * 按平台统计订单数
     *
     * <p>用 GROUP BY 拿真实分布，之前是写死 {@code {amazon:0, shopee:0}}。</p>
     */
    @Select("""
            SELECT platform, COUNT(*) AS cnt
            FROM t_order
            WHERE deleted = 0
              AND create_time >= #{start}
              AND create_time < #{end}
            GROUP BY platform
            """)
    List<Map<String, Object>> countByPlatform(@Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end);

    /**
     * 按状态统计订单数
     *
     * <p>用 GROUP BY 拿真实分布，之前是写死的全 0 假数据。</p>
     */
    @Select("""
            SELECT status, COUNT(*) AS cnt
            FROM t_order
            WHERE deleted = 0
              AND create_time >= #{start}
              AND create_time < #{end}
            GROUP BY status
            """)
    List<Map<String, Object>> countByStatus(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    /**
     * 按天聚合销售趋势
     *
     * <p>返回 date / cnt / amount 三列，按日期升序。
     * 之前 {@code GET /order/statistics/trend} 是 {@code // TODO} 桩，
     * 直接返回空列表 —— 前端拿不到任何趋势数据。</p>
     */
    @Select("""
            SELECT DATE(create_time) AS dt,
                   COUNT(*) AS cnt,
                   COALESCE(SUM(order_amount), 0) AS amount
            FROM t_order
            WHERE deleted = 0
              AND create_time >= #{start}
              AND create_time < #{end}
              AND status != 'CANCELLED'
            GROUP BY DATE(create_time)
            ORDER BY dt ASC
            """)
    List<Map<String, Object>> aggregateByDay(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);
}
