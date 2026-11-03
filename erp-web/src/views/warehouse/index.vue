<template>
  <div class="warehouse-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>仓库管理</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">添加仓库</el-button>
        </div>
      </template>

      <!-- 搜索筛选 -->
      <div class="filter-section">
        <el-form :inline="true" :model="filterForm" class="filter-form">
          <el-form-item label="关键词">
            <el-input v-model="filterForm.keyword" placeholder="仓库编码/名称" clearable style="width: 150px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table v-loading="loading" :data="warehouseList" stripe style="width: 100%">
        <el-table-column prop="warehouseCode" label="仓库编码" width="120" />
        <el-table-column prop="warehouseName" label="仓库名称" width="180" />
        <el-table-column prop="warehouseType" label="仓库类型" width="100">
          <template #default="{ row }">
            <el-tag>{{ getWarehouseType(row.warehouseType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="country" label="国家" width="80" />
        <el-table-column prop="province" label="省份" width="80" />
        <el-table-column prop="city" label="城市" width="80" />
        <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip />
        <el-table-column prop="contactPerson" label="联系人" width="100" />
        <el-table-column prop="contactPhone" label="联系电话" width="130" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="primary" size="small" @click="handleLocation(row)">库位</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
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
        @size-change="loadWarehouses"
        @current-change="loadWarehouses"
      />
    </el-card>

    <!-- 添加/编辑仓库对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="650px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="仓库编码" prop="warehouseCode">
              <el-input v-model="form.warehouseCode" placeholder="请输入仓库编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="仓库名称" prop="warehouseName">
              <el-input v-model="form.warehouseName" placeholder="请输入仓库名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="仓库类型" prop="warehouseType">
              <el-select v-model="form.warehouseType" placeholder="请选择仓库类型" style="width: 100%">
                <el-option label="主仓" value="main" />
                <el-option label="海外仓" value="overseas" />
                <el-option label="第三方仓" value="third_party" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否默认">
              <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="国家" prop="country">
              <el-input v-model="form.country" placeholder="国家" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="省份" prop="province">
              <el-input v-model="form.province" placeholder="省份" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="城市" prop="city">
              <el-input v-model="form.city" placeholder="城市" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="详细地址" prop="address">
          <el-input v-model="form.address" placeholder="请输入详细地址" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="联系人" prop="contactPerson">
              <el-input v-model="form.contactPerson" placeholder="联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="contactPhone">
              <el-input v-model="form.contactPhone" placeholder="联系电话" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="邮箱" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 库位管理对话框 -->
    <el-dialog v-model="locationDialogVisible" title="库位管理" width="800px">
      <div class="location-header">
        <span>仓库：{{ currentWarehouse?.warehouseName }}</span>
        <el-button type="primary" size="small" :icon="Plus" @click="handleAddLocation">添加库位</el-button>
      </div>
      <el-table v-loading="locationLoading" :data="locationList" stripe style="width: 100%; margin-top: 15px">
        <el-table-column prop="locationCode" label="库位编码" width="150" />
        <el-table-column prop="zone" label="区域" width="100" />
        <el-table-column prop="row" label="排" width="60" />
        <el-table-column prop="column" label="列" width="60" />
        <el-table-column prop="level" label="层" width="60" />
        <el-table-column prop="locationType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.locationType === 'shelf' ? '货架' : row.locationType === 'floor' ? '地面' : '其他' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '可用' : '不可用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEditLocation(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDeleteLocation(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 添加/编辑库位对话框 -->
    <el-dialog v-model="locationFormVisible" :title="locationFormTitle" width="500px">
      <el-form ref="locationFormRef" :model="locationForm" :rules="locationFormRules" label-width="80px">
        <el-form-item label="库位编码" prop="locationCode">
          <el-input v-model="locationForm.locationCode" placeholder="如：A-01-01-01" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="区域" prop="zone">
              <el-input v-model="locationForm.zone" placeholder="A区" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="排" prop="row">
              <el-input v-model="locationForm.row" placeholder="01" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="列" prop="column">
              <el-input v-model="locationForm.column" placeholder="01" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="层" prop="level">
          <el-input v-model="locationForm.level" placeholder="01" />
        </el-form-item>
        <el-form-item label="类型" prop="locationType">
          <el-select v-model="locationForm.locationType" placeholder="请选择类型" style="width: 100%">
            <el-option label="货架" value="shelf" />
            <el-option label="地面" value="floor" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="locationForm.status">
            <el-radio :label="1">可用</el-radio>
            <el-radio :label="0">不可用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="locationFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="locationSubmitLoading" @click="handleLocationSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import { warehouseApi, type Warehouse, type WarehouseLocation } from '@/api/warehouse'
import { unwrapPage } from '@/utils/response'

const loading = ref(false)
const submitLoading = ref(false)
const warehouseList = ref<Warehouse[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('添加仓库')
const formRef = ref()

// 分页
const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

// 筛选
const filterForm = reactive({
  keyword: ''
})

// 表单数据
const form = reactive({
  id: undefined as number | undefined,
  warehouseCode: '',
  warehouseName: '',
  warehouseType: 'main',
  country: '',
  province: '',
  city: '',
  address: '',
  contactPerson: '',
  contactPhone: '',
  email: '',
  status: 1,
  isDefault: 0
})

// 表单校验
const formRules = {
  warehouseCode: [{ required: true, message: '请输入仓库编码', trigger: 'blur' }],
  warehouseName: [{ required: true, message: '请输入仓库名称', trigger: 'blur' }],
  warehouseType: [{ required: true, message: '请选择仓库类型', trigger: 'change' }],
  country: [{ required: true, message: '请输入国家', trigger: 'blur' }],
  city: [{ required: true, message: '请输入城市', trigger: 'blur' }]
}

// 库位管理
const locationDialogVisible = ref(false)
const locationLoading = ref(false)
const locationList = ref<WarehouseLocation[]>([])
const currentWarehouse = ref<Warehouse | null>(null)
const locationFormVisible = ref(false)
const locationFormTitle = ref('添加库位')
const locationFormRef = ref()
const locationSubmitLoading = ref(false)

const locationForm = reactive({
  id: undefined as number | undefined,
  warehouseId: undefined as number | undefined,
  locationCode: '',
  locationType: 'shelf',
  zone: '',
  row: '',
  column: '',
  level: '',
  status: 1
})

const locationFormRules = {
  locationCode: [{ required: true, message: '请输入库位编码', trigger: 'blur' }],
  locationType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

// 加载仓库列表
const loadWarehouses = async () => {
  loading.value = true
  try {
    const params = {
      current: pagination.current,
      size: pagination.size,
      keyword: filterForm.keyword
    }
    const res = await warehouseApi.getWarehouseList(params)
    const pageData = unwrapPage(res)
    warehouseList.value = pageData.records || []
    pagination.total = pageData.total || 0
  } catch (error) {
    console.error('加载仓库列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  pagination.current = 1
  loadWarehouses()
}

// 重置
const handleReset = () => {
  filterForm.keyword = ''
  handleSearch()
}

// 添加仓库
const handleAdd = () => {
  dialogTitle.value = '添加仓库'
  form.id = undefined
  form.warehouseCode = ''
  form.warehouseName = ''
  form.warehouseType = 'main'
  form.country = ''
  form.province = ''
  form.city = ''
  form.address = ''
  form.contactPerson = ''
  form.contactPhone = ''
  form.email = ''
  form.status = 1
  form.isDefault = 0
  dialogVisible.value = true
}

// 编辑仓库
const handleEdit = (row: Warehouse) => {
  dialogTitle.value = '编辑仓库'
  form.id = row.id
  form.warehouseCode = row.warehouseCode
  form.warehouseName = row.warehouseName
  form.warehouseType = row.warehouseType
  form.country = row.country
  form.province = row.province || ''
  form.city = row.city
  form.address = row.address || ''
  form.contactPerson = row.contactPerson || ''
  form.contactPhone = row.contactPhone || ''
  form.email = row.email || ''
  form.status = row.status
  form.isDefault = row.isDefault
  dialogVisible.value = true
}

// 提交表单
const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    if (form.id) {
      await warehouseApi.updateWarehouse(form)
      ElMessage.success('更新成功')
    } else {
      await warehouseApi.createWarehouse(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadWarehouses()
  } catch (error) {
    console.error('保存仓库失败:', error)
  } finally {
    submitLoading.value = false
  }
}

// 删除仓库
const handleDelete = (row: Warehouse) => {
  ElMessageBox.confirm('确定删除该仓库吗？', '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      await warehouseApi.deleteWarehouse(row.id)
      ElMessage.success('删除成功')
      loadWarehouses()
    } catch (error) {
      console.error('删除仓库失败:', error)
    }
  }).catch(() => {})
}

// 库位管理
const handleLocation = async (row: Warehouse) => {
  currentWarehouse.value = row
  locationDialogVisible.value = true
  await loadLocations(row.id)
}

const loadLocations = async (warehouseId: number) => {
  locationLoading.value = true
  try {
    const res = await warehouseApi.getLocations(warehouseId)
    locationList.value = res || []
  } catch (error) {
    console.error('加载库位列表失败:', error)
  } finally {
    locationLoading.value = false
  }
}

const handleAddLocation = () => {
  locationFormTitle.value = '添加库位'
  locationForm.id = undefined
  locationForm.warehouseId = currentWarehouse.value?.id ?? undefined
  locationForm.locationCode = ''
  locationForm.locationType = 'shelf'
  locationForm.zone = ''
  locationForm.row = ''
  locationForm.column = ''
  locationForm.level = ''
  locationForm.status = 1
  locationFormVisible.value = true
}

const handleEditLocation = (row: WarehouseLocation) => {
  locationFormTitle.value = '编辑库位'
  locationForm.id = row.id
  locationForm.warehouseId = row.warehouseId
  locationForm.locationCode = row.locationCode
  locationForm.locationType = row.locationType
  locationForm.zone = row.zone
  locationForm.row = row.row
  locationForm.column = row.column
  locationForm.level = row.level
  locationForm.status = row.status
  locationFormVisible.value = true
}

const handleLocationSubmit = async () => {
  const valid = await locationFormRef.value?.validate().catch(() => false)
  if (!valid) return

  locationSubmitLoading.value = true
  try {
    if (locationForm.id) {
      await warehouseApi.updateLocation(locationForm)
      ElMessage.success('更新成功')
    } else {
      await warehouseApi.createLocation(locationForm)
      ElMessage.success('创建成功')
    }
    locationFormVisible.value = false
    loadLocations(currentWarehouse.value!.id)
  } catch (error) {
    console.error('保存库位失败:', error)
  } finally {
    locationSubmitLoading.value = false
  }
}

const handleDeleteLocation = (row: WarehouseLocation) => {
  ElMessageBox.confirm('确定删除该库位吗？', '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      await warehouseApi.deleteLocation(row.id)
      ElMessage.success('删除成功')
      loadLocations(currentWarehouse.value!.id)
    } catch (error) {
      console.error('删除库位失败:', error)
    }
  }).catch(() => {})
}

// 工具函数
const getWarehouseType = (type: string) => {
  const map: Record<string, string> = {
    main: '主仓',
    overseas: '海外仓',
    third_party: '第三方仓'
  }
  return map[type] || type
}

onMounted(() => {
  loadWarehouses()
})
</script>

<style scoped>
.warehouse-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-section {
  margin-bottom: 15px;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 4px;
}

.location-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 15px;
  border-bottom: 1px solid #eee;
}
</style>