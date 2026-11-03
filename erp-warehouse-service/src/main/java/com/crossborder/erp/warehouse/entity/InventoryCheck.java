package com.crossborder.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 库存盘点单（主表）
 *
 * <p>🔴 这个表**以前根本不存在** —— 盘点流程只有 {@code wms_inventory_check_result}
 *（结果明细），没有承载「这是一次盘点」的��表，结果是：</p>
 * <ul>
 *   <li>{@code createInventoryCheck(...)} 只是一行 {@code log.info}，**什么都没做**
 *       却返回「成功」→ 前端点「创建盘点单」，接口 200，库里什么都没有</li>
 *   <li>{@code confirmInventoryCheck(checkId, results)} 的 checkId 是前端随便传的，
 *       没有主表校验 → 传 0 也能「确认」</li>
 * </ul>
 *
 * <p>盘点状态机：{@code CREATED → IN_PROGRESS → CONFIRMED / CANCELLED}</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("wms_inventory_check")
public class InventoryCheck {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 盘点单号 */
    private String checkNo;

    private Long warehouseId;

    /** 盘点类型 FULL-全盘 / CYCLE-循环盘点 / SPOT-抽盘 */
    private String checkType;

    /** 盘点状态 CREATED/IN_PROGRESS/CONFIRMED/CANCELLED */
    private String checkStatus;

    /** 库位数 */
    private Integer locationCount;

    /** 商品 SKU 数 */
    private Integer skuCount;

    /** 盘点明细行数 */
    private Integer totalQuantity;

    /** 差异行数（盘盈 + 盘亏） */
    private Integer differenceCount;

    /** 盘盈数量合计 */
    private Integer profitQuantity;

    /** 盘亏数量合计 */
    private Integer lossQuantity;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String checker;

    private String remark;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
