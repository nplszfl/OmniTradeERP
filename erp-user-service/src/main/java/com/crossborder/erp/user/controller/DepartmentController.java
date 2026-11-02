package com.crossborder.erp.user.controller;

import com.crossborder.erp.user.service.DepartmentService;
import com.crossborder.erp.user.vo.DepartmentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门控制器
 *
 * <p>前端全部按裸数组 / 裸对象消费 → 不包 {@code Result}。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Slf4j
@RestController
@RequestMapping("/department")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    /**
     * 部门树
     */
    @GetMapping("/tree")
    public List<DepartmentVO> tree() {
        return departmentService.tree();
    }

    /**
     * 部门列表（扁平）
     */
    @GetMapping("/list")
    public List<DepartmentVO> list(@RequestParam(required = false) Long parentId) {
        return departmentService.listDepartments(parentId);
    }

    /**
     * 创建部门
     */
    @PostMapping
    public DepartmentVO create(@RequestBody DepartmentVO department) {
        return departmentService.createDepartment(department);
    }

    /**
     * 更新部门
     */
    @PutMapping
    public DepartmentVO update(@RequestBody DepartmentVO department) {
        return departmentService.updateDepartment(department);
    }

    /**
     * 删除部门
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
    }
}
