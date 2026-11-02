package com.crossborder.erp.user.controller;

import com.crossborder.erp.user.service.PermissionService;
import com.crossborder.erp.user.vo.PermissionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限控制器
 *
 * <p>前端 {@code permissionTree.value = res || []} → 返回<b>裸数组</b>。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Slf4j
@RestController
@RequestMapping("/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    /**
     * 权限树
     */
    @GetMapping("/tree")
    public List<PermissionVO> tree() {
        return permissionService.tree();
    }
}
