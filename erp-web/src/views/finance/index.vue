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

    <!-- 筛选和列表 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>流水记录</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">新增流水</el-button>
        </div>
      </template>

      <!-- 筛选条件 -->
      <div class="filter-section">
        <el-form :inline="true" :model="filterForm" class="filter-form">
          <el-form-item label="账户">
            <el-select v-model="filterForm.accountId" placeholder="全部账户" clearable style="width: 150px">
              <el-option v-for="acc in accounts" :key="acc.id" :label="acc.name" :value="acc.id" />
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
            <el-button type="primary" link @click="handleView(row)">详情</el-button>
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

    <!-- 新增/详情对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="流水类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio label="income">收入</el-radio>
            <el-radio label="expense">支出</el-radio>
            <el-radio label="refund">退款</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="form.amount" :min="0" :precision="2" :step="100" style="width: 200px" />
        </el-form-item>
        <el-form-item label="账户" prop="accountId">
          <el-select v-model="form.accountId" placeholder="请选择账户" style="width: 200px">
            <el-option v-for="acc in accounts" :key="acc.id" :label="acc.name" :value="acc.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 200px">
            <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联订单">
          <el-input v-model="form.orderNo" placeholder="可选" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { Plus, Search, Refresh, TrendCharts, Wallet, Money, Document } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import * as echarts from 'echarts'

const loading = ref(false)
const submitLoading = ref(false)
const flowList = ref<any[]>([])
const accounts = ref<any[]>([])
const categories = ref<any[]>([])

// 统计卡片数据
const stats = ref({
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
  accountId: null as number | null,
  type: '',
  dateRange: [] as string[]
})

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('新增流水')
const formRef = ref()
const form = reactive({
  id: null as number | null,
  type: 'income',
  amount: 0,
  accountId: null as number | null,
  categoryId: null as number | null,
  orderNo: '',
  remark: ''
})

const formRules = {
  type: [{ required: true, message: '请选择流水类型', trigger: 'change' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  accountId: [{ required: true, message: '请选择账户', trigger: 'change' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }]
}

// 加载统计数据
const loadStats = async () => {
  // TODO: 调用API
  // const res = await financeApi.getStatistics()
  // stats.value = res.data

  // 模拟数据
  stats.value = {
    totalIncome: 286500.00,
    totalExpense: 145200.00,
    profit: 141300.00,
    recordCount: 328
  }
}

// 加载账户列表
const loadAccounts = async () => {
  // TODO: 调用API
  // const res = await financeApi.getAccounts()
  // accounts.value = res.data

  // 模拟数据
  accounts.value = [
    { id: 1, name: '主账户' },
    { id: 2, name: 'PayPal' },
    { id: 3, name: '万里汇' }
  ]
}

// 加载分类列表
const loadCategories = async () => {
  // TODO: 调用API
  // const res = await financeApi.getCategories()
  // categories.value = res.data

  // 模拟数据
  categories.value = [
    { id: 1, name: '平台销售收入' },
    { id: 2, name: '商品采购' },
    { id: 3, name: '物流费用' },
    { id: 4, name: '平台费用' },
    { id: 5, name: '营销推广' }
  ]
}

// 加载流水列表
const loadFlows = async () => {
  loading.value = true
  // TODO: 调用API
  // const params = {
  //   ...filterForm,
  //   page: pagination.current,
  //   size: pagination.size
  // }
  // const res = await financeApi.getFlows(params)
  // flowList.value = res.data.records
  // pagination.total = res.data.total

  // 模拟数据
  setTimeout(() => {
    flowList.value = generateMockFlows()
    pagination.total = 100
    loading.value = false
  }, 300)
}

// 生成模拟数据
const generateMockFlows = () => {
  const types = ['income', 'expense', 'refund']
  const categories = ['平台销售收入', '商品采购', '物流费用', '平台费用', '营销推广']
  const accounts = ['主账户', 'PayPal', '万里汇']
  
  return Array.from({ length: 20 }, (_, i) => {
    const type = types[Math.floor(Math.random() * 3)]
    const amount = Math.floor(Math.random() * 10000) + 100
    return {
      id: i + 1,
      recordNo: `FIN${Date.now()}${i}`,
      type,
      amount,
      orderNo: `ORD${20260310000 + i}`,
      categoryName: categories[Math.floor(Math.random() * categories.length)],
      accountName: accounts[Math.floor(Math.random() * accounts.length)],
      remark: '示例备注',
      createTime: dayjs().subtract(i, 'day').format('YYYY-MM-DD HH:mm:ss')
    }
  })
}

// 加载图表数据
const loadChartData = async () => {
  // TODO: 调用API获取趋势数据
  updateTrendChart()
  updatePieChart()
}

// 更新趋势图
const updateTrendChart = () => {
  if (!trendChartRef.value) return
  
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }

  const days = parseInt(chartPeriod.value)
  const dates = Array.from({ length: days }, (_, i) => 
    dayjs().subtract(days - 1 - i, 'day').format('MM-DD')
  )
  
  const incomeData = dates.map(() => Math.floor(Math.random() * 20000) + 5000)
  const expenseData = dates.map(() => Math.floor(Math.random() * 10000) + 2000)

  const option = {
    tooltip: { trigger: 'axis' },
    legend: { data: ['收入', '支出'] },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value', axisLabel: { formatter: '¥{value}' } },
    series: [
      {
        name: '收入',
        type: 'line',
        data: incomeData,
        smooth: true,
        areaStyle: { opacity: 0.3 },
        itemStyle: { color: '#67c23a' }
      },
      {
        name: '支出',
        type: 'line',
        data: expenseData,
        smooth: true,
        areaStyle: { opacity: 0.3 },
        itemStyle: { color: '#f56c6c' }
      }
    ]
  }

  trendChart.setOption(option)
}

// 更新饼图
const updatePieChart = () => {
  if (!pieChartRef.value) return
  
  if (!pieChart) {
    pieChart = echarts.init(pieChartRef.value)
  }

  const option = {
    tooltip: { trigger: 'item', formatter: '{b}: ¥{c} ({d}%)' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}' },
      data: [
        { value: 154800, name: '平台销售收入' },
        { value: 58000, name: '商品采购' },
        { value: 32000, name: '物流费用' },
        { value: 28000, name: '平台费用' },
        { value: 15200, name: '营销推广' }
      ]
    }]
  }

  pieChart.setOption(option)
}

// 搜索
const handleSearch = () => {
  pagination.current = 1
  loadFlows()
}

// 重置
const handleReset = () => {
  filterForm.accountId = null
  filterForm.type = ''
  filterForm.dateRange = []
  handleSearch()
}

// 新增
const handleAdd = () => {
  dialogTitle.value = '新增流水'
  form.id = null
  form.type = 'income'
  form.amount = 0
  form.accountId = null
  form.categoryId = null
  form.orderNo = ''
  form.remark = ''
  dialogVisible.value = true
}

// 查看详情
const handleView = (row: any) => {
  dialogTitle.value = '流水详情'
  form.id = row.id
  form.type = row.type
  form.amount = row.amount
  form.accountId = row.accountId
  form.categoryId = row.categoryId
  form.orderNo = row.orderNo
  form.remark = row.remark
  dialogVisible.value = true
}

// 提交
const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  // TODO: 调用API保存
  setTimeout(() => {
    ElMessage.success('保存成功')
    dialogVisible.value = false
    submitLoading.value = false
    loadFlows()
    loadStats()
  }, 500)
}

// 工具函数
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
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

const formatNumber = (num: number) => {
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 窗口大小改变时重新渲染图表
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
</style>