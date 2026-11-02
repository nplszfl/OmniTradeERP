package com.crossborder.erp.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.user.entity.Department;
import com.crossborder.erp.user.entity.UserDepartment;
import com.crossborder.erp.user.mapper.DepartmentMapper;
import com.crossborder.erp.user.mapper.UserDepartmentMapper;
import com.crossborder.erp.user.service.DepartmentService;
import com.crossborder.erp.user.util.TreeBuilder;
import com.crossborder.erp.user.vo.DepartmentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 部门服务实现
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private static final Long ROOT_PARENT_ID = 0L;

    private final DepartmentMapper departmentMapper;
    private final UserDepartmentMapper userDepartmentMapper;

    @Override
    public List<DepartmentVO> tree() {
        List<Department> all = departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                .orderByAsc(Department::getSortOrder)
                .orderByAsc(Department::getId));
        List<DepartmentVO> vos = all.stream().map(DepartmentVO::from).collect(Collectors.toList());
        fillParentNames(vos);
        return TreeBuilder.build(vos,
                DepartmentVO::getId,
                DepartmentVO::getParentId,
                DepartmentVO::setChildren,
                ROOT_PARENT_ID);
    }

    @Override
    public List<DepartmentVO> listDepartments(Long parentId) {
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<Department>()
                .eq(parentId != null, Department::getParentId, parentId)
                .orderByAsc(Department::getSortOrder)
                .orderByAsc(Department::getId);
        List<DepartmentVO> vos = departmentMapper.selectList(wrapper).stream()
                .map(DepartmentVO::from)
                .collect(Collectors.toList());
        fillParentNames(vos);
        return vos;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepartmentVO createDepartment(DepartmentVO departmentVO) {
        if (departmentVO == null || !StringUtils.hasText(departmentVO.getDeptName())) {
            throw new BizException("部门名称不能为空");
        }
        if (StringUtils.hasText(departmentVO.getDeptCode())) {
            Long count = departmentMapper.selectCount(new LambdaQueryWrapper<Department>()
                    .eq(Department::getDeptCode, departmentVO.getDeptCode()));
            if (count != null && count > 0) {
                throw new BizException("部门编码已存在");
            }
        }
        Department dept = new Department();
        dept.setDeptCode(departmentVO.getDeptCode());
        dept.setDeptName(departmentVO.getDeptName());
        dept.setParentId(departmentVO.getParentId() == null ? ROOT_PARENT_ID : departmentVO.getParentId());
        dept.setLeader(departmentVO.getLeader());
        dept.setPhone(departmentVO.getPhone());
        dept.setEmail(departmentVO.getEmail());
        dept.setSortOrder(departmentVO.getSort() == null ? 0 : departmentVO.getSort());
        dept.setStatus(departmentVO.getStatus() == null ? 1 : departmentVO.getStatus());
        dept.setCreateTime(LocalDateTime.now());
        dept.setUpdateTime(LocalDateTime.now());

        departmentMapper.insert(dept);
        log.info("创建部门成功: deptName={}", dept.getDeptName());
        return DepartmentVO.from(dept);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepartmentVO updateDepartment(DepartmentVO departmentVO) {
        if (departmentVO == null || departmentVO.getId() == null) {
            throw new BizException("部门ID不能为空");
        }
        Department existing = departmentMapper.selectById(departmentVO.getId());
        if (existing == null) {
            throw new BizException("部门不存在");
        }
        Department dept = new Department();
        dept.setId(departmentVO.getId());
        dept.setDeptName(departmentVO.getDeptName());
        dept.setLeader(departmentVO.getLeader());
        dept.setPhone(departmentVO.getPhone());
        dept.setEmail(departmentVO.getEmail());
        dept.setStatus(departmentVO.getStatus());
        dept.setSortOrder(departmentVO.getSort());
        if (departmentVO.getParentId() != null) {
            // 不允许把自己挂到自己的子树下，否则树会成环
            if (departmentVO.getId().equals(departmentVO.getParentId())) {
                throw new BizException("上级部门不能是自己");
            }
            dept.setParentId(departmentVO.getParentId());
        }
        dept.setUpdateTime(LocalDateTime.now());
        departmentMapper.updateById(dept);
        return DepartmentVO.from(departmentMapper.selectById(departmentVO.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDepartment(Long id) {
        Department dept = departmentMapper.selectById(id);
        if (dept == null) {
            throw new BizException("部门不存在");
        }
        Long children = departmentMapper.selectCount(new LambdaQueryWrapper<Department>()
                .eq(Department::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException("该部门下还有子部门，无法删除");
        }
        Long users = userDepartmentMapper.selectCount(new LambdaQueryWrapper<UserDepartment>()
                .eq(UserDepartment::getDepartmentId, id));
        if (users != null && users > 0) {
            throw new BizException("该部门下还有用户，无法删除");
        }
        departmentMapper.deleteById(id);
        log.info("删除部门成功: deptId={}", id);
    }

    /**
     * 填充上级部门名称（前端表格「上级部门」列直接展示 parentName）
     */
    private void fillParentNames(List<DepartmentVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        Map<Long, String> nameById = vos.stream()
                .filter(v -> v.getId() != null)
                .collect(Collectors.toMap(DepartmentVO::getId, DepartmentVO::getDeptName,
                        (a, b) -> a));
        for (DepartmentVO vo : vos) {
            Long parentId = vo.getParentId();
            vo.setParentName(parentId == null ? null : nameById.get(parentId));
        }
    }
}
