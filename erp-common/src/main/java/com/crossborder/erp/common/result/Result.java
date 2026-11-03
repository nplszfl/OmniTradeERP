package com.crossborder.erp.common.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结果封装
 * @param <T> 数据类型
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 响应码
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 时间戳
     */
    private Long timestamp;

    public Result() {
        this.timestamp = System.currentTimeMillis();
    }

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 成功响应（无数据）
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    /**
     * 🔴 <b>成功响应，data 是雪花 ID</b> —— 以字符串输出。
     *
     * <p>本项目所有「create 接口返回新主键」的场景都该用这个方法：</p>
     * <pre>
     * return Result.successId(orderService.createOrder(...));
     * </pre>
     *
     * <p><b>为什么必须是字符串：</b>本项目 45 个实体用雪花算法生成 19 位主键
     * （如 {@code 2117546519450398722}），而 JS 的 {@code Number} 只能精确表示
     * {@code 2^53-1}（16 位）→ {@code JSON.parse} <b>必然丢精度</b>
     * （实测差 22）→ 前端把丢失精度的 id 原样发回后端 →
     * 后端匹配不到任何行 → <b>HTTP 200，删除/更新静默失败</b>。</p>
     *
     * <p>📌 <b>为什么不用 {@code Result<Long>} + 全局 Long 序列化器</b>：
     * {@code Result<T>} 的 data 字段静态类型是 {@code Object}（泛型擦除），
     * Jackson <b>分不出「这是 id」还是「这是计数」</b>；
     * 而全局把所有 Long 变字符串又会打破
     * {@code IPage.total} / {@code count} 等必须是数字的字段。
     * 所以这里用<b>显式类型</b>表达意图：返回 {@code Result<String>}，
     * data 直接就是字符串，Jackson 天然按字符串输出。</p>
     *
     * <p>📌 <b>跨服务兼容</b>：Feign 端声明 {@code Result<Long>} 依然能用 ——
     * Jackson 会把 JSON 字符串 {@code "123"} 自动转回 {@code Long 123}，
     * 且是<b>精确</b>的（已实测验证）。</p>
     */
    public static Result<String> successId(Long id) {
        return new Result<>(200, "success", id == null ? null : id.toString());
    }

    /**
     * 成功响应（自定义消息）
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    /**
     * 失败响应
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }

    /**
     * 失败响应（自定义码）
     */
    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}
