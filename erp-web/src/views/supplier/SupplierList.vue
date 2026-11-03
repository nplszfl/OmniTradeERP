<template>
  <div class="supplier-page">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon" style="background: #409eff">
            <el-icon :size="24"><OfficeBuilding /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.totalSuppliers }}</div>
            <div class="stat-label">供应商总数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon" style="background: #67c23a">
            <el-icon :size="24"><CircleCheck /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.activeSuppliers }}</div>
            <div class="stat-label">活跃供应商</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon" style="background: #e6a23c">
            <el-icon :size="24"><Star /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.avgRating.toFixed(1) }}</div>
            <div class="stat-label">平均评分</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon" style="background: #f56c6c">
            <el-icon :size="24"><Money /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">${{ formatAmount(stats.totalPurchaseAmount) }}</div>
            <div class="stat-label">累计采购</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Tab切换 -->
    <el-tabs v-model="activeTab">
      <!-- 供应商列表 -->
      <el-tab-pane label="供应商管理" name="supplier">
        <!-- 搜索筛选 -->
        <el-card class="search-card" shadow="never">
          <el-form :inline="true" :model="searchForm">
            <el-form-item label="关键词">
              <el-input v-model="searchForm.keyword" placeholder="供应商名称/联系人" clearable style="width: 150px" />
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="searchForm.type" placeholder="全部类型" clearable style="width: 120px">
                <el-option label="工厂" value="FACTORY" />
                <el-option label="贸易商" value="TRADER" />
                <el-option label="代理商" value="AGENT" />
              </el-select>
            </el-form-item>
            <el-form-item label="等级">
              <el-select v-model="searchForm.level" placeholder="全部等级" clearable style="width: 100px">
                <el-option label="A级" value="A" />
                <el-option label="B级" value="B" />
                <el-option label="C级" value="C" />
              </el-select>
            </el-form-item>
            <el-form-item label="国家">
              <el-input v-model="searchForm.country" placeholder="国家名称" clearable style="width: 120px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
              <el-button :icon="Refresh" @click="handleReset">重置</el-button>
              <el-button type="success" :icon="Plus" @click="handleAddSupplier">新增供应商</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 供应商列表 -->
        <el-card class="table-card" shadow="never">
          <el-table v-loading="loading" :data="supplierList" stripe>
            <el-table-column prop="name" label="供应商名称" width="180" fixed>
              <template #default="{ row }">
                <el-link type="primary" @click="handleViewSupplier(row)">{{ row.name }}</el-link>
              </template>
            </el-table-column>
            <el-table-column prop="contactPerson" label="联系人" width="100" />
            <el-table-column prop="phone" label="电话" width="130" />
            <el-table-column prop="email" label="邮箱" width="180" show-overflow-tooltip />
            <el-table-column prop="type" label="类型" width="90">
              <template #default="{ row }">
                <el-tag size="small">{{ getTypeName(row.type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="level" label="等级" width="70">
              <template #default="{ row }">
                <el-tag :type="getLevelType(row.level)" size="small">{{ row.level }}级</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="rating" label="评分" width="80">
              <template #default="{ row }">
                <el-rate v-model="row.rating" disabled :max="5" style="display: inline-block" />
              </template>
            </el-table-column>
            <el-table-column prop="totalOrders" label="采购次数" width="90" />
            <el-table-column prop="totalPurchaseAmount" label="累计采购" width="120">
              <template #default="{ row }">
                ${{ formatAmount(row.totalPurchaseAmount) }}
              </template>
            </el-table-column>
            <el-table-column prop="country" label="国家" width="100" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleViewSupplier(row)">详情</el-button>
                <el-button link type="primary" size="small" @click="handleEditSupplier(row)">编辑</el-button>
                <el-button link type="success" size="small" @click="handleCreatePurchase(row)">采购</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="pagination.current"
            v-model:page-size="pagination.size"
            :total="pagination.total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadSuppliers"
            @current-change="loadSuppliers"
            style="margin-top: 20px; justify-content: flex-end"
          />
        </el-card>
      </el-tab-pane>

      <!-- 采购订单 -->
      <el-tab-pane label="采购订单" name="purchase">
        <el-card class="search-card" shadow="never">
          <el-form :inline="true" :model="purchaseSearchForm">
            <el-form-item label="供应商">
              <el-select v-model="purchaseSearchForm.supplierId" placeholder="全部供应商" clearable style="width: 180px">
                <el-option v-for="s in supplierList" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="订单状态">
              <el-select v-model="purchaseSearchForm.status" placeholder="全部状态" clearable style="width: 130px">
                <el-option label="待审批" value="PENDING" />
                <el-option label="已审批" value="APPROVED" />
                <el-option label="采购中" value="PURCHASING" />
                <el-option label="运输中" value="IN_TRANSIT" />
                <el-option label="已收货" value="RECEIVED" />
                <el-option label="已取消" value="CANCELLED" />
              </el-select>
            </el-form-item>
            <el-form-item label="付款状态">
              <el-select v-model="purchaseSearchForm.paymentStatus" placeholder="全部" clearable style="width: 120px">
                <el-option label="未付款" value="UNPAID" />
                <el-option label="部分付款" value="PARTIAL" />
                <el-option label="已付款" value="PAID" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadPurchaseOrders">搜索</el-button>
              <el-button type="success" :icon="Plus" @click="showPurchaseDialog = true">新建采购单</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="table-card" shadow="never">
          <el-table v-loading="purchaseLoading" :data="purchaseOrderList" stripe>
            <el-table-column prop="orderNo" label="采购单号" width="160" fixed>
              <template #default="{ row }">
                <el-link type="primary">{{ row.orderNo }}</el-link>
              </template>
            </el-table-column>
            <el-table-column prop="supplierName" label="供应商" width="150" />
            <el-table-column prop="totalAmount" label="订单金额" width="120">
              <template #default="{ row }">
                {{ row.totalAmount }} {{ row.currency }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="订单状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getPurchaseStatusType(row.status)" size="small">{{ getPurchaseStatusName(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="paymentStatus" label="付款状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getPaymentStatusType(row.paymentStatus)" size="small">{{ getPaymentStatusName(row.paymentStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="expectedArrivalAt" label="预计到货" width="150">
              <template #default="{ row }">
                {{ row.expectedArrivalAt ? formatTime(row.expectedArrivalAt) : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="purchaserName" label="采购员" width="100" />
            <el-table-column prop="createTime" label="创建时间" width="150">
              <template #default="{ row }">
                {{ formatTime(row.createTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small">详情</el-button>
                <el-button link type="success" size="small" v-if="row.status === 'PENDING'">审批</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="purchasePagination.current"
            v-model:page-size="purchasePagination.size"
            :total="purchasePagination.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadPurchaseOrders"
            @current-change="loadPurchaseOrders"
            style="margin-top: 20px; justify-content: flex-end"
          />
        </el-card>
      </el-tab-pane>

      <!-- 供应商绩效 -->
      <el-tab-pane label="供应商绩效" name="evaluation">
        <el-card class="search-card" shadow="never">
          <el-form :inline="true">
            <el-form-item label="选择供应商">
              <el-select v-model="evalSupplierId" placeholder="请选择供应商" style="width: 200px" @change="loadEvaluationStats">
                <el-option v-for="s in supplierList" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-form>
        </el-card>

        <el-row :gutter="20" v-if="evalSupplierId">
          <el-col :span="8">
            <el-card shadow="never">
              <div class="eval-score">
                <div class="score-label">质量评分</div>
                <div class="score-value">{{ evaluationStats.avgQuality.toFixed(1) }}</div>
                <el-progress :percentage="evaluationStats.avgQuality * 20" :stroke-width="10" />
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never">
              <div class="eval-score">
                <div class="score-label">交货评分</div>
                <div class="score-value">{{ evaluationStats.avgDelivery.toFixed(1) }}</div>
                <el-progress :percentage="evaluationStats.avgDelivery * 20" :stroke-width="10" status="success" />
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never">
              <div class="eval-score">
                <div class="score-label">价格评分</div>
                <div class="score-value">{{ evaluationStats.avgPrice.toFixed(1) }}</div>
                <el-progress :percentage="evaluationStats.avgPrice * 20" :stroke-width="10" status="warning" />
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>

    <!-- 供应商详情弹窗 -->
    <el-dialog v-model="supplierDetailVisible" title="供应商详情" width="800px" append-to-body>
      <el-descriptions :column="2" border v-if="currentSupplier">
        <el-descriptions-item label="供应商名称">{{ currentSupplier.name }}</el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag>{{ getTypeName(currentSupplier.type) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="联系人">{{ currentSupplier.contactPerson }}</el-descriptions-item>
        <el-descriptions-item label="电话">{{ currentSupplier.phone }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ currentSupplier.email }}</el-descriptions-item>
        <el-descriptions-item label="等级">
          <el-tag :type="getLevelType(currentSupplier.level)">{{ currentSupplier.level }}级</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="国家">{{ currentSupplier.country }}</el-descriptions-item>
        <el-descriptions-item label="城市">{{ currentSupplier.city || '-' }}</el-descriptions-item>
        <el-descriptions-item label="详细地址" :span="2">{{ currentSupplier.address || '-' }}</el-descriptions-item>
        <el-descriptions-item label="主要产品">{{ currentSupplier.category || '-' }}</el-descriptions-item>
        <el-descriptions-item label="合作开始">{{ currentSupplier.cooperationStartAt ? formatTime(currentSupplier.cooperationStartAt) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="采购次数">{{ currentSupplier.totalOrders }}</el-descriptions-item>
        <el-descriptions-item label="累计采购">${{ formatAmount(currentSupplier.totalPurchaseAmount) }}</el-descriptions-item>
        <el-descriptions-item label="评分">
          <el-rate v-model="currentSupplier.rating" disabled :max="5" />
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ currentSupplier.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <template #footer>
        <el-button @click="supplierDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑供应商弹窗 -->
    <el-dialog v-model="supplierFormVisible" :title="isEditSupplier ? '编辑供应商' : '新增供应商'" width="600px" append-to-body>
      <el-form :model="supplierForm" :rules="supplierFormRules" ref="supplierFormRef" label-width="100px">
        <el-form-item label="供应商名称" prop="name">
          <el-input v-model="supplierForm.name" placeholder="请输入供应商名称" />
        </el-form-item>
        <el-form-item label="联系人" prop="contactPerson">
          <el-input v-model="supplierForm.contactPerson" placeholder="请输入联系人" />
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="supplierForm.phone" placeholder="请输入电话" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="supplierForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="supplierForm.type" placeholder="请选择类型" style="width: 100%">
            <el-option label="工厂" value="FACTORY" />
            <el-option label="贸易商" value="TRADER" />
            <el-option label="代理商" value="AGENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="supplierForm.level" placeholder="请选择等级" style="width: 100%">
            <el-option label="A级" value="A" />
            <el-option label="B级" value="B" />
            <el-option label="C级" value="C" />
          </el-select>
        </el-form-item>
        <el-form-item label="国家">
          <el-input v-model="supplierForm.country" placeholder="请输入国家" />
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="supplierForm.city" placeholder="请输入城市" />
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model="supplierForm.address" type="textarea" :rows="2" placeholder="请输入详细地址" />
        </el-form-item>
        <el-form-item label="主要产品">
          <el-input v-model="supplierForm.category" placeholder="请输入主要产品类别" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="supplierForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="supplierFormVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitSupplier">确定</el-button>
      </template>
    </el-dialog>

    <!-- 新建采购单弹窗 -->
    <el-dialog v-model="showPurchaseDialog" title="新建采购单" width="700px" append-to-body>
      <el-form :model="purchaseForm" label-width="100px">
        <el-form-item label="供应商" required>
          <el-select v-model="purchaseForm.supplierId" placeholder="请选择供应商" style="width: 100%">
            <el-option v-for="s in supplierList" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="预计到货">
          <el-date-picker v-model="purchaseForm.expectedArrivalAt" type="date" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="付款方式">
          <el-select v-model="purchaseForm.paymentMethod" placeholder="请选择付款方式" style="width: 100%">
            <el-option label="TT电汇" value="TT" />
            <el-option label="信用证" value="LC" />
            <el-option label="付款交单" value="DP" />
            <el-option label="承兑交单" value="DA" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="purchaseForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showPurchaseDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreatePurchaseOrder">创建采购单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Plus, OfficeBuilding, CircleCheck, Star, Money } from '@element-plus/icons-vue'
import { supplierApi, type Supplier, type PurchaseOrder, type SupplierStats } from '@/api/supplier'
import dayjs from 'dayjs'
import { unwrapPage } from '@/utils/response'

const activeTab = ref('supplier')
const loading = ref(false)
const purchaseLoading = ref(false)

// 统计数据
const stats = reactive<SupplierStats>({
  totalSuppliers: 0,
  activeSuppliers: 0,
  avgRating: 0,
  totalPurchaseAmount: 0
})

// 供应商列表
const supplierList = ref<Supplier[]>([])
const supplierDetailVisible = ref(false)
const supplierFormVisible = ref(false)
const isEditSupplier = ref(false)
const currentSupplier = ref<Supplier | null>(null)

// 搜索表单
const searchForm = reactive({
  keyword: '',
  type: '',
  level: '',
  country: ''
})

// 分页
const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

// 供应商表单
const supplierForm = reactive<Supplier>({
  id: 0,
  name: '',
  contactPerson: '',
  phone: '',
  email: '',
  type: 'TRADER',
  country: '',
  city: '',
  address: '',
  category: '',
  cooperationStartAt: '',
  totalPurchaseAmount: 0,
  totalOrders: 0,
  rating: 5,
  level: 'B',
  remark: '',
  status: 'ACTIVE',
  createTime: '',
  updateTime: ''
})

const supplierFormRules = {
  name: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }],
  contactPerson: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

// 采购订单
const purchaseOrderList = ref<PurchaseOrder[]>([])
const purchaseSearchForm = reactive({
  supplierId: undefined as number | undefined,
  status: '',
  paymentStatus: ''
})
const purchasePagination = reactive({
  current: 1,
  size: 20,
  total: 0
})
const showPurchaseDialog = ref(false)
const purchaseForm = reactive({
  supplierId: 0,
  expectedArrivalAt: '',
  paymentMethod: 'TT',
  remark: ''
})

// 绩效评估
const evalSupplierId = ref<number | null>(null)
const evaluationStats = reactive({
  avgQuality: 0,
  avgDelivery: 0,
  avgPrice: 0,
  avgService: 0,
  avgOverall: 0
})

// 加载供应商列表
const loadSuppliers = async () => {
  loading.value = true
  try {
    const res = await supplierApi.getSupplierList({
      current: pagination.current,
      size: pagination.size,
      keyword: searchForm.keyword || undefined,
      type: searchForm.type || undefined,
      level: searchForm.level || undefined,
      country: searchForm.country || undefined
    })
    const pageData = unwrapPage(res)
    supplierList.value = pageData.records || []
    pagination.total = pageData.total || 0
  } catch (error) {
    console.error('加载供应商失败', error)
  } finally {
    loading.value = false
  }
}

// 加载统计数据
const loadStats = async () => {
  try {
    const res = await supplierApi.getSupplierStats()
    Object.assign(stats, res)
  } catch (error) {
    console.error('加载统计失败', error)
  }
}

// 搜索
const handleSearch = () => {
  pagination.current = 1
  loadSuppliers()
}

// 重置
const handleReset = () => {
  searchForm.keyword = ''
  searchForm.type = ''
  searchForm.level = ''
  searchForm.country = ''
  handleSearch()
}

// 新增供应商
const handleAddSupplier = () => {
  isEditSupplier.value = false
  resetSupplierForm()
  supplierFormVisible.value = true
}

// 编辑供应商
const handleEditSupplier = (supplier: Supplier) => {
  isEditSupplier.value = true
  Object.assign(supplierForm, supplier)
  supplierFormVisible.value = true
}

// 提交供应商表单
const handleSubmitSupplier = async () => {
  try {
    if (isEditSupplier.value) {
      await supplierApi.updateSupplier(supplierForm.id, supplierForm)
      ElMessage.success('更新成功')
    } else {
      await supplierApi.createSupplier(supplierForm)
      ElMessage.success('创建成功')
    }
    supplierFormVisible.value = false
    loadSuppliers()
    loadStats()
  } catch (error) {
    console.error('操作失败', error)
  }
}

// 重置供应商表单
const resetSupplierForm = () => {
  Object.assign(supplierForm, {
    id: 0,
    name: '',
    contactPerson: '',
    phone: '',
    email: '',
    type: 'TRADER',
    country: '',
    city: '',
    address: '',
    category: '',
    cooperationStartAt: '',
    totalPurchaseAmount: 0,
    totalOrders: 0,
    rating: 5,
    level: 'B',
    remark: '',
    status: 'ACTIVE'
  })
}

// 查看供应商详情
const handleViewSupplier = (supplier: Supplier) => {
  currentSupplier.value = supplier
  supplierDetailVisible.value = true
}

// 创建采购
const handleCreatePurchase = (supplier: Supplier) => {
  purchaseForm.supplierId = supplier.id
  showPurchaseDialog.value = true
}

// 创建采购单
const handleCreatePurchaseOrder = async () => {
  if (!purchaseForm.supplierId) {
    ElMessage.warning('请选择供应商')
    return
  }
  try {
    await supplierApi.createPurchaseOrder({
      id: 0,
      orderNo: '',
      supplierId: purchaseForm.supplierId,
      supplierName: supplierList.value.find(s => s.id === purchaseForm.supplierId)?.name || '',
      status: 'PENDING',
      totalAmount: 0,
      paidAmount: 0,
      currency: 'USD',
      expectedArrivalAt: purchaseForm.expectedArrivalAt,
      actualArrivalAt: '',
      paymentMethod: purchaseForm.paymentMethod,
      paymentStatus: 'UNPAID',
      purchaserId: 1,
      purchaserName: '',
      remark: purchaseForm.remark,
      createTime: ''
    })
    ElMessage.success('采购单创建成功')
    showPurchaseDialog.value = false
    activeTab.value = 'purchase'
    loadPurchaseOrders()
  } catch (error) {
    console.error('创建采购单失败', error)
  }
}

// 加载采购订单
const loadPurchaseOrders = async () => {
  purchaseLoading.value = true
  try {
    const res = await supplierApi.getPurchaseOrderList({
      current: purchasePagination.current,
      size: purchasePagination.size,
      supplierId: purchaseSearchForm.supplierId,
      status: purchaseSearchForm.status || undefined,
      paymentStatus: purchaseSearchForm.paymentStatus || undefined
    })
    const pageData = unwrapPage(res)
    purchaseOrderList.value = pageData.records || []
    purchasePagination.total = pageData.total || 0
  } catch (error) {
    console.error('加载采购订单失败', error)
  } finally {
    purchaseLoading.value = false
  }
}

// 加载评估统计
const loadEvaluationStats = async () => {
  if (!evalSupplierId.value) return
  try {
    const res = await supplierApi.getEvaluationStats(evalSupplierId.value)
    Object.assign(evaluationStats, res)
  } catch (error) {
    console.error('加载评估统计失败', error)
  }
}

// 工具函数
const getTypeName = (type: string) => {
  const map: Record<string, string> = { FACTORY: '工厂', TRADER: '贸易商', AGENT: '代理商' }
  return map[type] || type
}

const getLevelType = (level: string) => {
  const map: Record<string, string> = { A: 'danger', B: 'warning', C: 'info' }
  return map[level] || ''
}

const getPurchaseStatusName = (status: string) => {
  const map: Record<string, string> = {
    PENDING: '待审批', APPROVED: '已审批', PURCHASING: '采购中',
    IN_TRANSIT: '运输中', RECEIVED: '已收货', CANCELLED: '已取消'
  }
  return map[status] || status
}

const getPurchaseStatusType = (status: string) => {
  const map: Record<string, string> = {
    PENDING: 'info', APPROVED: 'success', PURCHASING: 'warning',
    IN_TRANSIT: 'primary', RECEIVED: 'success', CANCELLED: 'danger'
  }
  return map[status] || ''
}

const getPaymentStatusName = (status: string) => {
  const map: Record<string, string> = { UNPAID: '未付款', PARTIAL: '部分付款', PAID: '已付款' }
  return map[status] || status
}

const getPaymentStatusType = (status: string) => {
  const map: Record<string, string> = { UNPAID: 'danger', PARTIAL: 'warning', PAID: 'success' }
  return map[status] || ''
}

const formatAmount = (amount: number) => amount?.toFixed(2) || '0.00'
const formatTime = (time: string) => time ? dayjs(time).format('YYYY-MM-DD') : '-'

onMounted(() => {
  loadSuppliers()
  loadStats()
  loadPurchaseOrders()
})
</script>

<style scoped>
.supplier-page {
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
  margin-bottom: 15px;
}

.table-card {
  margin-bottom: 20px;
}

.eval-score {
  text-align: center;
  padding: 20px;
}

.score-label {
  font-size: 14px;
  color: #666;
  margin-bottom: 10px;
}

.score-value {
  font-size: 32px;
  font-weight: bold;
  color: #409eff;
  margin-bottom: 15px;
}
</style>