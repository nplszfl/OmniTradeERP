package com.crossborder.erp.user.controller;

import com.crossborder.erp.common.result.PageResult;
import com.crossborder.erp.common.result.Result;
import com.crossborder.erp.user.service.UserService;
import com.crossborder.erp.user.util.JwtUtil;
import com.crossborder.erp.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    @PostMapping
    public UserVO create(@RequestBody UserVO user) {
        return userService.createUser(user);
    }

    /**
     * 更新用户
     */
    @PutMapping
    public UserVO update(@RequestBody UserVO user) {
        return userService.updateUser(user);
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        userService.deleteUser(id);
    }

    /**
     * 重置密码（重置为默认密码 admin123）
     */
    @PostMapping("/{id}/reset-password")
    public void resetPassword(@PathVariable Long id,
                              @RequestParam(required = false) String defaultPassword) {
        userService.resetPassword(id, defaultPassword);
    }

    /**
     * 修改自己的密码
     *
     * <p>⚠️ 前端 {@code user.ts} 的 axios 实例<b>没有</b>带 Authorization 头，
     * 所以这里同时接受 body 里的 userId 和请求头里的 token，
     * 两个都拿不到就明确报错，而不是偷偷改错用户的密码。</p>
     */
    @PostMapping("/change-password")
    public void changePassword(@RequestBody Map<String, String> body,
                                @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = null;
        Object bodyUserId = body.get("userId");
        if (bodyUserId != null) {
            try {
                userId = Long.valueOf(String.valueOf(bodyUserId));
            } catch (NumberFormatException ignored) {
                log.warn("changePassword 收到非法 userId: {}", bodyUserId);
            }
        }
        if (userId == null && authorization != null) {
            userId = jwtUtil.getUserIdFromToken(authorization.replaceFirst("(?i)^Bearer\\s+", ""));
        }
        if (userId == null) {
            throw new com.crossborder.erp.common.exception.BizException("未获取到当前用户，请重新登录");
        }
        userService.changePassword(userId, body.get("oldPassword"), body.get("newPassword"));
    }

    /**
     * 修改用户状态（启用/禁用）
     */
    @PutMapping("/{id}/status")
    public void updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        userService.updateStatus(id, body.get("status"));
    }

    /**
     * 分配角色（整体覆盖）
     */
    @PostMapping("/{userId}/roles")
    public void assignRoles(@PathVariable Long userId, @RequestBody Map<String, List<Long>> body) {
        userService.assignRoles(userId, body.get("roleIds"));
    }

    /**
     * 分配部门（整体覆盖）
     */
    @PostMapping("/{userId}/departments")
    public void assignDepartments(@PathVariable Long userId, @RequestBody Map<String, List<Long>> body) {
        userService.assignDepartments(userId, body.get("departmentIds"));
    }
}
