<template>
  <div class="dashboard-container">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #409EFF;">
              <el-icon><ShoppingCart /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.todayOrders }}</div>
              <div class="stat-label">今日订单</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #67C23A;">
              <el-icon><Money /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">¥{{ formatMoney(stats.todayAmount) }}</div>
              <div class="stat-label">今日销售额</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #E6A23C;">
              <el-icon><Box /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.pendingShipment }}</div>
              <div class="stat-label">待发货订单</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #F56C6C;">
              <el-icon><Warning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.lowStock }}</div>
              <div class="stat-label">库存预警</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20" class="charts-row">
      <!-- 销售趋势图 -->
      <el-col :span="16">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>销售趋势</span>
              <el-radio-group v-model="salesChartPeriod" size="small">
                <el-radio-button label="7">近7天</el-radio-button>
                <el-radio-button label="30">近30天</el-radio-button>
                <el-radio-button label="90">近90天</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="salesChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- 平台占比 -->
      <el-col :span="8">
        <el-card>
          <template #header>
            <span>平台销售占比</span>
          </template>
          <div ref="platformChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 订单状态分布 -->
    <el-row :gutter="20" class="charts-row">
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>订单状态分布</span>
          </template>
          <div ref="orderStatusChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- 最近订单 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>最近订单</span>
              <!-- 路由是 /order（不是 /orders）—— 写错的话点"查看更多"会跳到不存在的路由 -->
              <el-button type="text" @click="router.push('/order')">查看更多</el-button>
            </div>
          </template>
          <el-table :data="recentOrders" style="width: 100%" empty-text="暂无订单">
            <el-table-column prop="orderId" label="订单号" width="120" />
            <el-table-column prop="platform" label="平台" width="90" />
            <el-table-column prop="amount" label="金额" width="110" align="right">
              <template #default="scope">
                ¥{{ formatMoney(scope.row.amount) }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态">
              <template #default="scope">
                <el-tag :type="getStatusType(scope.row.status)">
                  {{ getStatusText(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="orderTime" label="时间" min-width="150" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { ShoppingCart, Money, Box, Warning } from '@element-plus/icons-vue'
import { analyticsApi, type DashboardOrderStatus, type TrendPoint } from '@/api/analytics'

/**
 * 首页数据看板
 *
 * ⚠️ 之前这个页面**整页都是写死的假数据**：
 *    统计卡片写死 (123 / 45678.90 / 15 / 8)、最近订单写死 5 条、
 *    销售趋势写死 7 个点、平台占比写死 5 个数、订单状态分布写死 [5,15,45,30,3]。
 *    后端 erp-analytics-service 明明已经接了真实数据库，
 *    前端却一直没接 —— 看板看着"有数据"，全是编的。
 *
 * 现在全部改成调 /api/analytics/**，取不到就显示 0 / 空，
 * **不再回退到 mock**（回退到 mock 只会把 404/500 全部掩盖掉）。
 */
const router = useRouter()

/** 统计数据（全部来自真实接口） */
const stats = ref({
  todayOrders: 0,
  todayAmount: 0,
  pendingShipment: 0,
  lowStock: 0
})

// 图表引用
const salesChartRef = ref<HTMLDivElement>()
const platformChartRef = ref<HTMLDivElement>()
const orderStatusChartRef = ref<HTMLDivElement>()

// 图表实例
let salesChart: echarts.ECharts | null = null
let platformChart: echarts.ECharts | null = null
let orderStatusChart: echarts.ECharts | null = null

// 销售趋势图周期
const salesChartPeriod = ref('30')

// 最近订单
const recentOrders = ref<any[]>([])

/** 订单状态分布（真实数据） */
const orderStatusDistribution = ref<DashboardOrderStatus[]>([])

/** 平台销售分布（真实数据） */
const platformDistribution = ref<{ platform: string; salesAmount: number; percentage: number }[]>([])

/** 销售趋势（真实数据） */
const salesTrend = ref<TrendPoint[]>([])

const formatMoney = (v: number | undefined | null) =>
  Number(v ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/**
 * 「待发货订单」的口径
 *
 * 后端订单状态枚举（erp-order-service 的 OrderStatus）：
 *   PENDING(待处理) → CONFIRMED(已确认) → PROCESSING(处理中) → SHIPPED(已发货)
 *   → DELIVERED(已送达) → COMPLETED(已完成)，另有 CANCELLED / REFUNDED
 *
 * 「已确认」和「处理中」都是**还没发出去**的状态，所以待发货 = 两者之和。
 * 注意不能用 overview.pendingOrders 顶替 —— 那个字段把 PENDING(待付款)
 * 也算进去了，口径比"待发货"宽得多。
 */
const PRE_SHIPMENT_STATUSES = ['CONFIRMED', 'PROCESSING']

/** 加载统计数据 */
const loadStats = async () => {
  try {
    const data = await analyticsApi.dashboardData()
    const overview = data?.overview
    const statusList = data?.orderStatusDistribution ?? []

    stats.value = {
      todayOrders: Number(overview?.todayOrders ?? 0),
      todayAmount: Number(overview?.todaySales ?? 0),
      pendingShipment: statusList
        .filter(s => PRE_SHIPMENT_STATUSES.includes(s.status))
        .reduce((sum, s) => sum + Number(s.count ?? 0), 0),
      lowStock: Number(overview?.lowStockProducts ?? 0)
    }

    orderStatusDistribution.value = statusList
    platformDistribution.value = data?.platformDistribution ?? []
  } catch (e) {
    ElMessage.error('看板统计加载失败，请检查分析服务状态')
    // 不回退到 mock —— 回退只会把后端的错掩盖掉
  }
}

/** 加载最近订单 */
const loadRecentOrders = async () => {
  try {
    recentOrders.value = (await analyticsApi.recentOrders()) ?? []
  } catch (e) {
    ElMessage.error('最近订单加载失败')
    recentOrders.value = []
  }
}

/** 加载销售趋势（受周期切换影响） */
const loadSalesChartData = async () => {
  try {
    salesTrend.value = (await analyticsApi.ordersTrend(Number(salesChartPeriod.value))) ?? []
    renderSalesChart()
  } catch (e) {
    ElMessage.error('销售趋势加载失败')
    salesTrend.value = []
    renderSalesChart()
  }
}

/** 渲染销售趋势图 */
const renderSalesChart = () => {
  if (!salesChartRef.value) return
  if (!salesChart) salesChart = echarts.init(salesChartRef.value)

  const dates = salesTrend.value.map(t => t.date)
  salesChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: {
      data: ['订单数', '销售额']
    },
    xAxis: {
      type: 'category',
      data: dates
    },
    yAxis: [
      {
        type: 'value',
        name: '订单数',
        position: 'left'
      },
      {
        type: 'value',
        name: '销售额(元)',
        position: 'right',
        axisLabel: {
          formatter: '¥{value}'
        }
      }
    ],
    series: [
      {
        name: '订单数',
        type: 'bar',
        data: salesTrend.value.map(t => Number(t.count ?? 0))
      },
      {
        name: '销售额',
        type: 'line',
        yAxisIndex: 1,
        data: salesTrend.value.map(t => Number(t.revenue ?? 0)),
        smooth: true
      }
    ]
  })
}

/** 渲染平台占比图 */
const renderPlatformChart = () => {
  if (!platformChartRef.value) return
  if (!platformChart) platformChart = echarts.init(platformChartRef.value)

  platformChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b} : {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    series: [
      {
        name: '平台销售占比',
        type: 'pie',
        radius: '65%',
        center: ['60%', '50%'],
        data: platformDistribution.value.map(p => ({
          value: Number(p.salesAmount ?? 0),
          name: p.platform
        })),
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  })
}

/** 渲染订单状态分布图 */
const renderOrderStatusChart = () => {
  if (!orderStatusChartRef.value) return
  if (!orderStatusChart) orderStatusChart = echarts.init(orderStatusChartRef.value)

  const colors = ['#909399', '#E6A23C', '#409EFF', '#67C23A', '#F56C6C', '#909399', '#606266', '#F56C6C']

  orderStatusChart.setOption({
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: orderStatusDistribution.value.map(s => getStatusText(s.status))
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '订单数',
        type: 'bar',
        data: orderStatusDistribution.value.map(s => Number(s.count ?? 0)),
        itemStyle: {
          color: (params: any) => colors[params.dataIndex % colors.length]
        }
      }
    ]
  })
}

// 窗口大小改变时重新渲染图表
const resizeCharts = () => {
  salesChart?.resize()
  platformChart?.resize()
  orderStatusChart?.resize()
}

/** 订单状态 → 标签颜色 */
const getStatusType = (status: string) => {
  const typeMap: Record<string, any> = {
    PENDING: 'info',
    CONFIRMED: 'info',
    PROCESSING: 'warning',
    SHIPPED: 'primary',
    DELIVERED: 'success',
    COMPLETED: 'success',
    CANCELLED: 'danger',
    REFUNDED: 'danger'
  }
  return typeMap[status] || 'info'
}

/** 订单状态 → 中文 */
const getStatusText = (status: string) => {
  const textMap: Record<string, string> = {
    PENDING: '待处理',
    CONFIRMED: '已确认',
    PROCESSING: '待发货',
    SHIPPED: '已发货',
    DELIVERED: '已送达',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    REFUNDED: '已退款'
  }
  return textMap[status] || status
}

// 监听销售趋势图周期变化
watch(salesChartPeriod, () => {
  loadSalesChartData()
})

onMounted(async () => {
  await Promise.all([loadStats(), loadRecentOrders(), loadSalesChartData()])

  await nextTick()
  renderSalesChart()
  renderPlatformChart()
  renderOrderStatusChart()

  window.addEventListener('resize', resizeCharts)
})

onUnmounted(() => {
  salesChart?.dispose()
  platformChart?.dispose()
  orderStatusChart?.dispose()
  window.removeEventListener('resize', resizeCharts)
})
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  height: 120px;
}

.stat-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: #fff;
}

.stat-info {
  text-align: right;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
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
  height: 350px;
}
</style>
