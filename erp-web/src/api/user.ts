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
    ElMessage.error(error.message || '请求失败')
    return Promise.reject(error)
  }
)

// ==================== 用户相关 ====================

export interface SysUser {
  id: number
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
  roleId?: number
  departmentId?: number
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
  getUser: (id: number) =>
    request.get<any, SysUser>(`/user/${id}`),

  // 创建用户
  createUser: (data: Partial<SysUser>) =>
    request.post<any, SysUser>('/user', data),

  // 更新用户
  updateUser: (data: Partial<SysUser>) =>
    request.put<any, SysUser>('/user', data),

  // 删除用户
  deleteUser: (id: number) =>
    request.delete<any, void>(`/user/${id}`),

  // 重置密码
  resetPassword: (id: number) =>
    request.post<any, void>(`/user/${id}/reset-password`),

  // 修改密码
  changePassword: (oldPassword: string, newPassword: string) =>
    request.post<any, void>('/user/change-password', { oldPassword, newPassword }),

  // 修改状态
  updateStatus: (id: number, status: number) =>
    request.put<any, void>(`/user/${id}/status`, { status }),

  // 分配角色
  assignRoles: (userId: number, roleIds: number[]) =>
    request.post<any, void>(`/user/${userId}/roles`, { roleIds })
}

// ==================== 角色相关 ====================

export interface Role {
  id: number
  roleCode: string
  roleName: string
  description: string
  status: number
  sort: number
  createTime: string
  permissions?: Permission[]
}

export interface Permission {
  id: number
  permissionCode: string
  permissionName: string
  permissionType: string
  parentId: number
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
  getRole: (id: number) =>
    request.get<any, Role>(`/role/${id}`),

  // 创建角色
  createRole: (data: Partial<Role>) =>
    request.post<any, Role>('/role', data),

  // 更新角色
  updateRole: (data: Partial<Role>) =>
    request.put<any, Role>('/role', data),

  // 删除角色
  deleteRole: (id: number) =>
    request.delete<any, void>(`/role/${id}`),

  // 分配权限
  assignPermissions: (roleId: number, permissionIds: number[]) =>
    request.post<any, void>(`/role/${roleId}/permissions`, { permissionIds }),

  // 获取所有权限树
  getPermissionTree: () =>
    request.get<any, Permission[]>('/permission/tree')
}

// ==================== 部门相关 ====================

export interface Department {
  id: number
  deptName: string
  deptCode: string
  parentId: number
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
  getDepartments: (params?: { parentId?: number }) =>
    request.get<any, Department[]>('/department/list', { params }),

  // 创建部门
  createDepartment: (data: Partial<Department>) =>
    request.post<any, Department>('/department', data),

  // 更新部门
  updateDepartment: (data: Partial<Department>) =>
    request.put<any, Department>('/department', data),

  // 删除部门
  deleteDepartment: (id: number) =>
    request.delete<any, void>(`/department/${id}`)
}