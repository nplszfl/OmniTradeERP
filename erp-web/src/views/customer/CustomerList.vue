<template>
  <div class="customer-page">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon" style="background: #409eff">
            <el-icon :size="24"><User /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.totalCustomers }}</div>
            <div class="stat-label">客户总数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon" style="background: #e6a23c">
            <el-icon :size="24"><Star /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.vipCount }}</div>
            <div class="stat-label">VIP客户</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon" style="background: #67c23a">
            <el-icon :size="24"><CircleCheck /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.activeCount }}</div>
            <div class="stat-label">活跃客户</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon" style="background: #f56c6c">
            <el-icon :size="24"><Money /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">${{ formatAmount(stats.totalRevenue) }}</div>
            <div class="stat-label">总消费</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 搜索筛选 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="姓名/邮箱/电话" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="平台">
          <el-select v-model="searchForm.platform" placeholder="全部平台" clearable style="width: 120px">
            <el-option label="亚马逊" value="amazon" />
            <el-option label="eBay" value="ebay" />
            <el-option label="Shopee" value="shopee" />
            <el-option label="Lazada" value="lazada" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户等级">
          <el-select v-model="searchForm.level" placeholder="全部等级" clearable style="width: 120px">
            <el-option label="VIP" value="VIP" />
            <el-option label="普通" value="NORMAL" />
            <el-option label="新客户" value="NEW" />
          </el-select>
        </el-form-item>
        <el-form-item label="国家">
          <el-input v-model="searchForm.country" placeholder="国家名称" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="Plus" @click="handleAdd">新增客户</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 客户列表 -->
    <el-card class="table-card" shadow="never">
      <el-table v-loading="loading" :data="customerList" stripe>
        <el-table-column prop="name" label="客户姓名" width="120" fixed>
          <template #default="{ row }">
            <el-link type="primary" @click="handleViewDetail(row)">{{ row.name }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" width="180" show-overflow-tooltip />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column prop="platform" label="平台" width="90">
          <template #default="{ row }">
            <el-tag :type="getPlatformType(row.platform)" size="small">{{ getPlatformName(row.platform) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="level" label="等级" width="80">
          <template #default="{ row }">
            <el-tag :type="getLevelType(row.level)" size="small">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalOrders" label="订单数" width="80" />
        <el-table-column prop="totalSpent" label="累计消费" width="120">
          <template #default="{ row }">
            ${{ formatAmount(row.totalSpent) }}
          </template>
        </el-table-column>
        <el-table-column prop="avgOrderValue" label="客单价" width="100">
          <template #default="{ row }">
            ${{ formatAmount(row.avgOrderValue) }}
          </template>
        </el-table-column>
        <el-table-column prop="country" label="国家" width="100" />
        <el-table-column prop="lastOrderAt" label="最近下单" width="150">
          <template #default="{ row }">
            {{ row.lastOrderAt ? formatTime(row.lastOrderAt) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleViewDetail(row)">详情</el-button>
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadCustomers"
        @current-change="loadCustomers"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <!-- 客户详情弹窗 -->
    <el-dialog v-model="detailVisible" title="客户详情" width="800px" append-to-body>
      <el-descriptions :column="2" border v-if="currentCustomer">
        <el-descriptions-item label="客户姓名">{{ currentCustomer.name }}</el-descriptions-item>
        <el-descriptions-item label="客户等级">
          <el-tag :type="getLevelType(currentCustomer.level)">{{ currentCustomer.level }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ currentCustomer.email }}</el-descriptions-item>
        <el-descriptions-item label="电话">{{ currentCustomer.phone }}</el-descriptions-item>
        <el-descriptions-item label="平台">
          <el-tag :type="getPlatformType(currentCustomer.platform)">{{ getPlatformName(currentCustomer.platform) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="国家">{{ currentCustomer.country }}</el-descriptions-item>
        <el-descriptions-item label="省份">{{ currentCustomer.state || '-' }}</el-descriptions-item>
        <el-descriptions-item label="城市">{{ currentCustomer.city || '-' }}</el-descriptions-item>
        <el-descriptions-item label="详细地址" :span="2">{{ currentCustomer.address || '-' }}</el-descriptions-item>
        <el-descriptions-item label="订单总数">{{ currentCustomer.totalOrders }}</el-descriptions-item>
        <el-descriptions-item label="累计消费">${{ formatAmount(currentCustomer.totalSpent) }}</el-descriptions-item>
        <el-descriptions-item label="平均客单价">${{ formatAmount(currentCustomer.avgOrderValue) }}</el-descriptions-item>
        <el-descriptions-item label="最近下单">{{ currentCustomer.lastOrderAt ? formatTime(currentCustomer.lastOrderAt) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户备注" :span="2">{{ currentCustomer.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="currentCustomer.status === 'ACTIVE' ? 'success' : 'info'">{{ currentCustomer.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(currentCustomer.createTime) }}</el-descriptions-item>
      </el-descriptions>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑客户弹窗 -->
    <el-dialog v-model="formVisible" :title="isEdit ? '编辑客户' : '新增客户'" width="600px" append-to-body>
      <el-form :model="form" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item label="客户姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入客户姓名" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入电话" />
        </el-form-item>
        <el-form-item label="平台" prop="platform">
          <el-select v-model="form.platform" placeholder="请选择平台" style="width: 100%">
            <el-option label="亚马逊" value="amazon" />
            <el-option label="eBay" value="ebay" />
            <el-option label="Shopee" value="shopee" />
            <el-option label="Lazada" value="lazada" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户等级" prop="level">
          <el-select v-model="form.level" placeholder="请选择等级" style="width: 100%">
            <el-option label="VIP" value="VIP" />
            <el-option label="普通" value="NORMAL" />
            <el-option label="新客户" value="NEW" />
          </el-select>
        </el-form-item>
        <el-form-item label="国家">
          <el-input v-model="form.country" placeholder="请输入国家" />
        </el-form-item>
        <el-form-item label="省份">
          <el-input v-model="form.state" placeholder="请输入省份" />
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="form.city" placeholder="请输入城市" />
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model="form.address" type="textarea" :rows="2" placeholder="请输入详细地址" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Refresh, Plus, User, Star, CircleCheck, Money } from '@element-plus/icons-vue'
import { customerApi, type Customer, type CustomerStats } from '@/api/customer'
import dayjs from 'dayjs'
import { unwrapPage } from '@/utils/response'

const loading = ref(false)
const customerList = ref<Customer[]>([])
const detailVisible = ref(false)
const formVisible = ref(false)
const isEdit = ref(false)
const currentCustomer = ref<Customer | null>(null)
const formRef = ref<FormInstance>()

// 统计数据
const stats = reactive<CustomerStats>({
  totalCustomers: 0,
  vipCount: 0,
  activeCount: 0,
  totalRevenue: 0,
  avgSpent: 0
})

// 搜索表单
const searchForm = reactive({
  keyword: '',
  platform: '',
  level: '',
  country: ''
})

// 分页
const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

// 表单
const form = reactive<Customer>({
  id: 0,
  name: '',
  email: '',
  phone: '',
  platform: '',
  platformCustomerId: '',
  country: '',
  state: '',
  city: '',
  address: '',
  postalCode: '',
  level: 'NORMAL',
  totalOrders: 0,
  totalSpent: 0,
  avgOrderValue: 0,
  lastOrderAt: '',
  remark: '',
  status: 'ACTIVE',
  createTime: '',
  updateTime: ''
})

// 表单验证规则
const formRules = {
  name: [{ required: true, message: '请输入客户姓名', trigger: 'blur' }],
  email: [{ required: true, message: '请输入邮箱', trigger: 'blur' }],
  platform: [{ required: true, message: '请选择平台', trigger: 'change' }]
}

// 加载客户列表
const loadCustomers = async () => {
  loading.value = true
  try {
    const res = await customerApi.getList({
      current: pagination.current,
      size: pagination.size,
      keyword: searchForm.keyword || undefined,
      platform: searchForm.platform || undefined,
      level: searchForm.level || undefined,
      country: searchForm.country || undefined
    })
    const pageData = unwrapPage(res)
    customerList.value = pageData.records || []
    pagination.total = pageData.total || 0
  } catch (error) {
    console.error('加载客户失败', error)
  } finally {
    loading.value = false
  }
}

// 加载统计数据
const loadStats = async () => {
  try {
    const res = await customerApi.getStats()
    Object.assign(stats, res)
  } catch (error) {
    console.error('加载统计失败', error)
  }
}

// 搜索
const handleSearch = () => {
  pagination.current = 1
  loadCustomers()
}

// 重置
const handleReset = () => {
  searchForm.keyword = ''
  searchForm.platform = ''
  searchForm.level = ''
  searchForm.country = ''
  handleSearch()
}

// 新增
const handleAdd = () => {
  isEdit.value = false
  resetForm()
  formVisible.value = true
}

// 编辑
const handleEdit = (customer: Customer) => {
  isEdit.value = true
  Object.assign(form, customer)
  formVisible.value = true
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (isEdit.value) {
          await customerApi.update(form.id, form)
          ElMessage.success('更新成功')
        } else {
          await customerApi.create(form)
          ElMessage.success('创建成功')
        }
        formVisible.value = false
        loadCustomers()
        loadStats()
      } catch (error) {
        console.error('操作失败', error)
      }
    }
  })
}

// 重置表单
const resetForm = () => {
  Object.assign(form, {
    id: 0,
    name: '',
    email: '',
    phone: '',
    platform: '',
    platformCustomerId: '',
    country: '',
    state: '',
    city: '',
    address: '',
    postalCode: '',
    level: 'NORMAL',
    totalOrders: 0,
    totalSpent: 0,
    avgOrderValue: 0,
    lastOrderAt: '',
    remark: '',
    status: 'ACTIVE'
  })
}

// 查看详情
const handleViewDetail = (customer: Customer) => {
  currentCustomer.value = customer
  detailVisible.value = true
}

// 删除
const handleDelete = (customer: Customer) => {
  ElMessageBox.confirm(`确定删除客户 ${customer.name} 吗？`, '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      await customerApi.delete(customer.id)
      ElMessage.success('删除成功')
      loadCustomers()
      loadStats()
    } catch (error) {
      console.error('删除失败', error)
    }
  })
}

// 平台名称
const getPlatformName = (platform: string) => {
  const map: Record<string, string> = {
    amazon: '亚马逊',
    ebay: 'eBay',
    shopee: 'Shopee',
    lazada: 'Lazada'
  }
  return map[platform] || platform
}

// 平台标签类型
const getPlatformType = (platform: string) => {
  const map: Record<string, string> = {
    amazon: 'success',
    ebay: '',
    shopee: 'warning',
    lazada: 'info'
  }
  return map[platform] || ''
}

// 客户等级类型
const getLevelType = (level: string) => {
  const map: Record<string, string> = {
    VIP: 'danger',
    NORMAL: 'success',
    NEW: 'info'
  }
  return map[level] || ''
}

// 格式化金额
const formatAmount = (amount: number) => {
  return amount ? amount.toFixed(2) : '0.00'
}

// 格式化时间
const formatTime = (time: string) => {
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

onMounted(() => {
  loadCustomers()
  loadStats()
})
</script>

<style scoped>
.customer-page {
  padding: 20px;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 20px;
}

.stat-icon {
  width: 50px;
  height: 50px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  margin-right: 15px;
}

.stat-content {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
}

.stat-label {
  color: #999;
  font-size: 14px;
}

.search-card {
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}
</style>