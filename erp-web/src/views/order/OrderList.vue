<template>
  <div class="order-page">
    <!-- 高级搜索筛选 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-row :gutter="10">
          <el-col :span="4">
            <el-form-item label="平台" style="width: 100%">
              <el-select v-model="searchForm.platform" placeholder="全部平台" clearable style="width: 100%">
                <el-option label="亚马逊" value="amazon" />
                <el-option label="eBay" value="ebay" />
                <el-option label="Shopee" value="shopee" />
                <el-option label="Lazada" value="lazada" />
                <el-option label="TikTok" value="tiktok" />
              </el-select>
            </el-form-item>
          </el-col>
          
          <el-col :span="4">
            <el-form-item label="订单状态" style="width: 100%">
              <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 100%">
                <el-option label="待付款" value="pending_payment" />
                <el-option label="待发货" value="pending_shipment" />
                <el-option label="已发货" value="shipped" />
                <el-option label="已送达" value="delivered" />
                <el-option label="已取消" value="cancelled" />
              </el-select>
            </el-form-item>
          </el-col>
          
          <el-col :span="4">
            <el-form-item label="支付状态" style="width: 100%">
              <el-select v-model="searchForm.paymentStatus" placeholder="全部" clearable style="width: 100%">
                <el-option label="已支付" value="paid" />
                <el-option label="未支付" value="unpaid" />
                <el-option label="部分支付" value="partial" />
              </el-select>
            </el-form-item>
          </el-col>
          
          <el-col :span="4">
            <el-form-item label="买家国家" style="width: 100%">
              <el-input v-model="searchForm.buyerCountry" placeholder="国家名称" clearable />
            </el-form-item>
          </el-col>
          
          <el-col :span="4">
            <el-form-item label="商品SKU" style="width: 100%">
              <el-input v-model="searchForm.productSku" placeholder="SKU" clearable />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="10">
          <el-col :span="4">
            <el-form-item label="订单号" style="width: 100%">
              <el-input v-model="searchForm.internalOrderNo" placeholder="内部订单号" clearable />
            </el-form-item>
          </el-col>
          
          <el-col :span="4">
            <el-form-item label="买家姓名" style="width: 100%">
              <el-input v-model="searchForm.buyerName" placeholder="买家姓名" clearable />
            </el-form-item>
          </el-col>
          
          <el-col :span="4">
            <el-form-item label="最小金额" style="width: 100%">
              <el-input-number v-model="searchForm.minAmount" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          
          <el-col :span="4">
            <el-form-item label="最大金额" style="width: 100%">
              <el-input-number v-model="searchForm.maxAmount" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          
          <el-col :span="4">
            <el-form-item label="日期范围" style="width: 100%">
              <el-date-picker
                v-model="dateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="ArrowDown" @click="showAdvanced = !showAdvanced">
            {{ showAdvanced ? '收起筛选' : '更多筛选' }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 批量操作工具栏 -->
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar">
        <div class="left">
          <el-checkbox v-model="selectAll" :indeterminate="isIndeterminate" @change="handleSelectAll">
            全选 ({{ selectedOrders.length }}/{{ orderList.length }})
          </el-checkbox>
          <el-button 
            type="primary" 
            :disabled="selectedOrders.length === 0"
            @click="handleBatchUpdateStatus"
          >
            批量更新状态
          </el-button>
          <el-button 
            type="success" 
            :disabled="selectedOrders.length === 0"
            @click="handleBatchShip"
          >
            批量发货
          </el-button>
          <el-button 
            type="warning" 
            :disabled="selectedOrders.length === 0"
            @click="handleBatchExport"
          >
            批量导出
          </el-button>
        </div>
        <div class="right">
          <el-button type="primary" :icon="RefreshLeft" @click="loadOrders">刷新</el-button>
        </div>
      </div>
    </el-card>

    <!-- 订单列表 -->
    <el-card class="table-card" shadow="never">
      <el-table
        v-loading="loading"
        :data="orderList"
        stripe
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="internalOrderNo" label="内部订单号" width="180" fixed>
          <template #default="{ row }">
            <el-link type="primary" @click="handleViewDetail(row)">{{ row.internalOrderNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="platformOrderNo" label="平台订单号" width="150" />
        <el-table-column prop="platform" label="平台" width="90">
          <template #default="{ row }">
            <el-tag :type="getPlatformType(row.platform)" size="small">{{ getPlatformName(row.platform) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="buyerName" label="买家" width="120" show-overflow-tooltip />
        <el-table-column prop="orderAmount" label="金额" width="110">
          <template #default="{ row }">
            <span style="font-weight: 500">{{ row.orderAmount }} {{ row.currencyCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="订单状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusName(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="paymentStatus" label="支付状态" width="90">
          <template #default="{ row }">
            <el-tag :type="getPaymentStatusType(row.paymentStatus)" size="small">{{ getPaymentStatusName(row.paymentStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="trackingNumber" label="物流单号" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.trackingNumber || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="recipientCountry" label="国家" width="80" />
        <el-table-column prop="createTime" label="创建时间" width="150">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleViewDetail(row)">详情</el-button>
            <el-button link type="success" size="small" @click="handleShip(row)" v-if="row.status === 'pending_shipment'">发货</el-button>
            <el-button link type="danger" size="small" @click="handleCancel(row)" v-if="row.status === 'pending_shipment'">取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 订单详情弹窗 -->
    <el-dialog v-model="detailVisible" title="订单详情" width="900px" append-to-body>
      <el-tabs v-if="currentOrder" v-model="activeTab">
        <el-tab-pane label="基本信息" name="basic">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="内部订单号">{{ currentOrder.internalOrderNo }}</el-descriptions-item>
            <el-descriptions-item label="平台订单号">{{ currentOrder.platformOrderNo }}</el-descriptions-item>
            <el-descriptions-item label="平台">
              <el-tag :type="getPlatformType(currentOrder.platform)" size="small">{{ getPlatformName(currentOrder.platform) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="订单状态">
              <el-tag :type="getStatusType(currentOrder.status)" size="small">{{ getStatusName(currentOrder.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="买家">{{ currentOrder.buyerName }}</el-descriptions-item>
            <el-descriptions-item label="买家邮箱">{{ currentOrder.buyerEmail }}</el-descriptions-item>
            <el-descriptions-item label="订单金额">
              <span style="font-weight: bold; color: #409eff">{{ currentOrder.orderAmount }} {{ currentOrder.currencyCode }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="支付状态">
              <el-tag :type="getPaymentStatusType(currentOrder.paymentStatus)" size="small">{{ getPaymentStatusName(currentOrder.paymentStatus) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="物流单号">{{ currentOrder.trackingNumber || '-' }}</el-descriptions-item>
            <el-descriptions-item label="物流公司">{{ currentOrder.logisticsCompany || '-' }}</el-descriptions-item>
            <el-descriptions-item label="收货人">{{ currentOrder.recipientName }}</el-descriptions-item>
            <el-descriptions-item label="收货电话">{{ currentOrder.recipientPhone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="收货国家">{{ currentOrder.recipientCountry }}</el-descriptions-item>
            <el-descriptions-item label="收货省份">{{ currentOrder.recipientState || '-' }}</el-descriptions-item>
            <el-descriptions-item label="收货城市">{{ currentOrder.recipientCity || '-' }}</el-descriptions-item>
            <el-descriptions-item label="收货邮编">{{ currentOrder.recipientPostalCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="详细地址" :span="2">{{ currentOrder.recipientAddress }}</el-descriptions-item>
            <el-descriptions-item label="订单备注" :span="2">{{ currentOrder.remark || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间" :span="2">{{ formatTime(currentOrder.createTime) }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        
        <el-tab-pane label="商品明细" name="items">
          <el-table :data="orderItems" stripe v-loading="itemsLoading">
            <el-table-column prop="productName" label="商品名称" min-width="200" show-overflow-tooltip />
            <el-table-column prop="productSku" label="SKU" width="150" />
            <el-table-column prop="unitPrice" label="单价" width="100">
              <template #default="{ row }">
                {{ row.unitPrice }} {{ row.currencyCode }}
              </template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="80" />
            <el-table-column prop="totalAmount" label="小计" width="120">
              <template #default="{ row }">
                <span style="font-weight: 500">{{ row.totalAmount }} {{ row.currencyCode }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 发货弹窗 -->
    <el-dialog v-model="shipVisible" title="订单发货" width="500px" append-to-body>
      <el-form :model="shipForm" label-width="100px">
        <el-form-item label="订单号" required>
          <el-input :value="currentShipOrder?.internalOrderNo" disabled />
        </el-form-item>
        <el-form-item label="物流单号" required>
          <el-input v-model="shipForm.trackingNumber" placeholder="请输入物流单号" />
        </el-form-item>
        <el-form-item label="物流公司" required>
          <el-select v-model="shipForm.logisticsCompany" placeholder="请选择物流公司" style="width: 100%">
            <el-option label="DHL" value="DHL" />
            <el-option label="FedEx" value="FedEx" />
            <el-option label="UPS" value="UPS" />
            <el-option label="顺丰国际" value="SF" />
            <el-option label="邮政小包" value="CHINA_POST" />
            <el-option label="燕文物流" value="YANWEN" />
            <el-option label="4PX" value="4PX" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="shipVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmShip">确定发货</el-button>
      </template>
    </el-dialog>

    <!-- 批量更新状态弹窗 -->
    <el-dialog v-model="batchStatusVisible" title="批量更新订单状态" width="400px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="已选择">
          <el-tag>{{ selectedOrders.length }} 个订单</el-tag>
        </el-form-item>
        <el-form-item label="目标状态" required>
          <el-select v-model="batchStatusForm.targetStatus" placeholder="请选择状态" style="width: 100%">
            <el-option label="待付款" value="pending_payment" />
            <el-option label="待发货" value="pending_shipment" />
            <el-option label="已发货" value="shipped" />
            <el-option label="已送达" value="delivered" />
            <el-option label="已取消" value="cancelled" />
            <el-option label="已退款" value="refunded" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="batchStatusVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmBatchStatus">确定更新</el-button>
      </template>
    </el-dialog>

    <!-- 批量发货弹窗 -->
    <el-dialog v-model="batchShipVisible" title="批量发货" width="500px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="已选择">
          <el-tag>{{ selectedOrders.length }} 个订单</el-tag>
        </el-form-item>
        <el-form-item label="物流单号" required>
          <el-input v-model="batchShipForm.trackingNumber" placeholder="请输入物流单号" />
        </el-form-item>
        <el-form-item label="物流公司" required>
          <el-select v-model="batchShipForm.logisticsCompany" placeholder="请选择物流公司" style="width: 100%">
            <el-option label="DHL" value="DHL" />
            <el-option label="FedEx" value="FedEx" />
            <el-option label="UPS" value="UPS" />
            <el-option label="顺丰国际" value="SF" />
            <el-option label="邮政小包" value="CHINA_POST" />
            <el-option label="燕文物流" value="YANWEN" />
            <el-option label="4PX" value="4PX" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="batchShipVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmBatchShip">确定发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, RefreshLeft, ArrowDown } from '@element-plus/icons-vue'
import { orderApi, type Order, type OrderItem, type OrderQueryRequest, type OrderExportResponse } from '@/api/order'
import dayjs from 'dayjs'
import { unwrapPage } from '@/utils/response'

const loading = ref(false)
const itemsLoading = ref(false)
const orderList = ref<Order[]>([])
const orderItems = ref<OrderItem[]>([])
const detailVisible = ref(false)
const shipVisible = ref(false)
const batchStatusVisible = ref(false)
const batchShipVisible = ref(false)
const currentOrder = ref<Order | null>(null)
const currentShipOrder = ref<Order | null>(null)
const selectedOrders = ref<Order[]>([])
const activeTab = ref('basic')
const showAdvanced = ref(false)

// 日期范围
const dateRange = ref<[string, string] | null>(null)

// 搜索表单
const searchForm = reactive<OrderQueryRequest>({
  current: 1,
  size: 20,
  platform: '',
  status: '',
  paymentStatus: '',
  buyerCountry: '',
  productSku: '',
  internalOrderNo: '',
  buyerName: '',
  minAmount: undefined,
  maxAmount: undefined
})

// 分页
const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

// 发货表单
const shipForm = reactive({
  trackingNumber: '',
  logisticsCompany: ''
})

// 批量更新状态表单
const batchStatusForm = reactive({
  targetStatus: ''
})

// 批量发货表单
const batchShipForm = reactive({
  trackingNumber: '',
  logisticsCompany: ''
})

// 计算是否半选
const isIndeterminate = computed(() => {
  return selectedOrders.value.length > 0 && selectedOrders.value.length < orderList.value.length
})

// 全选
const selectAll = computed({
  get: () => orderList.value.length > 0 && selectedOrders.value.length === orderList.value.length,
  set: () => {}
})

// 加载订单
const loadOrders = async () => {
  loading.value = true
  try {
    // 处理日期范围
    if (dateRange.value && dateRange.value.length === 2) {
      searchForm.startTime = dateRange.value[0]
      searchForm.endTime = dateRange.value[1]
    } else {
      searchForm.startTime = undefined
      searchForm.endTime = undefined
    }
    
    // 设置分页
    searchForm.current = pagination.current
    searchForm.size = pagination.size
    
    const res = await orderApi.advancedQuery(searchForm)
    const pageData = unwrapPage(res)
    orderList.value = pageData.records || []
    pagination.total = pageData.total || 0
  } catch (error) {
    console.error('加载订单失败', error)
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  pagination.current = 1
  loadOrders()
}

// 重置
const handleReset = () => {
  searchForm.platform = ''
  searchForm.status = ''
  searchForm.paymentStatus = ''
  searchForm.buyerCountry = ''
  searchForm.productSku = ''
  searchForm.internalOrderNo = ''
  searchForm.buyerName = ''
  searchForm.minAmount = undefined
  searchForm.maxAmount = undefined
  dateRange.value = null
  handleSearch()
}

// 分页大小变化
const handleSizeChange = (size: number) => {
  pagination.size = size
  loadOrders()
}

// 页码变化
const handleCurrentChange = (current: number) => {
  pagination.current = current
  loadOrders()
}

// 选择变化
const handleSelectionChange = (orders: Order[]) => {
  selectedOrders.value = orders
}

// 全选
const handleSelectAll = (val: boolean) => {
  if (val) {
    selectedOrders.value = [...orderList.value]
  } else {
    selectedOrders.value = []
  }
}

// 查看详情
const handleViewDetail = async (order: Order) => {
  currentOrder.value = order
  detailVisible.value = true
  activeTab.value = 'basic'
  
  // 加载商品明细
  itemsLoading.value = true
  try {
    orderItems.value = await orderApi.getOrderItems(order.id)
  } catch (error) {
    console.error('加载商品明细失败', error)
    orderItems.value = []
  } finally {
    itemsLoading.value = false
  }
}

// 发货
const handleShip = (order: Order) => {
  currentShipOrder.value = order
  shipForm.trackingNumber = ''
  shipForm.logisticsCompany = ''
  shipVisible.value = true
}

// 确认发货
const confirmShip = async () => {
  if (!shipForm.trackingNumber || !shipForm.logisticsCompany) {
    ElMessage.warning('请填写完整的物流信息')
    return
  }

  try {
    await orderApi.updateShipping(
      currentShipOrder.value!.id,
      shipForm.trackingNumber,
      shipForm.logisticsCompany
    )
    ElMessage.success('发货成功')
    shipVisible.value = false
    loadOrders()
  } catch (error) {
    console.error('发货失败', error)
  }
}

// 取消订单
const handleCancel = (order: Order) => {
  ElMessageBox.confirm(`确定取消订单 ${order.internalOrderNo} 吗？`, '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      await orderApi.updateStatus(order.id, 'cancelled')
      ElMessage.success('订单已取消')
      loadOrders()
    } catch (error) {
      console.error('取消订单失败', error)
    }
  })
}

// 批量更新状态
const handleBatchUpdateStatus = () => {
  batchStatusForm.targetStatus = ''
  batchStatusVisible.value = true
}

// 确认批量更新状态
const confirmBatchStatus = async () => {
  if (!batchStatusForm.targetStatus) {
    ElMessage.warning('请选择目标状态')
    return
  }

  try {
    const orderIds = selectedOrders.value.map(o => o.id)
    const count = await orderApi.batchUpdateStatus({
      orderIds,
      targetStatus: batchStatusForm.targetStatus
    })
    ElMessage.success(`成功更新 ${count} 个订单状态`)
    batchStatusVisible.value = false
    selectedOrders.value = []
    loadOrders()
  } catch (error) {
    console.error('批量更新状态失败', error)
  }
}

// 批量发货
const handleBatchShip = () => {
  batchShipForm.trackingNumber = ''
  batchShipForm.logisticsCompany = ''
  batchShipVisible.value = true
}

// 确认批量发货
const confirmBatchShip = async () => {
  if (!batchShipForm.trackingNumber || !batchShipForm.logisticsCompany) {
    ElMessage.warning('请填写完整的物流信息')
    return
  }

  try {
    const orderIds = selectedOrders.value.map(o => o.id)
    const count = await orderApi.batchMarkShipped({
      orderIds,
      trackingNumber: batchShipForm.trackingNumber,
      logisticsCompany: batchShipForm.logisticsCompany
    })
    ElMessage.success(`成功发货 ${count} 个订单`)
    batchShipVisible.value = false
    selectedOrders.value = []
    loadOrders()
  } catch (error) {
    console.error('批量发货失败', error)
  }
}

// 批量导出
const handleBatchExport = async () => {
  try {
    const orderIds = selectedOrders.value.map(o => o.id)
    const res = await orderApi.batchExport({ orderIds })
    orderApi.exportToCsv(res)
    selectedOrders.value = []
  } catch (error) {
    console.error('导出失败', error)
  }
}

// 平台名称
const getPlatformName = (platform: string) => {
  const map: Record<string, string> = {
    amazon: '亚马逊',
    ebay: 'eBay',
    shopee: 'Shopee',
    lazada: 'Lazada',
    tiktok: 'TikTok'
  }
  return map[platform] || platform
}

// 平台标签类型
const getPlatformType = (platform: string) => {
  const map: Record<string, string> = {
    amazon: 'success',
    ebay: '',
    shopee: 'warning',
    lazada: 'info',
    tiktok: 'danger'
  }
  return map[platform] || ''
}

// 订单状态名称
const getStatusName = (status: string) => {
  const map: Record<string, string> = {
    pending_payment: '待付款',
    pending_shipment: '待发货',
    shipped: '已发货',
    delivered: '已送达',
    cancelled: '已取消',
    refunded: '已退款'
  }
  return map[status] || status
}

// 订单状态标签类型
const getStatusType = (status: string) => {
  const map: Record<string, string> = {
    pending_payment: 'info',
    pending_shipment: 'warning',
    shipped: 'primary',
    delivered: 'success',
    cancelled: 'danger',
    refunded: 'danger'
  }
  return map[status] || ''
}

// 支付状态名称
const getPaymentStatusName = (status: string) => {
  const map: Record<string, string> = {
    paid: '已支付',
    unpaid: '未支付',
    partial: '部分支付'
  }
  return map[status] || status
}

// 支付状态标签类型
const getPaymentStatusType = (status: string) => {
  const map: Record<string, string> = {
    paid: 'success',
    unpaid: 'danger',
    partial: 'warning'
  }
  return map[status] || ''
}

// 格式化时间
const formatTime = (time: string) => {
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

onMounted(() => {
  loadOrders()
})
</script>

<style scoped>
.order-page {
  padding: 20px;
}

.search-card {
  margin-bottom: 15px;
}

.toolbar-card {
  margin-bottom: 15px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.toolbar .left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.table-card {
  margin-bottom: 20px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>