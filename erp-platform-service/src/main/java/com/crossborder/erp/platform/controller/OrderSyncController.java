package com.crossborder.erp.platform.controller;

import com.crossborder.erp.common.result.Result;
import com.crossborder.erp.platform.schedule.OrderSyncSchedule;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 订单同步Controller
 */
@RestController
@RequestMapping("/platform/sync")
@RequiredArgsConstructor
public class OrderSyncController {

    private final OrderSyncSchedule orderSyncSchedule;

    /**
     * 手动触发订单同步
     */
    @PostMapping("/orders")
    public Result<Void> syncOrders(@RequestParam(required = false) String platform,
                                    @RequestParam(required = false) String shopId) {
        if (platform != null && shopId != null) {
            // 同步指定平台店铺
            orderSyncSchedule.syncPlatformOrders(platform, shopId);
        } else {
            // 同步所有平台
            orderSyncSchedule.syncOrders();
        }
        return Result.success();
    }
}
