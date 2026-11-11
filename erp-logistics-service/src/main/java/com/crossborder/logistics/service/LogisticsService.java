package com.crossborder.logistics.service;

import com.crossborder.logistics.dto.LogisticsChannelVO;
import com.crossborder.logistics.dto.LogisticsRateVO;
import com.crossborder.logistics.dto.ShippingRateQuery;
import com.crossborder.logistics.dto.ShippingRateResult;

import java.util.List;

/**
 * 物流服务
 *
 * <p>前端 erp-web/src/views/logistics/Logistics.vue + api/inventory-alert.ts 的
 * logisticsApi 对应实现。</p>
 */
public interface LogisticsService {

    /**
     * 查询物流渠道列表
     */
    List<LogisticsChannelVO> listChannels();

    /**
     * 分页查询费率
     *
     * @param channelCode  渠道编码，可空
     * @param fromCountry  起运国，可空
     * @param toCountry    目的国，可空
     * @param page         页码
     * @param size         每页条数
     */
    List<LogisticsRateVO> listRates(String channelCode, String fromCountry, String toCountry,
                                     int page, int size);

    /**
     * 费率总数
     */
    long countRates(String channelCode, String fromCountry, String toCountry);

    /**
     * 新增物流渠道
     *
     * <p>⚠️ 前端「添加渠道」按钮原来只弹「渠道添加成功」，**后端根本没有这个接口** →
     * 纯假成功（规则 80 / 82）。本方法是那条路径的**真实落库端点**。</p>
     *
     * @return 新增渠道ID
     */
    Long createChannel(LogisticsChannelVO channel);

    /**
     * 新增费率
     *
     * @return 新增费率ID
     */
    Long createRate(LogisticsRateVO rate);

    /**
     * 更新费率
     */
    void updateRate(Long id, LogisticsRateVO rate);

    /**
     * 删除费率
     */
    void deleteRate(Long id);

    /**
     * 计算运费（返回所有匹配渠道的报价）
     *
     * @param query 查询条件
     * @return 各渠道报价，按总价升序
     */
    List<ShippingRateResult> calculateRates(ShippingRateQuery query);

    /**
     * 推荐渠道（价格 / 时效综合最优）
     *
     * @param query 查询条件
     * @return 推荐结果
     */
    ShippingRateResult recommendChannel(ShippingRateQuery query);
}
