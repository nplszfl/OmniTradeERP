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
              <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
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
        <el-table-column prop="locationCode" label="库位" width="100" />
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
    <el-dialog v-model="adjustDialogVisible" title="调整库存" width="450px">
      <el-form ref="adjustFormRef" :model="adjustForm" :rules="adjustRules" label-width="80px">
        <el-form-item label="SKU">
          <span class="text-primary">{{ adjustForm.skuCode }}</span>
        </el-form-item>
        <el-form-item label="仓库">
          <span>{{ adjustForm.warehouseName }}</span>
        </el-form-item>
        <el-form-item label="当前库存">
          <span class="text-bold">{{ adjustForm.currentQty }}</span>
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
          <el-input v-model="adjustForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="adjustLoading" @click="handleAdjustSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 调拨对话框 -->
    <el-dialog v-model="transferDialogVisible" title="库存调拨" width="500px">
      <el-form ref="transferFormRef" :model="transferForm" :rules="transferRules" label-width="80px">
        <el-form-item label="SKU">
          <span class="text-primary">{{ transferForm.skuCode }}</span>
        </el-form-item>
        <el-form-item label="从仓库">
          <span>{{ transferForm.fromWarehouseName }}</span>
        </el-form-item>
        <el-form-item label="可用数">
          <span class="text-bold">{{ transferForm.availableQty }}</span>
        </el-form-item>
        <el-form-item label="目标仓库" prop="toWarehouseId">
          <el-select v-model="transferForm.toWarehouseId" placeholder="请选择目标仓库" style="width: 100%" @change="handleToWarehouseChange">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标库位" prop="toLocationId">
          <el-select v-model="transferForm.toLocationId" placeholder="请选择目标库位" style="width: 100%">
            <el-option v-for="loc in toLocations" :key="loc.id" :label="loc.locationCode" :value="loc.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="transferForm.quantity" :min="1" :max="transferForm.availableQty" style="width: 150px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="transferForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="transferLoading" @click="handleTransferSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- SKU详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="库存详情" width="750px">
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
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import { inventoryApi, type Inventory, type StockFlow, type StockAdjustRequest, type StockTransferRequest } from '@/api/inventory'
import { warehouseApi, type Warehouse, type WarehouseLocation } from '@/api/warehouse'
import { unwrapPage } from '@/utils/response'

const loading = ref(false)
const adjustLoading = ref(false)
const transferLoading = ref(false)
const inventoryList = ref<Inventory[]>([])
const warehouses = ref<Warehouse[]>([])
const stockFlows = ref<StockFlow[]>([])

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
  id: undefined as number | undefined,
  skuCode: '',
  warehouseName: '',
  currentQty: 0,
  type: 'increase' as 'increase' | 'decrease' | 'set',
  quantity: 0,
  reason: '',
  remark: ''
})
const adjustRules = {
  type: [{ required: true, message: '请选择调整类型', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }],
  reason: [{ required: true, message: '请选择原因', trigger: 'change' }]
}

// 调拨对话框
const transferDialogVisible = ref(false)
const transferFormRef = ref()
const transferForm = reactive({
  fromInventoryId: null as number | null,
  skuCode: '',
  fromWarehouseName: '',
  availableQty: 0,
  toWarehouseId: null as number | null,
  toLocationId: null as number | null,
  quantity: 1,
  remark: ''
})
const transferRules = {
  toWarehouseId: [{ required: true, message: '请选择目标仓库', trigger: 'change' }],
  toLocationId: [{ required: true, message: '请选择目标库位', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }]
}
const toLocations = ref<WarehouseLocation[]>([])

// 详情对话框
const detailDialogVisible = ref(false)
const currentDetail = reactive<any>({})

// 加载仓库列表
const loadWarehouses = async () => {
  try {
    const res = await warehouseApi.getWarehouses({ status: 1 })
    warehouses.value = res || []
  } catch (error) {
    console.error('加载仓库列表失败:', error)
  }
}

// 加载统计数据
const loadStats = async () => {
  try {
    const res = await inventoryApi.getStats()
    stats.value = res || { totalSku: 0, normalStock: 0, lowStock: 0, outOfStock: 0 }
  } catch (error) {
    console.error('加载统计失败:', error)
  }
}

