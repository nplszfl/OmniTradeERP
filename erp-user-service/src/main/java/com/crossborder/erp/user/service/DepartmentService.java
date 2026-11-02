package com.crossborder.erp.user.service;

import com.crossborder.erp.user.vo.DepartmentVO;

import java.util.List;

/**
 * 部门服务
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
public interface DepartmentService {

    /**
     * 部门树
     */
    List<DepartmentVO> tree();

    /**
     * 部门列表（扁平）
     *
     * @param parentId 上级部门（null=全部）
     */
    List<DepartmentVO> listDepartments(Long parentId);

    /**
     * 创建部门
     */
    DepartmentVO createDepartment(DepartmentVO departmentVO);

    /**
     * 更新部门
     */
    DepartmentVO updateDepartment(DepartmentVO departmentVO);

    /**
     * 删除部门（有子部门或下属用户时拒绝）
     */
    void deleteDepartment(Long id);
}
