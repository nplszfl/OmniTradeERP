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
              <div class="stat-value">¥{{ stats.todayAmount }}</div>
              <div class="stat-label">今日销售额</div>
            </div>
          </div>
        </el-card>
      </el-col>
      < <el-col :span="6">
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
            <span>最近订单</span>
            <el-button type="text" @click="router.push('/orders')">查看更多</el-button>
          </template>
          <el-table :data="recentOrders" style="width: 100%">
            <el-table-column prop="platformOrderNo" label="订单号" width="150" />
            <el-table-column prop="platform" label="平台" width="80" />
            <el-table-column prop="totalAmount" label="金额" width="100" />
            <el-table-column prop="status" label="状态">
              <template #default="scope">
                <el-tag :type="getStatusType(scope.row.status)">
                  {{ getStatusText(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="orderTime" label="时间" width="160" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ShoppingCart, Money, Box, Warning } from '@element-plus/icons-vue'

const router = useRouter()

// 统计数据
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
const salesChartPeriod = ref('7')

// 最近订单
const recentOrders = ref([])

// 加载统计数据
const loadStats = async () => {
  // TODO: 调用API获取统计数据
  // const response = await orderApi.getDashboardStats()
  // stats.value = response.data

  // 模拟数据
  stats.value = {
    todayOrders: 123,
    todayAmount: 45678.90,
    pendingShipment: 15,
    lowStock: 8
  }
}

// 加载最近订单
const loadRecentOrders = async () => {
  // TODO: 调用API获取最近订单
  // const response = await orderApi.getRecentOrders(5)
  // recentOrders.value = response.data

  // 模拟数据
  recentOrders.value = [
    { platformOrderNo: 'AMZ-20260314001', platform: 'amazon', totalAmount: 199.99, status: 'pending_shipment', orderTime: '2026-03-14 10:30:00' },
    { platformOrderNo: 'EBAY-20260314002', platform: 'ebay', totalAmount: 89.99, status: 'shipped', orderTime: '2026-03-14 10:15:00' },
    { platformOrderNo: 'SHOPEE-20260314003', platform: 'shopee', totalAmount: 59.99, status: 'pending_payment', orderTime: '2026-03-14 10:00:00' },
    { platformOrderNo: 'LAZADA-20260314004', platform: 'lazada', totalAmount: 79.99, status: 'shipped', orderTime: '2026-03-14 09:45:00' },
    { platformOrderNo: 'TIKTOK-20260314005', platform: 'tiktok', totalAmount: 129.99, status: 'delivered', orderTime: '2026-03-14 09:30:00' }
  ]
}

// 初始化销售趋势图
const initSalesChart = () => {
  if (!salesChartRef.value) return

  salesChart = echarts.init(salesChartRef.value)

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: {
      data: ['订单数', '销售额']
    },
    xAxis: {
      type: 'category',
      data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
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
        data: [120, 132, 101, 134, 90, 230, 210]
      },
      {
        name: '销售额',
        type: 'line',
        yAxisIndex: 1,
        data: [22000, 18200, 19100, 23400, 29000, 33000, 31000],
        smooth: true
      }
    ]
  }

  salesChart.setOption(option)
}

// 初始化平台占比图
const initPlatformChart = () => {
  if (!platformChartRef.value) return

  platformChart = echarts.init(platformChartRef.value)

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b} : {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left',
      data: ['Amazon', 'eBay', 'Shopee', 'Lazada', 'TikTok']
    },
    series: [
      {
        name: '平台销售占比',
        type: 'pie',
        radius: '65%',
        center: ['60%', '50%'],
        data: [
          { value: 335, name: 'Amazon' },
          { value: 310, name: 'eBay' },
          { value: 234, name: 'Shopee' },
          { value: 135, name: 'Lazada' },
          { value: 148, name: 'TikTok' }
        ],
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  }

  platformChart.setOption(option)
}

// 初始化订单状态分布图
const initOrderStatusChart = () => {
  if (!orderStatusChartRef.value) return

  orderStatusChart = echarts.init(orderStatusChartRef.value)

  const option = {
    tooltip: {
      trigger: 'item'
    },
    xAxis: {
      type: 'category',
      data: ['待付款', '待发货', '已发货', '已送达', '已取消']
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '订单数',
        type: 'bar',
        data: [5, 15, 45, 30, 3],
        itemStyle: {
          color: function(params: any) {
            const colors = ['#909399', '#E6A23C', '#409EFF', '#67C23A', '#F56C6C']
            return colors[params.dataIndex]
          }
        }
      }
    ]
  }

  orderStatusChart.setOption(option)
}

// 窗口大小改变时重新渲染图表
const resizeCharts = () => {
  salesChart?.resize()
  platformChart?.resize()
  orderStatusChart?.resize()
}

// 获取状态类型
const getStatusType = (status: string) => {
  const typeMap: Record<string, any> = {
    pending_payment: 'info',
    pending_shipment: 'warning',
    shipped: 'primary',
    delivered: 'success',
    cancelled: 'danger'
  }
  return typeMap[status] || 'info'
}

// 获取状态文本
const getStatusText = (status: string) => {
  const textMap: Record<string, string> = {
    pending_payment: '待付款',
    pending_shipment: '待发货',
    shipped: '已发货',
    delivered: '已送达',
    cancelled: '已取消'
  }
  return textMap[status] || status
}

// 监听销售趋势图周期变化
watch(salesChartPeriod, () => {
  loadSalesChartData()
})

// 加载销售趋势图数据
const loadSalesChartData = async () => {
  // TODO: 根据周期调用API获取数据
  // const response = await orderApi.getSalesTrend(salesChartPeriod.value)
  // 更新图表数据
}

onMounted(async () => {
  await loadStats()
  await loadRecentOrders()

  await nextTick()
  initSalesChart()
  initPlatformChart()
  initOrderStatusChart()

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
  color: white;
}

.stat-info {
  text-align: right;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
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
