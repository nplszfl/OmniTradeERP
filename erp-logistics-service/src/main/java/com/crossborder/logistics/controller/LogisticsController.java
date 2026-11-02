package com.crossborder.logistics.controller;

import com.crossborder.logistics.dto.LogisticsChannelVO;
import com.crossborder.logistics.dto.LogisticsRateVO;
import com.crossborder.logistics.dto.PageVO;
import com.crossborder.logistics.dto.ShippingRateQuery;
import com.crossborder.logistics.dto.ShippingRateResult;
import com.crossborder.logistics.service.LogisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物流控制器
 *
 * <p>⚠️ 响应形态：前端 erp-web/src/api/inventory-alert.ts 的 logisticsApi 是
 * {@code channels.value = await logisticsApi.getChannels()} —— 直接把返回值当数组用，
 * 所以这里<b>返回裸数组</b>，不包 Result（包了前端表格会渲染成空）。</p>
 *
 * <p>路由根路径是 {@code /logistics}，网关 {@code Path=/api/logistics/**} +
 * {@code StripPrefix=1}。</p>
 */
@Slf4j
@RestController
@RequestMapping("/logistics")
@RequiredArgsConstructor
public class LogisticsController {

    private final LogisticsService logisticsService;

    /**
     * 物流渠道列表
     * GET /logistics/channels
     */
    @GetMapping("/channels")
    public List<LogisticsChannelVO> channels() {
        return logisticsService.listChannels();
    }

    /**
     * 运费计算
     * POST /logistics/calculate
     */
    @PostMapping("/calculate")
    public List<ShippingRateResult> calculate(@RequestBody ShippingRateQuery query) {
        return logisticsService.calculateRates(query);
    }

    /**
     * 渠道推荐
     * POST /logistics/recommend
     */
    @PostMapping("/recommend")
    public ShippingRateResult recommend(@RequestBody ShippingRateQuery query) {
        return logisticsService.recommendChannel(query);
    }

    /**
     * 费率列表
     * GET /logistics/rates
     *
     * <p>返回裸分页对象 {@code {current,size,total,records}}，
     * 前端 {@code res.records} / {@code res.total} 直接可用。</p>
     */
    @GetMapping("/rates")
    public PageVO<LogisticsRateVO> rates(
            @RequestParam(required = false) String channelCode,
            @RequestParam(required = false) String fromCountry,
            @RequestParam(required = false) String toCountry,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<LogisticsRateVO> records = logisticsService.listRates(
                channelCode, fromCountry, toCountry, page, size);
        long total = logisticsService.countRates(channelCode, fromCountry, toCountry);
        return PageVO.of(page, size, total, records);
    }

    /**
     * 费率总数
     * GET /logistics/rates/count
     */
    @GetMapping("/rates/count")
    public long ratesCount(
            @RequestParam(required = false) String channelCode,
            @RequestParam(required = false) String fromCountry,
            @RequestParam(required = false) String toCountry) {
        return logisticsService.countRates(channelCode, fromCountry, toCountry);
    }

    /**
     * 新增费率
     * POST /logistics/rate
     */
    @PostMapping("/rate")
    public Long createRate(@RequestBody LogisticsRateVO rate) {
        return logisticsService.createRate(rate);
    }

    /**
     * 更新费率
     * PUT /logistics/rate/{id}
     */
    @PutMapping("/rate/{id}")
    public void updateRate(@PathVariable Long id, @RequestBody LogisticsRateVO rate) {
        logisticsService.updateRate(id, rate);
    }

    /**
     * 删除费率
     * DELETE /logistics/rate/{id}
     */
    @DeleteMapping("/rate/{id}")
    public void deleteRate(@PathVariable Long id) {
        logisticsService.deleteRate(id);
    }

    /**
     * 健康检查
     * GET /logistics/health
     */
    @GetMapping("/health")
    public String health() {
        return "物流服务运行中！🔥";
    }
}
