<template>
  <div class="user-page">
    <!-- 标签页 -->
    <el-tabs v-model="activeTab" type="border-card" @tab-change="handleTabChange">
      <!-- 用户管理 -->
      <el-tab-pane label="用户管理" name="user">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>用户列表</span>
              <el-button type="primary" :icon="Plus" @click="handleAddUser">添加用户</el-button>
            </div>
          </template>

          <!-- 搜索筛选 -->
          <div class="filter-section">
            <el-form :inline="true" :model="userFilterForm" class="filter-form">
              <el-form-item label="关键词">
                <el-input v-model="userFilterForm.keyword" placeholder="用户名/姓名/邮箱" clearable style="width: 180px" />
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="userFilterForm.status" placeholder="全部" clearable style="width: 100px">
                  <el-option label="启用" :value="1" />
                  <el-option label="禁用" :value="0" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :icon="Search" @click="handleUserSearch">搜索</el-button>
                <el-button :icon="Refresh" @click="handleUserReset">重置</el-button>
              </el-form-item>
            </el-form>
          </div>

          <el-table v-loading="userLoading" :data="userList" stripe style="width: 100%">
            <el-table-column prop="username" label="用户名" width="150" />
            <el-table-column prop="realName" label="真实姓名" width="120" />
            <el-table-column prop="email" label="邮箱" min-width="180" />
            <el-table-column prop="phone" label="手机号" width="130" />
            <el-table-column prop="roles" label="角色" width="150">
              <template #default="{ row }">
                <el-tag v-for="role in row.roles" :key="role.id" size="small" style="margin-right: 5px">
                  {{ role.roleName }}
                </el-tag>
                <span v-if="!row.roles?.length" class="text-gray">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="lastLoginTime" label="最后登录" width="160">
              <template #default="{ row }">
                {{ row.lastLoginTime ? formatTime(row.lastLoginTime) : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'">
                  {{ row.status === 1 ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleEditUser(row)">编辑</el-button>
                <el-button link type="warning" size="small" @click="handleAssignRole(row)">分配角色</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteUser(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="userPagination.current"
            v-model:page-size="userPagination.size"
            :total="userPagination.total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            style="margin-top: 20px; justify-content: flex-end"
            @size-change="loadUsers"
            @current-change="loadUsers"
          />
        </el-card>
      </el-tab-pane>

      <!-- 角色管理 -->
      <el-tab-pane label="角色管理" name="role">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>角色列表</span>
              <el-button type="primary" :icon="Plus" @click="handleAddRole">添加角色</el-button>
            </div>
          </template>

          <el-table v-loading="roleLoading" :data="roleList" stripe style="width: 100%">
            <el-table-column prop="roleCode" label="角色编码" width="150" />
            <el-table-column prop="roleName" label="角色名称" width="150" />
            <el-table-column prop="description" label="描述" min-width="200" />
            <el-table-column prop="sort" label="排序" width="80" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'">
                  {{ row.status === 1 ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="160">
              <template #default="{ row }">
                {{ formatTime(row.createTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleEditRole(row)">编辑</el-button>
                <el-button link type="success" size="small" @click="handleAssignPermission(row)">分配权限</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteRole(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 部门管理 -->
      <el-tab-pane label="部门管理" name="department">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>部门列表</span>
              <el-button type="primary" :icon="Plus" @click="handleAddDept">添加部门</el-button>
            </div>
          </template>

          <el-table v-loading="deptLoading" :data="deptList" stripe style="width: 100%">
            <el-table-column prop="deptCode" label="部门编码" width="150" />
            <el-table-column prop="deptName" label="部门名称" width="180" />
            <el-table-column prop="parentName" label="上级部门" width="150" />
            <el-table-column prop="leader" label="负责人" width="100" />
            <el-table-column prop="phone" label="联系电话" width="130" />
            <el-table-column prop="sort" label="排序" width="80" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'">
                  {{ row.status === 1 ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleEditDept(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteDept(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 用户对话框 -->
    <el-dialog v-model="userDialogVisible" :title="userDialogTitle" width="600px" :close-on-click-modal="false">
      <el-form ref="userFormRef" :model="userForm" :rules="userFormRules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="userForm.username" placeholder="请输入用户名" :disabled="!!userForm.id" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="真实姓名" prop="realName">
              <el-input v-model="userForm.realName" placeholder="请输入真实姓名" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="userForm.email" placeholder="请输入邮箱" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="userForm.phone" placeholder="请输入手机号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="!userForm.id" label="密码" prop="password">
          <el-input v-model="userForm.password" type="password" placeholder="请输入密码" show-password style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="userForm.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="userSubmitLoading" @click="handleUserSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色对话框 -->
    <el-dialog v-model="roleDialogVisible" title="分配角色" width="400px">
      <el-form label-width="80px">
        <el-form-item label="用户">
          <span>{{ currentUser?.username }}</span>
        </el-form-item>
        <el-form-item label="角色">
          <el-checkbox-group v-model="selectedRoleIds">
            <el-checkbox v-for="role in allRoles" :key="role.id" :label="role.id">
              {{ role.roleName }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSubmitLoading" @click="handleRoleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 角色对话框 -->
    <el-dialog v-model="roleFormDialogVisible" :title="roleFormDialogTitle" width="500px" :close-on-click-modal="false">
      <el-form ref="roleFormRef" :model="roleForm" :rules="roleFormRules" label-width="100px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="roleForm.roleCode" placeholder="如：admin" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="roleForm.roleName" placeholder="如：管理员" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="roleForm.description" type="textarea" :rows="2" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="roleForm.sort" :min="0" style="width: 150px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="roleForm.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleFormDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleFormSubmitLoading" @click="handleRoleFormSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限对话框 -->
    <el-dialog v-model="permDialogVisible" title="分配权限" width="400px">
      <div class="perm-tree-wrapper">
        <el-tree
          ref="permTreeRef"
          :data="permissionTree"
          :props="{ label: 'permissionName', children: 'children' }"
          show-checkbox
          node-key="id"
          :default-checked-keys="checkedPermIds"
        />
      </div>
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="permSubmitLoading" @click="handlePermSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 部门对话框 -->
    <el-dialog v-model="deptDialogVisible" :title="deptDialogTitle" width="500px" :close-on-click-modal="false">
      <el-form ref="deptFormRef" :model="deptForm" :rules="deptFormRules" label-width="100px">
        <el-form-item label="上级部门">
          <el-select v-model="deptForm.parentId" placeholder="请选择上级部门" clearable style="width: 100%">
            <el-option v-for="dept in deptList" :key="dept.id" :label="dept.deptName" :value="dept.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门编码" prop="deptCode">
          <el-input v-model="deptForm.deptCode" placeholder="请输入部门编码" />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="deptForm.deptName" placeholder="请输入部门名称" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="负责人">
              <el-input v-model="deptForm.leader" placeholder="负责人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="deptForm.phone" placeholder="联系电话" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="邮箱">
          <el-input v-model="deptForm.email" placeholder="邮箱" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="deptForm.sort" :min="0" style="width: 150px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="deptForm.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deptDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="deptSubmitLoading" @click="handleDeptSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { 
  userApi, roleApi, departmentApi, 
  type SysUser, type Role, type Department, type Permission 
} from '@/api/user'
import { unwrapPage } from '@/utils/response'

const activeTab = ref('user')

// ========== 用户管理 ==========
const userLoading = ref(false)
const userList = ref<SysUser[]>([])
const userPagination = reactive({ current: 1, size: 20, total: 0 })
const userFilterForm = reactive({ keyword: '', status: undefined as number | undefined })

const userDialogVisible = ref(false)
const userDialogTitle = ref('添加用户')
const userFormRef = ref()
const userSubmitLoading = ref(false)
const userForm = reactive({
  id: undefined as number | undefined,
  username: '',
  realName: '',
  email: '',
  phone: '',
  password: '',
  status: 1
})
const userFormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur', min: 6 }]
}

// 分配角色
const roleDialogVisible = ref(false)
const currentUser = ref<SysUser | null>(null)
const allRoles = ref<Role[]>([])
const selectedRoleIds = ref<number[]>([])
const roleSubmitLoading = ref(false)

// ========== 角色管理 ==========
const roleLoading = ref(false)
const roleList = ref<Role[]>([])

const roleFormDialogVisible = ref(false)
const roleFormDialogTitle = ref('添加角色')
const roleFormRef = ref()
const roleFormSubmitLoading = ref(false)
const roleForm = reactive({
  id: undefined as number | undefined,
  roleCode: '',
  roleName: '',
  description: '',
  sort: 0,
  status: 1
})
const roleFormRules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

// 分配权限
const permDialogVisible = ref(false)
const currentRole = ref<Role | null>(null)
const permissionTree = ref<Permission[]>([])
const checkedPermIds = ref<number[]>([])
const permTreeRef = ref()
const permSubmitLoading = ref(false)

// ========== 部门管理 ==========
const deptLoading = ref(false)
const deptList = ref<Department[]>([])

const deptDialogVisible = ref(false)
const deptDialogTitle = ref('添加部门')
const deptFormRef = ref()
const deptSubmitLoading = ref(false)
const deptForm = reactive({
  id: undefined as number | undefined,
  parentId: undefined as number | undefined,
  parentName: '',
  deptCode: '',
  deptName: '',
  leader: '',
  phone: '',
  email: '',
  sort: 0,
  status: 1
})
const deptFormRules = {
  deptCode: [{ required: true, message: '请输入部门编码', trigger: 'blur' }],
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }]
}

// 加载用户列表
const loadUsers = async () => {
  userLoading.value = true
  try {
    const params = {
      current: userPagination.current,
      size: userPagination.size,
      ...userFilterForm
    }
    const res = await userApi.getUsers(params)
    const pageData = unwrapPage(res)
    userList.value = pageData.records || []
    userPagination.total = pageData.total || 0
  } catch (error) {
    console.error('加载用户列表失败:', error)
  } finally {
    userLoading.value = false
  }
}

// 加载所有角色
const loadAllRoles = async () => {
  try {
    const res = await roleApi.getRoles()
    allRoles.value = res || []
  } catch (error) {
    console.error('加载角色列表失败:', error)
  }
}

// 加载角色列表
const loadRoles = async () => {
  roleLoading.value = true
  try {
    const res = await roleApi.getRoleList({ current: 1, size: 100 })
    const pageData = unwrapPage(res)
    roleList.value = pageData.records || []
  } catch (error) {
    console.error('加载角色列表失败:', error)
  } finally {
    roleLoading.value = false
  }
}

// 加载部门列表
const loadDepartments = async () => {
  deptLoading.value = true
  try {
    const res = await departmentApi.getDeptTree()
    // 扁平化处理树形数据
    const flatten = (list: Department[]): Department[] => {
      return list.reduce((acc, item) => {
        acc.push(item)
        if (item.children?.length) {
          acc.push(...flatten(item.children))
        }
        return acc
      }, [] as Department[])
    }
    deptList.value = flatten(res || [])
  } catch (error) {
    console.error('加载部门列表失败:', error)
  } finally {
    deptLoading.value = false
  }
}

// 标签页切换
const handleTabChange = (tab: string) => {
  if (tab === 'user') loadUsers()
  else if (tab === 'role') loadRoles()
  else if (tab === 'department') loadDepartments()
}

// ========== 用户操作 ==========
const handleUserSearch = () => { userPagination.current = 1; loadUsers() }
const handleUserReset = () => {
  userFilterForm.keyword = ''
  userFilterForm.status = undefined
  handleUserSearch()
}

const handleAddUser = () => {
  userDialogTitle.value = '添加用户'
  userForm.id = undefined
  userForm.username = ''
  userForm.realName = ''
  userForm.email = ''
  userForm.phone = ''
  userForm.password = ''
  userForm.status = 1
  userDialogVisible.value = true
}

const handleEditUser = (row: SysUser) => {
  userDialogTitle.value = '编辑用户'
  userForm.id = row.id
  userForm.username = row.username
  userForm.realName = row.realName
  userForm.email = row.email
  userForm.phone = row.phone
  userForm.password = ''
  userForm.status = row.status
  userDialogVisible.value = true
}

const handleUserSubmit = async () => {
  const valid = await userFormRef.value?.validate().catch(() => false)
  if (!valid) return

  userSubmitLoading.value = true
  try {
    if (userForm.id) {
      // 编辑时移除密码字段
      const data = { ...userForm }
      delete (data as any).password
      await userApi.updateUser(data)
      ElMessage.success('更新成功')
    } else {
      await userApi.createUser(userForm)
      ElMessage.success('创建成功')
    }
    userDialogVisible.value = false
    loadUsers()
  } catch (error) {
    console.error('保存用户失败:', error)
  } finally {
    userSubmitLoading.value = false
  }
}

const handleDeleteUser = (row: SysUser) => {
  ElMessageBox.confirm(`确定删除用户 ${row.username} 吗？`, '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      await userApi.deleteUser(row.id)
      ElMessage.success('删除成功')
      loadUsers()
    } catch (error) {
      console.error('删除用户失败:', error)
    }
  }).catch(() => {})
}

const handleAssignRole = async (row: SysUser) => {
  currentUser.value = row
  selectedRoleIds.value = row.roles?.map(r => r.id) || []
  await loadAllRoles()
  roleDialogVisible.value = true
}

const handleRoleSubmit = async () => {
  if (!currentUser.value) return
  roleSubmitLoading.value = true
  try {
    await userApi.assignRoles(currentUser.value.id, selectedRoleIds.value)
    ElMessage.success('分配成功')
    roleDialogVisible.value = false
    loadUsers()
  } catch (error) {
    console.error('分配角色失败:', error)
  } finally {
    roleSubmitLoading.value = false
  }
}

// ========== 角色操作 ==========
const handleAddRole = () => {
  roleFormDialogTitle.value = '添加角色'
  roleForm.id = undefined
  roleForm.roleCode = ''
  roleForm.roleName = ''
  roleForm.description = ''
  roleForm.sort = 0
  roleForm.status = 1
  roleFormDialogVisible.value = true
}

const handleEditRole = (row: Role) => {
  roleFormDialogTitle.value = '编辑角色'
  roleForm.id = row.id
  roleForm.roleCode = row.roleCode
  roleForm.roleName = row.roleName
  roleForm.description = row.description || ''
  roleForm.sort = row.sort
  roleForm.status = row.status
  roleFormDialogVisible.value = true
}

const handleRoleFormSubmit = async () => {
  const valid = await roleFormRef.value?.validate().catch(() => false)
  if (!valid) return

  roleFormSubmitLoading.value = true
  try {
    if (roleForm.id) {
      await roleApi.updateRole(roleForm)
      ElMessage.success('更新成功')
    } else {
      await roleApi.createRole(roleForm)
      ElMessage.success('创建成功')
    }
    roleFormDialogVisible.value = false
    loadRoles()
  } catch (error) {
    console.error('保存角色失败:', error)
  } finally {
    roleFormSubmitLoading.value = false
  }
}

const handleDeleteRole = (row: Role) => {
  ElMessageBox.confirm(`确定删除角色 ${row.roleName} 吗？`, '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      await roleApi.deleteRole(row.id)
      ElMessage.success('删除成功')
      loadRoles()
    } catch (error) {
      console.error('删除角色失败:', error)
    }
  }).catch(() => {})
}

const handleAssignPermission = async (row: Role) => {
  currentRole.value = row
  try {
    // 加载权限树
    const res = await roleApi.getPermissionTree()
    permissionTree.value = res || []
    
    // 加载当前角色的权限
    const roleDetail = await roleApi.getRole(row.id)
    checkedPermIds.value = roleDetail.permissions?.map(p => p.id) || []
  } catch (error) {
    console.error('加载权限失败:', error)
    permissionTree.value = []
    checkedPermIds.value = []
  }
  permDialogVisible.value = true
}

const handlePermSubmit = async () => {
  if (!currentRole.value) return
  permSubmitLoading.value = true
  try {
    const checkedKeys = permTreeRef.value?.getCheckedKeys() || []
    const halfCheckedKeys = permTreeRef.value?.getHalfCheckedKeys() || []
    const permissionIds = [...checkedKeys, ...halfCheckedKeys] as number[]
    
    await roleApi.assignPermissions(currentRole.value.id, permissionIds)
    ElMessage.success('分配成功')
    permDialogVisible.value = false
  } catch (error) {
    console.error('分配权限失败:', error)
  } finally {
    permSubmitLoading.value = false
  }
}

// ========== 部门操作 ==========
const handleAddDept = () => {
  deptDialogTitle.value = '添加部门'
  deptForm.id = undefined
  deptForm.parentId = undefined
  deptForm.parentName = ''
  deptForm.deptCode = ''
  deptForm.deptName = ''
  deptForm.leader = ''
  deptForm.phone = ''
  deptForm.email = ''
  deptForm.sort = 0
  deptForm.status = 1
  deptDialogVisible.value = true
}

const handleEditDept = (row: Department) => {
  deptDialogTitle.value = '编辑部门'
  deptForm.id = row.id
  deptForm.parentId = row.parentId
  deptForm.parentName = row.parentName || ''
  deptForm.deptCode = row.deptCode
  deptForm.deptName = row.deptName
  deptForm.leader = row.leader || ''
  deptForm.phone = row.phone || ''
  deptForm.email = row.email || ''
  deptForm.sort = row.sort
  deptForm.status = row.status
  deptDialogVisible.value = true
}

const handleDeptSubmit = async () => {
  const valid = await deptFormRef.value?.validate().catch(() => false)
  if (!valid) return

  deptSubmitLoading.value = true
  try {
    // 如果有parentName但没有parentId，需要通过名称查找
    if (deptForm.parentName && !deptForm.parentId) {
      const parent = deptList.value.find(d => d.deptName === deptForm.parentName)
      if (parent) deptForm.parentId = parent.id
    }
    
    if (deptForm.id) {
      await departmentApi.updateDepartment(deptForm)
      ElMessage.success('更新成功')
    } else {
      await departmentApi.createDepartment(deptForm)
      ElMessage.success('创建成功')
    }
    deptDialogVisible.value = false
    loadDepartments()
  } catch (error) {
    console.error('保存部门失败:', error)
  } finally {
    deptSubmitLoading.value = false
  }
}

const handleDeleteDept = (row: Department) => {
  ElMessageBox.confirm(`确定删除部门 ${row.deptName} 吗？`, '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      await departmentApi.deleteDepartment(row.id)
      ElMessage.success('删除成功')
      loadDepartments()
    } catch (error) {
      console.error('删除部门失败:', error)
    }
  }).catch(() => {})
}

// 工具函数
const formatTime = (time: string) => {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

onMounted(() => {
  loadUsers()
  loadAllRoles()
})
</script>

<style scoped>
.user-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-section {
  margin-bottom: 15px;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 4px;
}

.text-gray {
  color: #909399;
}

.perm-tree-wrapper {
  max-height: 400px;
  overflow-y: auto;
}
</style>