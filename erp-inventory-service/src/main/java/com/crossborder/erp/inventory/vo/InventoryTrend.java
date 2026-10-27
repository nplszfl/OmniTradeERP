package com.crossborder.erp.inventory.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 库存趋势（按天）
 */
@Data
public class InventoryTrend {

    /** 日期 */
    private LocalDate date;

    /** 当日入库数量 */
    private Integer inboundQty;

    /** 当日出库数量 */
    private Integer outboundQty;

    /** 当日净变化数量 */
    private Integer netQty;
}
