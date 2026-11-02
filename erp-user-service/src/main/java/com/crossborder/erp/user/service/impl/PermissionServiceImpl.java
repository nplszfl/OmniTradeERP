package com.crossborder.erp.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crossborder.erp.user.entity.Permission;
import com.crossborder.erp.user.mapper.PermissionMapper;
import com.crossborder.erp.user.service.PermissionService;
import com.crossborder.erp.user.util.TreeBuilder;
import com.crossborder.erp.user.vo.PermissionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 权限服务实现
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private static final Long ROOT_PARENT_ID = 0L;

    private final PermissionMapper permissionMapper;

    @Override
    public List<PermissionVO> tree() {
        List<Permission> all = permissionMapper.selectList(new LambdaQueryWrapper<Permission>()
                .orderByAsc(Permission::getSortOrder)
                .orderByAsc(Permission::getId));
        List<PermissionVO> vos = all.stream().map(PermissionVO::from).collect(Collectors.toList());
        return TreeBuilder.build(vos,
                PermissionVO::getId,
                PermissionVO::getParentId,
                PermissionVO::setChildren,
                ROOT_PARENT_ID);
    }

    @Override
    public List<PermissionVO> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return permissionMapper.selectBatchIds(ids).stream()
                .map(PermissionVO::from)
                .collect(Collectors.toList());
    }
}
