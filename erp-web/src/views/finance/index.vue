<template>
  <div class="finance-page">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card income">
          <div class="stat-content">
            <div class="stat-icon">
              <el-icon><TrendCharts /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">¥{{ formatNumber(stats.totalIncome) }}</div>
              <div class="stat-label">总收入</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card expense">
          <div class="stat-content">
            <div class="stat-icon">
              <el-icon><Wallet /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">¥{{ formatNumber(stats.totalExpense) }}</div>
              <div class="stat-label">总支出</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card profit">
          <div class="stat-content">
            <div class="stat-icon">
              <el-icon><Money /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value" :style="{ color: stats.profit >= 0 ? '#67c23a' : '#f56c6c' }">
                ¥{{ formatNumber(stats.profit) }}
              </div>
              <div class="stat-label">净利润</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card count">
          <div class="stat-content">
            <div class="stat-icon">
              <el-icon><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.recordCount }}</div>
              <div class="stat-label">交易笔数</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20" class="charts-row">
      <el-col :span="16">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>收支趋势</span>
              <el-radio-group v-model="chartPeriod" size="small" @change="loadChartData">
                <el-radio-button label="7">近7天</el-radio-button>
                <el-radio-button label="30">近30天</el-radio-button>
                <el-radio-button label="90">近90天</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="trendChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>
            <span>收支构成</span>
          </template>
          <div ref="pieChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 账户管理 -->
    <el-card shadow="never" class="account-card">
      <template #header>
        <div class="card-header">
          <span>账户管理</span>
          <el-button type="primary" size="small" :icon="Plus" @click="handleAddAccount">添加账户</el-button>
        </div>
      </template>
      <el-table :data="accounts" stripe style="width: 100%">
        <el-table-column prop="accountName" label="账户名称" width="150" />
        <el-table-column prop="accountNo" label="账号" width="180" />
        <el-table-column prop="accountType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag>{{ getAccountType(row.accountType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bankName" label="银行/渠道" width="150" />
        <el-table-column prop="currency" label="币种" width="80" />
        <el-table-column prop="balance" label="余额" width="120" align="right">
          <template #default="{ row }">
            <span class="text-bold text-primary">¥{{ formatNumber(row.balance) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="isDefault" label="默认" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault === 1" type="success" size="small">是</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEditAccount(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDeleteAccount(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 流水记录 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>流水记录</span>
          <el-button type="primary" :icon="Plus" @click="handleAddFlow">新增流水</el-button>
        </div>
      </template>

      <!-- 筛选条件 -->
      <div class="filter-section">
        <el-form :inline="true" :model="filterForm" class="filter-form">
          <el-form-item label="账户">
            <el-select v-model="filterForm.accountId" placeholder="全部账户" clearable style="width: 150px">
              <el-option v-for="acc in accounts" :key="acc.id" :label="acc.accountName" :value="acc.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="filterForm.type" placeholder="全部类型" clearable style="width: 120px">
              <el-option label="收入" value="income" />
              <el-option label="支出" value="expense" />
              <el-option label="退款" value="refund" />
            </el-select>
          </el-form-item>
          <el-form-item label="日期">
            <el-date-picker
              v-model="filterForm.dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 240px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="flowList" stripe style="width: 100%">
        <el-table-column prop="recordNo" label="流水号" width="180" />
        <el-table-column prop="createTime" label="时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="getFlowTypeTag(row.type)">
              {{ getFlowTypeText(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orderNo" label="关联订单" width="150" />
        <el-table-column prop="amount" label="金额" width="120" align="right">
          <template #default="{ row }">
            <span :style="{ color: getAmountColor(row.type), fontWeight: 'bold' }">
              {{ formatAmount(row.amount, row.type) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分类" width="120" />
        <el-table-column prop="accountName" label="账户" width="100" />
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleViewFlow(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px; justify-content: flex-end"
        @size-change="loadFlows"
        @current-change="loadFlows"
      />
    </el-card>

    <!-- 账户对话框 -->
    <el-dialog v-model="accountDialogVisible" :title="accountDialogTitle" width="500px">
      <el-form ref="accountFormRef" :model="accountForm" :rules="accountFormRules" label-width="100px">
        <el-form-item label="账户名称" prop="accountName">
          <el-input v-model="accountForm.accountName" placeholder="请输入账户名称" />
        </el-form-item>
        <el-form-item label="账号" prop="accountNo">
          <el-input v-model="accountForm.accountNo" placeholder="请输入账号" />
        </el-form-item>
        <el-form-item label="账户类型" prop="accountType">
          <el-select v-model="accountForm.accountType" placeholder="请选择类型" style="width: 100%">
            <el-option label="银行账户" value="bank" />
            <el-option label="支付宝" value="alipay" />
            <el-option label="PayPal" value="paypal" />
            <el-option label="万里汇" value="worldfirst" />
            <el-option label="派安盈" value="payoneer" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="银行/渠道">
          <el-input v-model="accountForm.bankName" placeholder="请输入银行或支付渠道" />
        </el-form-item>
        <el-form-item label="币种">
          <el-select v-model="accountForm.currency" placeholder="请选择币种" style="width: 100%">
            <el-option label="人民币 (CNY)" value="CNY" />
            <el-option label="美元 (USD)" value="USD" />
            <el-option label="欧元 (EUR)" value="EUR" />
            <el-option label="英镑 (GBP)" value="GBP" />
          </el-select>
        </el-form-item>
        <el-form-item label="初始余额">
          <el-input-number v-model="accountForm.balance" :min="0" :precision="2" style="width: 200px" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="accountForm.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="accountForm.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="accountDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="accountSubmitLoading" @click="handleAccountSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 流水对话框 -->
    <el-dialog v-model="flowDialogVisible" :title="flowDialogTitle" width="600px">
      <el-form ref="flowFormRef" :model="flowForm" :rules="flowFormRules" label-width="100px">
        <el-form-item label="流水类型" prop="type">
          <el-radio-group v-model="flowForm.type">
            <el-radio label="income">收入</el-radio>
            <el-radio label="expense">支出</el-radio>
            <el-radio label="refund">退款</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="flowForm.amount" :min="0" :precision="2" :step="100" style="width: 200px" />
        </el-form-item>
        <el-form-item label="账户" prop="accountId">
          <el-select v-model="flowForm.accountId" placeholder="请选择账户" style="width: 200px">
            <el-option v-for="acc in accounts" :key="acc.id" :label="acc.accountName" :value="acc.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="flowForm.categoryId" placeholder="请选择分类" style="width: 200px">
            <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联订单">
          <el-input v-model="flowForm.orderNo" placeholder="可选" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="flowForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="flowDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="flowSubmitLoading" @click="handleFlowSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { Plus, Search, Refresh, TrendCharts, Wallet, Money, Document } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import * as echarts from 'echarts'
import { 
  financeAccountApi, financeFlowApi, financeCategoryApi, 
  type FinanceAccount, type FinanceFlow, type FinanceCategory,
  type FinanceStatistics 
} from '@/api/finance'
import { unwrapPage } from '@/utils/response'

const loading = ref(false)
const flowList = ref<FinanceFlow[]>([])
const accounts = ref<FinanceAccount[]>([])
const categories = ref<FinanceCategory[]>([])

// 统计卡片数据
const stats = ref<FinanceStatistics>({
  totalIncome: 0,
  totalExpense: 0,
  profit: 0,
  recordCount: 0
})

// 图表引用
const trendChartRef = ref<HTMLDivElement>()
const pieChartRef = ref<HTMLDivElement>()
let trendChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null

// 图表周期
const chartPeriod = ref('30')

// 分页
const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

// 筛选表单
const filterForm = reactive({
  accountId: undefined as string | undefined,
  type: '',
  dateRange: [] as string[]
})

// 账户对话框
const accountDialogVisible = ref(false)
const accountDialogTitle = ref('添加账户')
const accountFormRef = ref()
const accountSubmitLoading = ref(false)
const accountForm = reactive({
  id: undefined as string | undefined,
  accountName: '',
  accountNo: '',
  accountType: 'bank',
  bankName: '',
  currency: 'CNY',
  balance: 0,
  isDefault: 0,
  status: 1
})
const accountFormRules = {
  accountName: [{ required: true, message: '请输入账户名称', trigger: 'blur' }],
  accountNo: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  accountType: [{ required: true, message: '请选择账户类型', trigger: 'change' }]
}

// 流水对话框
const flowDialogVisible = ref(false)
const flowDialogTitle = ref('新增流水')
const flowFormRef = ref()
const flowSubmitLoading = ref(false)
const flowForm = reactive({
  id: undefined as string | undefined,
  type: 'income',
  amount: 0,
  accountId: undefined as string | undefined,
  categoryId: undefined as string | undefined,
  orderNo: '',
  remark: ''
})
const flowFormRules = {
  type: [{ required: true, message: '请选择流水类型', trigger: 'change' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  accountId: [{ required: true, message: '请选择账户', trigger: 'change' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }]
}

// 加载统计数据
const loadStats = async () => {
  try {
    const params: any = {}
    if (filterForm.dateRange?.length === 2) {
      params.startDate = filterForm.dateRange[0]
      params.endDate = filterForm.dateRange[1]
    }
    const res = await financeFlowApi.getStatistics(params)
    stats.value = res || { totalIncome: 0, totalExpense: 0, profit: 0, recordCount: 0 }
  } catch (error) {
    console.error('加载统计失败:', error)
  }
}

// 加载账户列表
const loadAccounts = async () => {
  try {
    const res = await financeAccountApi.getAccounts()
    accounts.value = res || []
  } catch (error) {
    console.error('加载账户列表失败:', error)
  }
}

// 加载分类列表
const loadCategories = async () => {
  try {
    const res = await financeCategoryApi.getCategories()
    categories.value = res || []
  } catch (error) {
    console.error('加载分类列表失败:', error)
  }
}

// 加载流水列表
const loadFlows = async () => {
  loading.value = true
  try {
    const params = {
      current: pagination.current,
      size: pagination.size,
      accountId: filterForm.accountId || undefined,
      type: filterForm.type || undefined,
      startDate: filterForm.dateRange?.[0],
      endDate: filterForm.dateRange?.[1]
    }
    const res = await financeFlowApi.getFlows(params)
    const pageData = unwrapPage(res)
    flowList.value = pageData.records || []
    pagination.total = pageData.total || 0
  } catch (error) {
    console.error('加载流水列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 加载图表数据
const loadChartData = async () => {
  try {
    const days = parseInt(chartPeriod.value)
    const trendData = await financeFlowApi.getTrendData(days)
    const categoryData = await financeFlowApi.getCategoryData()
    updateTrendChart(trendData || [])
    updatePieChart(categoryData || [])
  } catch (error) {
    console.error('加载图表数据失败:', error)
    updateTrendChart([])
    updatePieChart([])
  }
}

// 更新趋势图
const updateTrendChart = (data: any[]) => {
  if (!trendChartRef.value) return
  
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }

  const option = {
    tooltip: { trigger: 'axis' },
    legend: { data: ['收入', '支出'] },
    xAxis: { type: 'category', data: data.map(d => d.date) },
    yAxis: { type: 'value', axisLabel: { formatter: '¥{value}' } },
    series: [
      {
        name: '收入',
        type: 'line',
        data: data.map(d => d.income),
        smooth: true,
        areaStyle: { opacity: 0.3 },
        itemStyle: { color: '#67c23a' }
      },
      {
        name: '支出',
        type: 'line',
        data: data.map(d => d.expense),
        smooth: true,
        areaStyle: { opacity: 0.3 },
        itemStyle: { color: '#f56c6c' }
      }
    ]
  }

  trendChart.setOption(option)
}

// 更新饼图
const updatePieChart = (data: any[]) => {
  if (!pieChartRef.value) return
  
  if (!pieChart) {
    pieChart = echarts.init(pieChartRef.value)
  }

  const colors = ['#67c23a', '#f56c6c', '#e6a23c', '#409eff', '#909399']
  
  const option = {
    tooltip: { trigger: 'item', formatter: '{b}: ¥{c} ({d}%)' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}' },
      data: data.map((d, i) => ({
        value: d.amount,
        name: d.categoryName,
        itemStyle: { color: colors[i % colors.length] }
      }))
    }]
  }

  pieChart.setOption(option)
}

// 搜索
const handleSearch = () => {
  pagination.current = 1
  loadFlows()
  loadStats()
}

// 重置
const handleReset = () => {
  filterForm.accountId = undefined
  filterForm.type = ''
  filterForm.dateRange = []
  handleSearch()
}

// 账户操作
const handleAddAccount = () => {
  accountDialogTitle.value = '添加账户'
  accountForm.id = undefined
  accountForm.accountName = ''
  accountForm.accountNo = ''
  accountForm.accountType = 'bank'
  accountForm.bankName = ''
  accountForm.currency = 'CNY'
  accountForm.balance = 0
  accountForm.isDefault = 0
  accountForm.status = 1
  accountDialogVisible.value = true
}

const handleEditAccount = (row: FinanceAccount) => {
  accountDialogTitle.value = '编辑账户'
  accountForm.id = row.id
  accountForm.accountName = row.accountName
  accountForm.accountNo = row.accountNo
  accountForm.accountType = row.accountType
  accountForm.bankName = row.bankName || ''
  accountForm.currency = row.currency
  accountForm.balance = row.balance
  accountForm.isDefault = row.isDefault
  accountForm.status = row.status
  accountDialogVisible.value = true
}

const handleAccountSubmit = async () => {
  const valid = await accountFormRef.value?.validate().catch(() => false)
  if (!valid) return

  accountSubmitLoading.value = true
  try {
    if (accountForm.id) {
      await financeAccountApi.updateAccount(accountForm)
      ElMessage.success('更新成功')
    } else {
      await financeAccountApi.createAccount(accountForm)
      ElMessage.success('创建成功')
    }
    accountDialogVisible.value = false
    loadAccounts()
  } catch (error) {
    console.error('保存账户失败:', error)
  } finally {
    accountSubmitLoading.value = false
  }
}

const handleDeleteAccount = (row: FinanceAccount) => {
  ElMessageBox.confirm('确定删除该账户吗？', '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      await financeAccountApi.deleteAccount(row.id)
      ElMessage.success('删除成功')
      loadAccounts()
    } catch (error) {
      console.error('删除账户失败:', error)
    }
  }).catch(() => {})
}

// 流水操作
const handleAddFlow = () => {
  flowDialogTitle.value = '新增流水'
  flowForm.id = undefined
  flowForm.type = 'income'
  flowForm.amount = 0
  flowForm.accountId = undefined
  flowForm.categoryId = undefined
  flowForm.orderNo = ''
  flowForm.remark = ''
  flowDialogVisible.value = true
}

const handleViewFlow = (row: FinanceFlow) => {
  flowDialogTitle.value = '流水详情'
  flowForm.id = row.id
  flowForm.type = row.type
  flowForm.amount = row.amount
  flowForm.accountId = row.accountId
  flowForm.categoryId = row.categoryId
  flowForm.orderNo = row.orderNo || ''
  flowForm.remark = row.remark || ''
  flowDialogVisible.value = true
}

const handleFlowSubmit = async () => {
  const valid = await flowFormRef.value?.validate().catch(() => false)
  if (!valid) return

  flowSubmitLoading.value = true
  try {
    if (flowForm.id) {
      await financeFlowApi.updateFlow(flowForm)
      ElMessage.success('更新成功')
    } else {
      await financeFlowApi.createFlow(flowForm)
      ElMessage.success('创建成功')
    }
    flowDialogVisible.value = false
    loadFlows()
    loadStats()
  } catch (error) {
    console.error('保存流水失败:', error)
  } finally {
    flowSubmitLoading.value = false
  }
}

// 工具函数
const getAccountType = (type: string) => {
  const map: Record<string, string> = {
    bank: '银行账户', alipay: '支付宝', paypal: 'PayPal',
    worldfirst: '万里汇', payoneer: '派安盈', other: '其他'
  }
  return map[type] || type
}

const getFlowTypeTag = (type: string) => {
  if (type === 'income') return 'success'
  if (type === 'refund') return 'warning'
  return 'danger'
}

const getFlowTypeText = (type: string) => {
  const map: Record<string, string> = { income: '收入', expense: '支出', refund: '退款' }
  return map[type] || type
}

const getAmountColor = (type: string) => {
  if (type === 'income') return '#67c23a'
  if (type === 'refund') return '#e6a23c'
  return '#f56c6c'
}

const formatAmount = (amount: number, type: string) => {
  const prefix = type === 'income' ? '+' : '-'
  return prefix + '¥' + amount.toFixed(2)
}

const formatTime = (time: string) => {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm') : '-'
}

const formatNumber = (num: number) => {
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const resizeCharts = () => {
  trendChart?.resize()
  pieChart?.resize()
}

onMounted(async () => {
  await Promise.all([loadStats(), loadAccounts(), loadCategories()])
  await loadFlows()
  
  await nextTick()
  loadChartData()
  
  window.addEventListener('resize', resizeCharts)
})

onUnmounted(() => {
  trendChart?.dispose()
  pieChart?.dispose()
  window.removeEventListener('resize', resizeCharts)
})
</script>

<style scoped>
.finance-page {
  padding: 20px;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  height: 100px;
}

.stat-card.income .stat-icon { background: #67c23a; }
.stat-card.expense .stat-icon { background: #f56c6c; }
.stat-card.profit .stat-icon { background: #409eff; }
.stat-card.count .stat-icon { background: #e6a23c; }

.stat-content {
  display: flex;
  align-items: center;
  gap: 15px;
}

.stat-icon {
  width: 50px;
  height: 50px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: white;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 22px;
  font-weight: bold;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.charts-row {
  margin-bottom: 20px;
}

.account-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart-container {
  height: 300px;
}

.filter-section {
  margin-bottom: 20px;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 4px;
}

.filter-form {
  margin-bottom: 0;
}

.text-bold { font-weight: bold; }
.text-primary { color: #409eff; }
</style>