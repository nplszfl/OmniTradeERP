package com.crossborder.erp.warehouse.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 仓库查询DTO
 */
@Data
public class WarehouseQueryDTO {
    private Long id;
    private String warehouseCode;
    private String name;
    private String country;
    private String city;
    private Integer status;
    private String warehouseType;
    private Integer page = 1;
    private Integer size = 20;
}

/**
 * 库位查询DTO
 */
@Data
public class LocationQueryDTO {
    private Long warehouseId;
    private String locationCode;
    private String locationType;
    private String zone;
    private String status;
    private Boolean isPickable;
    private Boolean isStorable;
    private Integer page = 1;
    private Integer size = 20;
}

/**
 * 入库单DTO
 */
@Data
public class InboundOrderDTO {
    private Long id;
    private Long warehouseId;
    private String inboundType;
    private String supplierCode;
    private String supplierName;
    private String purchaseOrderNo;
    private String expectedArrivalDate;
    private String remark;
    
    // 明细
    private java.util.List<InboundDetailDTO> details;
}

/**
 * 入库明细DTO
 */
@Data
class InboundDetailDTO {
    private Long productId;
    private String skuCode;
    private Integer quantity;
    private String locationCode;
    private String batchNo;
    private String productionDate;
    private BigDecimal unitCost;
    private String remark;
}

/**
 * 出库单DTO
 */
@Data
public class OutboundOrderDTO {
    private Long id;
    private Long warehouseId;
    private String outboundType;
    private String orderNo;
    private String customerName;
    private String shippingAddress;
    private String shippingMethod;
    private String remark;
    
    // 明细
    private java.util.List<OutboundDetailDTO> details;
}

/**
 * 出库明细DTO
 */
@Data
class OutboundDetailDTO {
    private Long productId;
    private String skuCode;
    private Integer quantity;
    private String locationCode;
    private String batchNo;
    private String remark;
}

/**
 * 调拨单DTO
 */
@Data
public class TransferOrderDTO {
    private Long id;
    private Long fromWarehouseId;
    private Long toWarehouseId;
    private String transferType;
    private String expectedArrivalDate;
    private String remark;
    
    // 明细
    private java.util.List<TransferDetailDTO> details;
}

/**
 * 调拨明细DTO
 */
@Data
class TransferDetailDTO {
    private Long productId;
    private String skuCode;
    private Integer quantity;
    private String fromLocationCode;
    private String toLocationCode;
    private String batchNo;
}

/**
 * 波次创建DTO
 */
@Data
public class WaveCreateDTO {
    private String waveType;
    private String priority;
    private java.util.List<Long> orderIds;
    private String remark;
}

/**
 * 包装材料DTO
 */
@Data
public class PackingMaterialDTO {
    private Long id;
    private String materialCode;
    private String materialName;
    private String materialType;
    private String specifications;
    private Double weight;
    private Integer quantity;
    private String supplierName;
    private String remark;
}