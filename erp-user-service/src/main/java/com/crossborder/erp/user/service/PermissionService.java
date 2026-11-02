package com.crossborder.erp.user.service;

import com.crossborder.erp.user.vo.PermissionVO;

import java.util.List;

/**
 * 权限服务
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
public interface PermissionService {

    /**
     * 权限树
     */
    List<PermissionVO> tree();

    /**
     * 按ID集合查询权限
     */
    List<PermissionVO> listByIds(List<Long> ids);
}
