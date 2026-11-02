package com.crossborder.erp.user.service;

import com.crossborder.erp.common.result.PageResult;
import com.crossborder.erp.user.vo.UserVO;

import java.util.List;

/**
 * 用户管理服务
 *
 * <p>⚠️ 这个接口之前是「认证服务」的重复品（login/register），
 * 而且实际上<b>没有任何地方调用它</b> —— 登录走的是 AuthService。
 * 现在改成真正的用户管理服务，对齐前端 {@code /api/user/**} 的 9 个接口。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
public interface UserService {

    /**
     * 分页查询用户（带角色、部门）
     *
     * @param current       页码
     * @param size          每页条数
     * @param keyword       关键字（用户名/姓名/邮箱/手机号）
     * @param status        状态（null=全部）
     * @param roleId        按角色过滤
     * @param departmentId  按部门过滤
     */
    PageResult<UserVO> pageUsers(int current, int size, String keyword,
                                 Integer status, Long roleId, Long departmentId);

    /**
     * 查询用户详情（含角色、部门）
     */
    UserVO getUserDetail(Long id);

    /**
     * 创建用户
     */
    UserVO createUser(UserVO userVO);

    /**
     * 更新用户
     */
    UserVO updateUser(UserVO userVO);

    /**
     * 删除用户（逻辑删除，同时清理角色/部门关联）
     */
    void deleteUser(Long id);

    /**
     * 重置密码为默认密码
     */
    void resetPassword(Long id, String defaultPassword);

    /**
     * 修改自己的密码
     */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 修改用户状态（启用/禁用）
     */
    void updateStatus(Long id, Integer status);

    /**
     * 分配角色（整体覆盖）
     */
    void assignRoles(Long userId, List<Long> roleIds);

    /**
     * 分配部门（整体覆盖）
     */
    void assignDepartments(Long userId, List<Long> departmentIds);
}
