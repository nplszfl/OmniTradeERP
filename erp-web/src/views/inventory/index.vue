<template>
  <div class="inventory-page">
    <!-- 统计概览 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card total">
          <div class="stat-content">
            <div class="stat-icon"><el-icon><Box /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalSku }}</div>
              <div class="stat-label">SKU总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card normal">
          <div class="stat-content">
            <div class="stat-icon"><el-icon><CircleCheck /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.normalStock }}</div>
              <div class="stat-label">库存正常</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card warning">
          <div class="stat-content">
            <div class="stat-icon"><el-icon><Warning /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.lowStock }}</div>
              <div class="stat-label">库存预警</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card danger">
          <div class="stat-content">
            <div class="stat-icon"><el-icon><WarningFilled /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.outOfStock }}</div>
              <div class="stat-label">缺货</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表 -->
    <el-row :gutter="20" class="charts-row">
      <el-col :span="12">
        <el-card>
          <template #header><span>库存状态分布</span></template>
          <div ref="statusChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header><span>各仓库库存占比</span></template>
          <div ref="warehouseChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 库存列表 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>库存明细</span>
          <div class="header-actions">
            <el-button type="primary" :icon="Refresh" @click="loadInventory">刷新</el-button>
            <el-button type="success" :icon="Download" @click="handleExport">导出</el-button>
          </div>
        </div>
      </template>

      <!-- 筛选 -->
      <div class="filter-section">
        <el-form :inline="true" :model="filterForm" class="filter-form">
          <el-form-item label="仓库">
            <el-select v-model="filterForm.warehouseId" placeholder="全部仓库" clearable style="width: 150px">
              <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="SKU">
            <el-input v-model="filterForm.skuCode" placeholder="请输入SKU" clearable style="width: 150px" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filterForm.status" placeholder="全部状态" clearable style="width: 120px">
              <el-option label="正常" value="normal" />
              <el-option label="预警" value="warning" />
              <el-option label="不足" value="danger" />
              <el-option label="缺货" value="out" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="inventoryList" stripe style="width: 100%">
        <el-table-column prop="skuCode" label="SKU" width="150">
          <template #default="{ row }">
            <el-link type="primary" @click="handleDetail(row)">{{ row.skuCode }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="商品名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="warehouseName" label="仓库" width="120" />
        <el-table-column prop="availableQty" label="可用" width="80" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.availableQty < row.safetyStock }">{{ row.availableQty }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="lockedQty" label="锁定" width="80" align="right">
          <template #default="{ row }">
            <span class="text-warning">{{ row.lockedQty }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="totalQty" label="总库存" width="80" align="right">
          <template #default="{ row }">
            <span class="text-bold">{{ row.totalQty }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="safetyStock" label="安全库存" width="80" align="right" />
        <el-table-column label="库存状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStockStatus(row)">{{ getStockStatusText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="周转" width="80">
          <template #default="{ row }">
            <el-tooltip :content="`日均销量: ${row.dailySales}`">
              <span>{{ row.turnoverDays || '-' }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleAdjust(row)">调库存</el-button>
            <el-button type="warning" link @click="handleTransfer(row)">调拨</el-button>
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
        @size-change="loadInventory"
        @current-change="loadInventory"
      />
    </el-card>

    <!-- 库存调整对话框 -->
    <el-dialog v-model="adjustDialogVisible" title="调整库存" width="400px">
      <el-form ref="adjustFormRef" :model="adjustForm" :rules="adjustRules" label-width="80px">
        <el-form-item label="SKU">
          <span>{{ adjustForm.skuCode }}</span>
        </el-form-item>
        <el-form-item label="当前库存">
          <span>{{ adjustForm.currentQty }}</span>
        </el-form-item>
        <el-form-item label="调整类型" prop="type">
          <el-radio-group v-model="adjustForm.type">
            <el-radio label="increase">增加</el-radio>
            <el-radio label="decrease">减少</el-radio>
            <el-radio label="set">设为</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="adjustForm.quantity" :min="0" style="width: 150px" />
        </el-form-item>
        <el-form-item label="原因" prop="reason">
          <el-select v-model="adjustForm.reason" placeholder="请选择原因" style="width: 100%">
            <el-option label="采购入库" value="purchase" />
            <el-option label="退货" value="return" />
            <el-option label="盘点调整" value="check" />
            <el-option label="损耗" value="loss" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="adjustForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="adjustLoading" @click="handleAdjustSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- SKU详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="库存详情" width="700px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="SKU">{{ currentDetail.skuCode }}</el-descriptions-item>
        <el-descriptions-item label="商品名称">{{ currentDetail.productName }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ currentDetail.warehouseName }}</el-descriptions-item>
        <el-descriptions-item label="库位">{{ currentDetail.locationCode }}</el-descriptions-item>
        <el-descriptions-item label="可用库存">
          <span class="text-bold text-primary">{{ currentDetail.availableQty }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="锁定库存">{{ currentDetail.lockedQty }}</el-descriptions-item>
        <el-descriptions-item label="总库存">{{ currentDetail.totalQty }}</el-descriptions-item>
        <el-descriptions-item label="安全库存">{{ currentDetail.safetyStock }}</el-descriptions-item>
        <el-descriptions-item label="日均销量">{{ currentDetail.dailySales }}</el-descriptions-item>
        <el-descriptions-item label="预计可售天数">{{ currentDetail.turnoverDays }}</el-descriptions-item>
      </el-descriptions>
      
      <el-divider>库存流水</el-divider>
      <el-table :data="stockFlows" max-height="200">
        <el-table-column prop="flowTime" label="时间" width="160" />
        <el-table-column prop="flowType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.flowType === 'in' ? 'success' : 'danger'" size="small">
              {{ row.flowType === 'in' ? '入库' : '出库' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="80" align="right" />
        <el-table-column prop="orderNo" label="关联单号" width="150" />
        <el-table-column prop="remark" label="备注" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { Refresh, Download, Search, Box, CircleCheck, Warning, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'

const loading = ref(false)
const adjustLoading = ref(false)
const inventoryList = ref<any[]>([])
const warehouses = ref<any[]>([])
const stockFlows = ref<any[]>([])

// 统计
const stats = ref({ totalSku: 0, normalStock: 0, lowStock: 0, outOfStock: 0 })

// 图表
const statusChartRef = ref<HTMLDivElement>()
const warehouseChartRef = ref<HTMLDivElement>()
let statusChart: echarts.ECharts | null = null
let warehouseChart: echarts.ECharts | null = null

// 分页
const pagination = reactive({ current: 1, size: 20, total: 0 })

// 筛选
const filterForm = reactive({ warehouseId: null as number | null, skuCode: '', status: '' })

// 调整对话框
const adjustDialogVisible = ref(false)
const adjustFormRef = ref()
const adjustForm = reactive({
  id: null as number | null,
  skuCode: '',
  currentQty: 0,
  type: 'increase',
  quantity: 0,
  reason: '',
  remark: ''
})
const adjustRules = {
  type: [{ required: true, message: '请选择调整类型', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }],
  reason: [{ required: true, message: '请选择原因', trigger: 'change' }]
}

// 详情对话框
const detailDialogVisible = ref(false)
const currentDetail = reactive<any>({})

// 加载仓库列表
const loadWarehouses = async () => {
  // TODO: 调用API
  warehouses.value = [
    { id: 1, name: '美国仓' },
    { id: 2, name: '欧洲仓' },
    { id: 3, name: '东南亚仓' }
  ]
}

// 加载统计数据
const loadStats = async () => {
  // TODO: 调用API
  stats.value = { totalSku: 1256, normalStock: 1080, lowStock: 142, outOfStock: 34 }
}

// 加载库存列表
const loadInventory = async () => {
  loading.value = true
  // TODO: 调用API
  setTimeout(() => {
    inventoryList.value = generateMockInventory()
    pagination.total = 200
    loading.value = false
  }, 300)
}

const generateMockInventory = () => {
  const warehouses = ['美国仓', '欧洲仓', '东南亚仓']
  const statuses = ['normal', 'warning', 'danger', 'out']
  return Array.from({ length: 20 }, (_, i) => {
    const availableQty = Math.floor(Math.random() * 500)
    const safetyStock = 50
    let status = 'normal'
    if (availableQty < safetyStock) status = 'out'
    else if (availableQty < safetyStock * 2) status = 'danger'
    else if (availableQty < safetyStock * 3) status = 'warning'
    
    return {
      id: i + 1,
      skuCode: `SKU${1000 + i}`,
      productName: `商品名称 ${i + 1}`,
      warehouseName: warehouses[i % 3],
      warehouseId: (i % 3) + 1,
      availableQty,
      lockedQty: Math.floor(Math.random() * 20),
      totalQty: availableQty + Math.floor(Math.random() * 20),
      safetyStock,
      status,
      dailySales: Math.floor(Math.random() * 10) + 1,
      turnoverDays: availableQty > 0 ? Math.floor(availableQty / (Math.floor(Math.random() * 10) + 1)) : 0,
      locationCode: `A-${Math.floor(i / 10) + 1}-0${i % 10 + 1}`
    }
  })
}

// 加载图表
const loadCharts = () => {
  updateStatusChart()
  updateWarehouseChart()
}

const updateStatusChart = () => {
  if (!statusChartRef.value) return
  if (!statusChart) statusChart = echarts.init(statusChartRef.value)
  
  statusChart.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: [
        { value: 1080, name: '正常', itemStyle: { color: '#67c23a' } },
        { value: 142, name: '预警', itemStyle: { color: '#e6a23c' } },
        { value: 34, name: '缺货', itemStyle: { color: '#f56c6c' } }
      ]
    }]
  })
}

const updateWarehouseChart = () => {
  if (!warehouseChartRef.value) return
  if (!warehouseChart) warehouseChart = echarts.init(warehouseChartRef.value)
  
  warehouseChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: ['美国仓', '欧洲仓', '东南亚仓'] },
    yAxis: { type: 'value' },
    series: [{
      type: 'bar',
      data: [45000, 32000, 28000],
      itemStyle: { color: '#409eff' }
    }]
  })
}

// 搜索重置
const handleSearch = () => { pagination.current = 1; loadInventory() }
const handleReset = () => { filterForm.warehouseId = null; filterForm.skuCode = ''; filterForm.status = ''; handleSearch() }

// 导出
const handleExport = () => { ElMessage.info('导出功能开发中...') }

// 调整库存
const handleAdjust = (row: any) => {
  adjustForm.id = row.id
  adjustForm.skuCode = row.skuCode
  adjustForm.currentQty = row.availableQty
  adjustForm.type = 'increase'
  adjustForm.quantity = 0
  adjustForm.reason = ''
  adjustForm.remark = ''
  adjustDialogVisible.value = true
}

const handleAdjustSubmit = async () => {
  const valid = await adjustFormRef.value?.validate().catch(() => false)
  if (!valid) return
  adjustLoading.value = true
  // TODO: 调用API
  setTimeout(() => {
    ElMessage.success('库存调整成功')
    adjustDialogVisible.value = false
    adjustLoading.value = false
    loadInventory()
    loadStats()
  }, 500)
}

// 调拨
const handleTransfer = (row: any) => { ElMessage.info('调拨功能开发中...') }

// 详情
const handleDetail = (row: any) => {
  Object.assign(currentDetail, row)
  stockFlows.value = [
    { flowTime: '2026-03-30 10:00', flowType: 'in', quantity: 100, orderNo: 'PO202603001', remark: '采购入库' },
    { flowTime: '2026-03-28 15:30', flowType: 'out', quantity: 25, orderNo: 'SO202603045', remark: '订单出库' },
    { flowTime: '2026-03-25 09:00', flowType: 'in', quantity: 200, orderNo: 'PO202602088', remark: '采购入库' }
  ]
  detailDialogVisible.value = true
}

// 工具函数
const getStockStatus = (row: any) => {
  if (row.availableQty < row.safetyStock) return 'danger'
  if (row.availableQty < row.safetyStock * 2) return 'warning'
  return 'success'
}

const getStockStatusText = (row: any) => {
  if (row.availableQty < row.safetyStock) return '缺货'
  if (row.availableQty < row.safetyStock * 2) return '不足'
  if (row.availableQty < row.safetyStock * 3) return '预警'
  return '正常'
}

const resizeCharts = () => { statusChart?.resize(); warehouseChart?.resize() }

onMounted(async () => {
  await Promise.all([loadWarehouses(), loadStats()])
  await loadInventory()
  await nextTick()
  loadCharts()
  window.addEventListener('resize', resizeCharts)
})

onUnmounted(() => {
  statusChart?.dispose()
  warehouseChart?.dispose()
  window.removeEventListener('resize', resizeCharts)
})
</script>

<style scoped>
.inventory-page { padding: 20px; }
.stats-row { margin-bottom: 20px; }
.stat-card { height: 90px; }
.stat-card.total .stat-icon { background: #409eff; }
.stat-card.normal .stat-icon { background: #67c23a; }
.stat-card.warning .stat-icon { background: #e6a23c; }
.stat-card.danger .stat-icon { background: #f56c6c; }

.stat-content { display: flex; align-items: center; gap: 15px; }
.stat-icon { width: 45px; height: 45px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-size: 22px; color: white; }
.stat-info { flex: 1; }
.stat-value { font-size: 20px; font-weight: bold; }
.stat-label { font-size: 12px; color: #909399; margin-top: 2px; }

.charts-row { margin-bottom: 20px; }
.chart-container { height: 250px; }

.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-actions { display: flex; gap: 10px; }

.filter-section { margin-bottom: 15px; padding: 15px; background: #f5f7fa; border-radius: 4px; }

.text-danger { color: #f56c6c; }
.text-warning { color: #e6a23c; }
.text-bold { font-weight: bold; }
.text-primary { color: #409eff; }
</style>