<template>
  <div class="dashboard">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #409eff">
              <el-icon><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.orderCount }}</div>
              <div class="stat-label">今日订单</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #67c23a">
              <el-icon><Money /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">¥{{ statistics.totalAmount }}</div>
              <div class="stat-label">今日销售额</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #e6a23c">
              <el-icon><Van /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.pendingShipmentCount }}</div>
              <div class="stat-label">待发货</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #f56c6c">
              <el-icon><Warning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.overdueCount }}</div>
              <div class="stat-label">超时未发</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20" class="charts-row">
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <span>销售趋势</span>
          </template>
          <div ref="trendChart" style="height: 350px"></div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <span>平台分布</span>
          </template>
          <div ref="platformChart" style="height: 350px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { Document, Money, Van, Warning } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

const trendChart = ref<HTMLElement>()
const platformChart = ref<HTMLElement>()
let trendChartInstance: echarts.ECharts | null = null
let platformChartInstance: echarts.ECharts | null = null

const statistics = reactive({
  orderCount: 0,
  totalAmount: '0.00',
  pendingShipmentCount: 0,
  overdueCount: 0
})

const initTrendChart = () => {
  if (!trendChart.value) return

  trendChartInstance = echarts.init(trendChart.value)

  const option = {
    tooltip: {
      trigger: 'axis'
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
        name: '订单数'
      },
      {
        type: 'value',
        name: '销售额'
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
        data: [2200, 1820, 1910, 2340, 2900, 3300, 3100]
      }
    ]
  }

  trendChartInstance.setOption(option)
}

const initPlatformChart = () => {
  if (!platformChart.value) return

  platformChartInstance = echarts.init(platformChart.value)

  const option = {
    tooltip: {
      trigger: 'item'
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    series: [
      {
        name: '平台分布',
        type: 'pie',
        radius: '50%',
        data: [
          { value: 1048, name: '亚马逊' },
          { value: 735, name: 'eBay' },
          { value: 580, name: 'Shopee' },
          { value: 484, name: 'Lazada' },
          { value: 300, name: 'TikTok' }
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

  platformChartInstance.setOption(option)
}

const loadStatistics = async () => {
  // TODO: 调用后端API加载统计数据
  statistics.orderCount = 1589
  statistics.totalAmount = '34520.00'
  statistics.pendingShipmentCount = 45
  statistics.overdueCount = 8
}

onMounted(() => {
  initTrendChart()
  initPlatformChart()
  loadStatistics()

  window.addEventListener('resize', () => {
    trendChartInstance?.resize()
    platformChartInstance?.resize()
  })
})

onUnmounted(() => {
  trendChartInstance?.dispose()
  platformChartInstance?.dispose()
})
</script>

<style scoped>
.dashboard {
  padding: 20px;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  cursor: pointer;
}

.stat-content {
  display: flex;
  align-items: center;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 15px;
}

.stat-icon .el-icon {
  font-size: 30px;
  color: #fff;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  margin-bottom: 5px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
}

.charts-row {
  margin-top: 20px;
}
</style>
