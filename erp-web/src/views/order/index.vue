<template>
  <div class="order-page">
    <!-- 搜索筛选 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="平台">
          <el-select v-model="searchForm.platform" placeholder="全部平台" clearable style="width: 150px">
            <el-option label="亚马逊" value="amazon" />
            <el-option label="eBay" value="ebay" />
            <el-option label="Shopee" value="shopee" />
            <el-option label="Lazada" value="lazada" />
          </el-select>
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 150px">
            <el-option label="待付款" value="pending_payment" />
            <el-option label="待发货" value="pending_shipment" />
            <el-option label="已发货" value="shipped" />
            <el-option label="已送达" value="delivered" />
            <el-option label="已取消" value="cancelled" />
          </el-select>
        </el-form-item>

        <el-form-item label="订单号">
          <el-input v-model="searchForm.orderNo" placeholder="内部/平台订单号" clearable style="width: 200px" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 订单列表 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>订单列表</span>
          <el-button type="primary" size="small" :icon="RefreshLeft" @click="loadOrders">同步订单</el-button>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="orderList"
        stripe
        style="width: 100%"
      >
        <el-table-column prop="internalOrderNo" label="内部订单号" width="180" fixed />
        <el-table-column prop="platformOrderNo" label="平台订单号" width="150" />
        <el-table-column prop="platform" label="平台" width="100">
          <template #default="{ row }">
            <el-tag :type="getPlatformType(row.platform)">{{ getPlatformName(row.platform) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="buyerName" label="买家" width="120" />
        <el-table-column prop="orderAmount" label="金额" width="120">
          <template #default="{ row }">
            {{ row.orderAmount }} {{ row.currencyCode }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusName(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="trackingNumber" label="物流单号" width="180" show-overflow-tooltip />
        <el-table-column prop="recipientCountry" label="国家" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleViewDetail(row)">详情</el-button>
            <el-button link type="primary" size="small" @click="handleShip(row)" v-if="row.status === 'pending_shipment'">发货</el-button>
            <el-button link type="danger" size="small" @click="handleCancel(row)" v-if="row.status === 'pending_shipment'">取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadOrders"
        @current-change="loadOrders"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <!-- 订单详情弹窗 -->
    <el-dialog v-model="detailVisible" title="订单详情" width="800px" append-to-body>
      <el-descriptions :column="2" border v-if="currentOrder">
        <el-descriptions-item label="内部订单号">{{ currentOrder.internalOrderNo }}</el-descriptions-item>
        <el-descriptions-item label="平台订单号">{{ currentOrder.platformOrderNo }}</el-descriptions-item>
        <el-descriptions-item label="平台">{{ getPlatformName(currentOrder.platform) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(currentOrder.status)">{{ getStatusName(currentOrder.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="买家">{{ currentOrder.buyerName }}</el-descriptions-item>
        <el-descriptions-item label="买家邮箱">{{ currentOrder.buyerEmail }}</el-descriptions-item>
        <el-descriptions-item label="订单金额">{{ currentOrder.orderAmount }} {{ currentOrder.currencyCode }}</el-descriptions-item>
        <el-descriptions-item label="支付状态">{{ currentOrder.paymentStatus }}</el-descriptions-item>
        <el-descriptions-item label="物流单号">{{ currentOrder.trackingNumber || '-' }}</el-descriptions-item>
        <el-descriptions-item label="物流公司">{{ currentOrder.logisticsCompany || '-' }}</el-descriptions-item>
        <el-descriptions-item label="收货人" :span="2">{{ currentOrder.recipientName }}</el-descriptions-item>
        <el-descriptions-item label="收货地址" :span="2">
          {{ currentOrder.recipientCountry }} {{ currentOrder.recipientState }} {{ currentOrder.recipientCity }}
          {{ currentOrder.recipientAddress }} {{ currentOrder.recipientPostalCode }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ formatTime(currentOrder.createTime) }}</el-descriptions-item>
      </el-descriptions>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 发货弹窗 -->
    <el-dialog v-model="shipVisible" title="订单发货" width="500px" append-to-body>
      <el-form :model="shipForm" label-width="100px">
        <el-form-item label="物流单号">
          <el-input v-model="shipForm.trackingNumber" placeholder="请输入物流单号" />
        </el-form-item>
        <el-form-item label="物流公司">
          <el-select v-model="shipForm.logisticsCompany" placeholder="请选择物流公司" style="width: 100%">
            <el-option label="DHL" value="dhl" />
            <el-option label="FedEx" value="fedex" />
            <el-option label="UPS" value="ups" />
            <el-option label="顺丰国际" value="sf_international" />
            <el-option label="邮政小包" value="china_post" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="shipVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmShip">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, RefreshLeft } from '@element-plus/icons-vue'
import { orderApi, type Order } from '@/api/order'
import dayjs from 'dayjs'

const loading = ref(false)
const orderList = ref<Order[]>([])
const detailVisible = ref(false)
const shipVisible = ref(false)
const currentOrder = ref<Order | null>(null)
const currentShipOrder = ref<Order | null>(null)

const searchForm = reactive({
  platform: '',
  status: '',
  orderNo: ''
})

const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

const shipForm = reactive({
  trackingNumber: '',
  logisticsCompany: ''
})

const loadOrders = async () => {
  loading.value = true
  try {
    const res = await orderApi.getOrders({
      current: pagination.current,
      size: pagination.size,
      platform: searchForm.platform || undefined,
      status: searchForm.status || undefined
    })
    orderList.value = res.records || []
    pagination.total = res.total || 0
  } catch (error) {
    console.error('加载订单失败', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.current = 1
  loadOrders()
}

const handleReset = () => {
  searchForm.platform = ''
  searchForm.status = ''
  searchForm.orderNo = ''
  handleSearch()
}

const handleViewDetail = (order: Order) => {
  currentOrder.value = order
  detailVisible.value = true
}

const handleShip = (order: Order) => {
  currentShipOrder.value = order
  shipForm.trackingNumber = ''
  shipForm.logisticsCompany = ''
  shipVisible.value = true
}

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

const getPlatformName = (platform: string) => {
  const map: Record<string, string> = {
    amazon: '亚马逊',
    ebay: 'eBay',
    shopee: 'Shopee',
    lazada: 'Lazada'
  }
  return map[platform] || platform
}

const getPlatformType = (platform: string) => {
  const map: Record<string, any> = {
    amazon: 'success',
    shopee: 'warning',
    lazada: 'info'
  }
  return map[platform] || ''
}

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

const getStatusType = (status: string) => {
  const map: Record<string, any> = {
    pending_payment: 'info',
    pending_shipment: 'warning',
    shipped: 'primary',
    delivered: 'success',
    cancelled: 'danger',
    refunded: 'danger'
  }
  return map[status] || ''
}

const formatTime = (time: string) => {
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
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
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
