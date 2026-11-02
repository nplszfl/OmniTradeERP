package com.crossborder.logistics.dto;

import lombok.Data;

import java.util.List;

/**
 * 分页结果
 *
 * <p>物流模块统一返回<b>裸对象</b>（不包 Result），字段与前端
 * {@code res.records} / {@code res.total} 的消费方式一致。</p>
 */
@Data
public class PageVO<T> {

    /** 当前页（从 1 开始） */
    private long current;

    /** 每页条数 */
    private long size;

    /** 总条数 */
    private long total;

    /** 数据 */
    private List<T> records;

    public static <T> PageVO<T> of(long current, long size, long total, List<T> records) {
        PageVO<T> vo = new PageVO<>();
        vo.setCurrent(current);
        vo.setSize(size);
        vo.setTotal(total);
        vo.setRecords(records);
        return vo;
    }
}