// 加载库存列表
const loadInventory = async () => {
  loading.value = true
  try {
    const params = {
      current: pagination.current,
      size: pagination.size,
      warehouseId: filterForm.warehouseId || undefined,
      skuCode: filterForm.skuCode || undefined,
      status: filterForm.status || undefined
    }
    const res = await inventoryApi.getInventoryList(params)
    const pageData = unwrapPage(res)
    inventoryList.value = pageData.records || []
    pagination.total = pageData.total || 0
  } catch (error) {
    console.error('加载库存列表失败:', error)
  } finally {
    loading.value = false
  }
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
        { value: stats.value.normalStock, name: '正常', itemStyle: { color: '#67c23a' } },
        { value: stats.value.lowStock, name: '预警', itemStyle: { color: '#e6a23c' } },
        { value: stats.value.outOfStock, name: '缺货', itemStyle: { color: '#f56c6c' } }
      ]
    }]
  })
}

const updateWarehouseChart = () => {
  if (!warehouseChartRef.value) return
  if (!warehouseChart) warehouseChart = echarts.init(warehouseChartRef.value)
  
  // 按仓库统计数据
  const warehouseData: Record<string, number> = {}
  inventoryList.value.forEach(item => {
    const name = item.warehouseName || '未知'
    warehouseData[name] = (warehouseData[name] || 0) + item.totalQty
  })
  
  warehouseChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: Object.keys(warehouseData) },
    yAxis: { type: 'value' },
    series: [{
      type: 'bar',
      data: Object.values(warehouseData),
      itemStyle: { color: '#409eff' }
    }]
  })
}

// 搜索重置
const handleSearch = () => { pagination.current = 1; loadInventory() }
const handleReset = () => { 
  filterForm.warehouseId = null
  filterForm.skuCode = '' 
  filterForm.status = '' 
  handleSearch() 
}

// 导出
const handleExport = async () => { 
  try {
    const params = {
      current: 1,
      size: 10000,
      warehouseId: filterForm.warehouseId || undefined,
      skuCode: filterForm.skuCode || undefined,
      status: filterForm.status || undefined
    }
    const blob = await inventoryApi.exportInventory(params)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `inventory_${new Date().toISOString().slice(0, 10)}.xlsx`
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败')
  }
}

// 调整库存
const handleAdjust = (row: Inventory) => {
  adjustForm.id = row.id
  adjustForm.skuCode = row.skuCode
  adjustForm.warehouseName = row.warehouseName
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
  try {
    const data: StockAdjustRequest = {
      inventoryId: adjustForm.id!,
      type: adjustForm.type,
      quantity: adjustForm.quantity,
      reason: adjustForm.reason,
      remark: adjustForm.remark
    }
    await inventoryApi.adjustStock(data)
    ElMessage.success('库存调整成功')
    adjustDialogVisible.value = false
    loadInventory()
    loadStats()
  } catch (error) {
    console.error('调整库存失败:', error)
  } finally {
    adjustLoading.value = false
  }
}

// 调拨
const handleTransfer = (row: Inventory) => {
  transferForm.fromInventoryId = row.id
  transferForm.skuCode = row.skuCode
  transferForm.fromWarehouseName = row.warehouseName
  transferForm.availableQty = row.availableQty
  transferForm.toWarehouseId = null
  transferForm.toLocationId = null
  transferForm.quantity = 1
  transferForm.remark = ''
  toLocations.value = []
  transferDialogVisible.value = true
}

const handleToWarehouseChange = async (warehouseId: number) => {
  try {
    const res = await warehouseApi.getLocations(warehouseId)
    toLocations.value = res?.filter((l: WarehouseLocation) => l.status === 1) || []
  } catch (error) {
    console.error('加载库位失败:', error)
    toLocations.value = []
  }
}

const handleTransferSubmit = async () => {
  const valid = await transferFormRef.value?.validate().catch(() => false)
  if (!valid) return
  transferLoading.value = true
  try {
    const data: StockTransferRequest = {
      fromInventoryId: transferForm.fromInventoryId!,
      toWarehouseId: transferForm.toWarehouseId!,
      toLocationId: transferForm.toLocationId!,
      quantity: transferForm.quantity,
      remark: transferForm.remark
    }
    await inventoryApi.transferStock(data)
    ElMessage.success('调拨成功')
    transferDialogVisible.value = false
    loadInventory()
  } catch (error) {
    console.error('调拨失败:', error)
  } finally {
    transferLoading.value = false
  }
}

// 详情
const handleDetail = async (row: Inventory) => {
  Object.assign(currentDetail, row)
  try {
    const res = await inventoryApi.getStockFlows(row.id)
    const pageData = unwrapPage(res)
    stockFlows.value = pageData.records || []
  } catch (error) {
    console.error('加载流水失败:', error)
    stockFlows.value = []
  }
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