import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// ⚠️ 必须带上 token：后端 change-password 需要从 Authorization 头解析当前用户，
// 没有 token 后端拿不到 userId 就会直接报错
request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  response => response.data,
  error => {
    // 📌 **401 要跳登录页**（2026-11-12 账户系统加鉴权后补上）
    //    后端现在对 /api/user/** 要求带 token → token 过期/没登录会返回 401
    //    之前这里只弹一句「请求失败」→ **用户看着正常页面却什么都干不了**，
    //    根本不知道是登录过期了。
    //    📌 与 `api/index.ts` 的全局实例保持一致（那边早就有这段，这里漏了）。
    if (error.response && error.response.status === 401) {
      if (window.location.pathname !== '/login') {
        localStorage.removeItem('token')
        window.location.href = '/login'
      }
    } else {
      // 📌 优先用后端给的业务原因（如「原密码错误」「不能修改其他用户的密码」）
      ElMessage.error(error.response?.data?.message || error.message || '请求失败')
    }
    return Promise.reject(error)
  }
)

// ==================== 用户相关 ====================

export interface SysUser {
  id: string
  username: string
  password?: string
  realName: string
  email: string
  phone: string
  avatar: string
  status: number
  lastLoginTime: string
  lastLoginIp: string
  createTime: string
  updateTime: string
  roles?: Role[]
  departments?: Department[]
}

export interface UserQuery {
  current: number
  size: number
  keyword?: string
  status?: number
  roleId?: string
  departmentId?: string
}

export interface PageResult<T> {
  current: number
  size: number
  total: number
  records: T[]
}

export const userApi = {
  // 分页查询用户
  getUsers: (params: UserQuery) =>
    request.get<any, PageResult<SysUser>>('/user/list', { params }),

  // 获取用户详情
  getUser: (id: string) =>
    request.get<any, SysUser>(`/user/${id}`),

  // 创建用户
  createUser: (data: Partial<SysUser>) =>
    request.post<any, SysUser>('/user', data),

  // 更新用户
  updateUser: (data: Partial<SysUser>) =>
    request.put<any, SysUser>('/user', data),

  // 删除用户
  deleteUser: (id: string) =>
    request.delete<any, void>(`/user/${id}`),

  // 重置密码
  resetPassword: (id: string) =>
    request.post<any, void>(`/user/${id}/reset-password`),

  // 修改密码
  changePassword: (oldPassword: string, newPassword: string) =>
    request.post<any, void>('/user/change-password', { oldPassword, newPassword }),

  // 修改状态
  updateStatus: (id: string, status: number) =>
    request.put<any, void>(`/user/${id}/status`, { status }),

  // 分配角色
  assignRoles: (userId: string, roleIds: string[]) =>
    request.post<any, void>(`/user/${userId}/roles`, { roleIds })
}

// ==================== 角色相关 ====================

export interface Role {
  id: string
  roleCode: string
  roleName: string
  description: string
  status: number
  sort: number
  createTime: string
  permissions?: Permission[]
}

export interface Permission {
  id: string
  permissionCode: string
  permissionName: string
  permissionType: string
  parentId: string
  path: string
  component: string
  icon: string
  sort: number
  children?: Permission[]
}

export const roleApi = {
  // 获取角色列表
  getRoles: (params?: { status?: number }) =>
    request.get<any, Role[]>('/role/list', { params }),

  // 分页查询角色
  getRoleList: (params: { current: number; size: number; keyword?: string }) =>
    request.get<any, PageResult<Role>>('/role/list', { params }),

  // 获取角色详情
  getRole: (id: string) =>
    request.get<any, Role>(`/role/${id}`),

  // 创建角色
  createRole: (data: Partial<Role>) =>
    request.post<any, Role>('/role', data),

  // 更新角色
  updateRole: (data: Partial<Role>) =>
    request.put<any, Role>('/role', data),

  // 删除角色
  deleteRole: (id: string) =>
    request.delete<any, void>(`/role/${id}`),

  // 分配权限
  assignPermissions: (roleId: string, permissionIds: string[]) =>
    request.post<any, void>(`/role/${roleId}/permissions`, { permissionIds }),

  // 获取所有权限树
  getPermissionTree: () =>
    request.get<any, Permission[]>('/permission/tree')
}

// ==================== 部门相关 ====================

export interface Department {
  id: string
  deptName: string
  deptCode: string
  parentId: string
  parentName: string
  leader: string
  phone: string
  email: string
  sort: number
  status: number
  createTime: string
  children?: Department[]
}

export const departmentApi = {
  // 获取部门树
  getDeptTree: () =>
    request.get<any, Department[]>('/department/tree'),

  // 获取部门列表
  getDepartments: (params?: { parentId?: string }) =>
    request.get<any, Department[]>('/department/list', { params }),

  // 创建部门
  createDepartment: (data: Partial<Department>) =>
    request.post<any, Department>('/department', data),

  // 更新部门
  updateDepartment: (data: Partial<Department>) =>
    request.put<any, Department>('/department', data),

  // 删除部门
  deleteDepartment: (id: string) =>
    request.delete<any, void>(`/department/${id}`)
}