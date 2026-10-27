package com.crossborder.erp.warehouse.vo;

import com.crossborder.erp.warehouse.entity.Warehouse;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 仓库VO —— 对齐前端契约 {@code erp-web/src/api/warehouse.ts} 的 {@code Warehouse} 接口。
 *
 * <p>前端字段名与后端实体不一致，这里统一转换：</p>
 * <ul>
 *     <li>{@code warehouseName} &lt;-&gt; {@code Warehouse.name}</li>
 *     <li>{@code contactPerson} &lt;-&gt; {@code Warehouse.contact}</li>
 *     <li>{@code contactPhone} &lt;-&gt; {@code Warehouse.phone}</li>
 * </ul>
 *
 * @author 火球鼠
 * @since 2026-10-28
 */
@Data
public class WarehouseVO {

    /** 主键 */
    private Long id;

    /** 仓库编码 */
    private String warehouseCode;

    /** 仓库名称（对应实体 name） */
    private String warehouseName;

    /** 仓库类型：main / overseas / third_party */
    private String warehouseType;

    /** 国家 */
    private String country;

    /** 省份 */
    private String province;

    /** 城市 */
    private String city;

    /** 详细地址 */
    private String address;

    /** 联系人（对应实体 contact） */
    private String contactPerson;

    /** 联系电话（对应实体 phone） */
    private String contactPhone;

    /** 邮箱 */
    private String email;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 实体 -> VO
     */
    public static WarehouseVO from(Warehouse entity) {
        if (entity == null) {
            return null;
        }
        WarehouseVO vo = new WarehouseVO();
        vo.setId(entity.getId());
        vo.setWarehouseCode(entity.getWarehouseCode());
        vo.setWarehouseName(entity.getName());
        vo.setWarehouseType(entity.getWarehouseType());
        vo.setCountry(entity.getCountry());
        vo.setProvince(entity.getProvince());
        vo.setCity(entity.getCity());
        vo.setAddress(entity.getAddress());
        vo.setContactPerson(entity.getContact());
        vo.setContactPhone(entity.getPhone());
        vo.setEmail(entity.getEmail());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /**
     * VO -> 实体（创建 / 更新入参转换）
     */
    public Warehouse toEntity() {
        Warehouse entity = new Warehouse();
        entity.setId(id);
        entity.setWarehouseCode(warehouseCode);
        entity.setName(warehouseName);
        entity.setWarehouseType(warehouseType);
        entity.setCountry(country);
        entity.setProvince(province);
        entity.setCity(city);
        entity.setAddress(address);
        entity.setContact(contactPerson);
        entity.setPhone(contactPhone);
        entity.setEmail(email);
        entity.setStatus(status);
        entity.setCreateTime(createTime);
        entity.setUpdateTime(updateTime);
        return entity;
    }
}
