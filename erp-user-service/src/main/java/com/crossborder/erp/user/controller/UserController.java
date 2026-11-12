package com.crossborder.erp.user.controller;

import com.crossborder.erp.common.result.PageResult;
import com.crossborder.erp.common.result.Result;
import com.crossborder.erp.user.dto.ResetPasswordRequest;
import com.crossborder.erp.user.service.UserService;
import com.crossborder.erp.user.util.JwtUtil;
import com.crossborder.erp.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理控制器
 *
 * <p>📌 <b>响应形态严格按前端消费方式决定</b>（见 erp-web/src/api/user.ts）：
 * <ul>
 *   <li>分页 → 包 {@code Result}（前端读 {@code res.data.records}）</li>
 *   <li>单个对象 / 数组 → <b>不包</b>（前端读 {@code res.xxx} 或 {@code res || []}）</li>
 * </ul>
 * 包错了前端不会报错，只会静默拿到 undefined。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    /**
     * 分页查询用户
     *
     * <p>前端：{@code const pageData = res.data || {}; userList.value = pageData.records || []}</p>
     */
    @GetMapping("/list")
    public Result<PageResult<UserVO>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Long departmentId) {
        return Result.success(userService.pageUsers(current, size, keyword, status, roleId, departmentId));
    }

    /**
     * 用户详情（含角色、部门）
     */
    @GetMapping("/{id}")
    public UserVO detail(@PathVariable Long id) {
        return userService.getUserDetail(id);
    }

    /**
     * 创建用户
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @PostMapping
    public UserVO create(@RequestBody UserVO user) {
        return userService.createUser(user);
    }

    /**
     * 更新用户
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @PutMapping
    public UserVO update(@RequestBody UserVO user) {
        return userService.updateUser(user);
    }

    /**
     * 删除用户
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        userService.deleteUser(id);
    }

    /**
     * 重置密码（不传口令 = 重置为默认口令）
     *
     * <p>📌 <b>口令只能走请求体，不能走 URL 查询串</b>（2026-11-12 修）：
     * <pre>
     *   ❌ POST /api/user/1/reset-password?defaultPassword=Secret123
     *   ✅ POST /api/user/1/reset-password   body: {"defaultPassword":"Secret123"}
     * </pre>
     * nginx / ingress / 网关的访问日志里有 {@code $request} = <b>完整请求行（含 query string）</b>
     * → 口令走 query → <b>明文口令进访问日志</b>。
     * 📌 之前用 {@code @RequestParam}，现在用 {@code @RequestBody}（body 不进访问日志）。
     * 📌 body 可选 → 前端不传时仍是「重置为默认口令」，契约兼容。</p>
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @PostMapping("/{id}/reset-password")
    public void resetPassword(@PathVariable Long id,
                              @RequestBody(required = false) ResetPasswordRequest request) {
        userService.resetPassword(id, request == null ? null : request.getDefaultPassword());
    }

    /**
     * 修改自己的密码
     *
     * <p>📌 <b>身份只认 token</b>：body 里的 {@code userId} <b>不再</b>当身份用
     * —— 之前是「body 优先、token 兜底」，等于登录用户可以指定**任意 userId**
     * 去改别人的密码（虽然还要过「原密码」这一关，但方向是错的）。
     * 现在：<b>token 才是身份</b>；body 里带了 {@code userId} 且和 token 对不上 → 直接报错。</p>
     *
     * <p>📌 前端 {@code user.ts} 的 axios 实例<b>是</b>带 Authorization 头的
     * （request 拦截器统一加 {@code Bearer <token>}），所以身份一定拿得到。</p>
     */
    @PostMapping("/change-password")
    public void changePassword(@RequestBody Map<String, String> body,
                                @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long tokenUserId = null;
        if (authorization != null) {
            tokenUserId = jwtUtil.getUserIdFromToken(authorization.replaceFirst("(?i)^Bearer\\s+", ""));
        }
        if (tokenUserId == null) {
            throw new com.crossborder.erp.common.exception.BizException("未获取到当前用户，请重新登录");
        }
        String bodyUserId = body.get("userId");
        if (bodyUserId != null && !bodyUserId.isBlank()) {
            Long parsed;
            try {
                parsed = Long.valueOf(bodyUserId.trim());
            } catch (NumberFormatException e) {
                throw new com.crossborder.erp.common.exception.BizException("userId 非法");
            }
            if (!parsed.equals(tokenUserId)) {
                throw new com.crossborder.erp.common.exception.BizException("不能修改其他用户的密码");
            }
        }
        userService.changePassword(tokenUserId, body.get("oldPassword"), body.get("newPassword"));
    }

    /**
     * 修改用户状态（启用/禁用）
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @PutMapping("/{id}/status")
    public void updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        userService.updateStatus(id, body.get("status"));
    }

    /**
     * 分配角色（整体覆盖）
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @PostMapping("/{userId}/roles")
    public void assignRoles(@PathVariable Long userId, @RequestBody Map<String, List<Long>> body) {
        userService.assignRoles(userId, body.get("roleIds"));
    }

    /**
     * 分配部门（整体覆盖）
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @PostMapping("/{userId}/departments")
    public void assignDepartments(@PathVariable Long userId, @RequestBody Map<String, List<Long>> body) {
        userService.assignDepartments(userId, body.get("departmentIds"));
    }
}
