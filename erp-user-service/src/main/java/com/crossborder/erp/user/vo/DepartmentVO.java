package com.crossborder.erp.user.vo;

import com.crossborder.erp.user.entity.Department;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 部门视图对象（树形）
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Data
public class DepartmentVO {

    private Long id;

    private String deptCode;

    private String deptName;

    private Long parentId;

    /** 上级部门名称（前端表格「上级部门」列） */
    private String parentName;

    private String leader;

    private String phone;

    private String email;

    /** 排序号（实体列名 sort_order） */
    private Integer sort;

    private Integer status;

    private LocalDateTime createTime;

    /** 子部门 */
    private List<DepartmentVO> children = new ArrayList<>();

    public static DepartmentVO from(Department dept) {
        if (dept == null) {
            return null;
        }
        DepartmentVO vo = new DepartmentVO();
        vo.setId(dept.getId());
        vo.setDeptCode(dept.getDeptCode());
        vo.setDeptName(dept.getDeptName());
        vo.setParentId(dept.getParentId());
        vo.setLeader(dept.getLeader());
        vo.setPhone(dept.getPhone());
        vo.setEmail(dept.getEmail());
        vo.setSort(dept.getSortOrder());
        vo.setStatus(dept.getStatus());
        vo.setCreateTime(dept.getCreateTime());
        return vo;
    }
}
