<template>
  <div class="product-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>商品管理</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">添加商品</el-button>
        </div>
      </template>

      <!-- 搜索筛选 -->
      <div class="filter-section">
        <el-form :inline="true" :model="filterForm" class="filter-form">
          <el-form-item label="关键词">
            <el-input v-model="filterForm.keyword" placeholder="SKU/商品名称" clearable style="width: 150px" />
          </el-form-item>
          <el-form-item label="品牌">
            <el-input v-model="filterForm.brand" placeholder="品牌" clearable style="width: 120px" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filterForm.status" placeholder="全部" clearable style="width: 100px">
              <el-option label="上架" :value="1" />
              <el-option label="下架" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table v-loading="loading" :data="productList" stripe style="width: 100%">
        <el-table-column prop="internalSku" label="SKU" width="150" />
        <el-table-column prop="productName" label="商品名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="brand" label="品牌" width="120" />
        <el-table-column prop="category" label="分类" width="100" />
        <el-table-column prop="mainImage" label="主图" width="80">
          <template #default="{ row }">
            <el-image v-if="row.mainImage" :src="row.mainImage" style="width: 40px; height: 40px" :preview-src-list="[row.mainImage]" fit="cover" />
            <span v-else class="no-image">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="costPrice" label="成本价" width="100" align="right">
          <template #default="{ row }">
            ¥{{ row.costPrice?.toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column prop="salePrice" label="销售价" width="100" align="right">
          <template #default="{ row }">
            ¥{{ row.salePrice?.toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link :type="row.status === 1 ? 'warning' : 'success'" size="small" @click="handleToggleStatus(row)">
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
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
        @size-change="loadProducts"
        @current-change="loadProducts"
      />
    </el-card>

    <!-- 添加/编辑商品对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="SKU" prop="internalSku">
              <el-input v-model="form.internalSku" placeholder="请输入SKU" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品名称" prop="productName">
              <el-input v-model="form.productName" placeholder="请输入商品名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="品牌" prop="brand">
              <el-input v-model="form.brand" placeholder="请输入品牌" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类" prop="category">
              <el-input v-model="form.category" placeholder="请输入分类" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="成本价" prop="costPrice">
              <el-input-number v-model="form.costPrice" :min="0" :precision="2" :step="0.01" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="销售价" prop="salePrice">
              <el-input-number v-model="form.salePrice" :min="0" :precision="2" :step="0.01" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="重量(g)" prop="weight">
              <el-input-number v-model="form.weight" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="尺寸" prop="dimensions">
              <el-input v-model="form.dimensions" placeholder="长x宽x高(cm)" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="主图URL" prop="mainImage">
          <el-input v-model="form.mainImage" placeholder="请输入主图URL">
            <template #append>
              <el-button @click="handleImagePreview">预览</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="商品描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入商品描述" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">上架</el-radio>
            <el-radio :label="0">下架</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 图片预览 -->
    <el-dialog v-model="previewVisible" title="图片预览" width="500px">
      <el-image v-if="previewUrl" :src="previewUrl" style="width: 100%; height: 400px" fit="contain" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { productApi, type Product } from '@/api/product'
import { unwrapPage } from '@/utils/response'

const loading = ref(false)
const submitLoading = ref(false)
const productList = ref<Product[]>([])
const dialogVisible = ref(false)
const previewVisible = ref(false)
const previewUrl = ref('')
const dialogTitle = ref('添加商品')
const formRef = ref()

// 分页
const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

// 筛选表单
const filterForm = reactive({
  keyword: '',
  brand: '',
  status: undefined as number | undefined
})

// 表单数据
const form = reactive({
  id: undefined as number | undefined,
  internalSku: '',
  productName: '',
  brand: '',
  category: '',
  mainImage: '',
  description: '',
  weight: 0,
  dimensions: '',
  costPrice: 0,
  salePrice: 0,
  status: 1
})

// 表单校验
const formRules = {
  internalSku: [{ required: true, message: '请输入SKU', trigger: 'blur' }],
  productName: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  brand: [{ required: true, message: '请输入品牌', trigger: 'blur' }],
  category: [{ required: true, message: '请输入分类', trigger: 'blur' }],
  costPrice: [{ required: true, message: '请输入成本价', trigger: 'blur' }],
  salePrice: [{ required: true, message: '请输入销售价', trigger: 'blur' }]
}

// 加载商品列表
const loadProducts = async () => {
  loading.value = true
  try {
    const params = {
      current: pagination.current,
      size: pagination.size,
      ...filterForm
    }
    const res = await productApi.getProducts(params)
    // 映射后端字段 → 前端字段
    const pageData = unwrapPage(res)
    productList.value = (pageData.records || []).map((item: any) => ({
      id: item.id,
      internalSku: item.sku || '',
      productName: item.name || '',
      brand: item.brand || '',
      category: item.categoryId || '',
      mainImage: item.mainImage || '',
      images: item.images ? JSON.parse(item.images) : [],
      description: item.description || '',
      weight: item.weight || 0,
      dimensions: item.length && item.width && item.height ? `${item.length}x${item.width}x${item.height}` : '',
      costPrice: item.costPrice || 0,
      salePrice: item.salePrice || 0,
      currencyCode: 'CNY',
      status: item.status ?? 1,
      createTime: item.createTime || '',
      updateTime: item.updateTime || ''
    }))
    pagination.total = pageData.total || (pageData.records || []).length
  } catch (error) {
    console.error('加载商品列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  pagination.current = 1
  loadProducts()
}

// 重置
const handleReset = () => {
  filterForm.keyword = ''
  filterForm.brand = ''
  filterForm.status = undefined
  handleSearch()
}

// 添加商品
const handleAdd = () => {
  dialogTitle.value = '添加商品'
  form.id = undefined
  form.internalSku = ''
  form.productName = ''
  form.brand = ''
  form.category = ''
  form.mainImage = ''
  form.description = ''
  form.weight = 0
  form.dimensions = ''
  form.costPrice = 0
  form.salePrice = 0
  form.status = 1
  dialogVisible.value = true
}

// 编辑商品
const handleEdit = (row: Product) => {
  dialogTitle.value = '编辑商品'
  form.id = row.id
  form.internalSku = row.internalSku
  form.productName = row.productName
  form.brand = row.brand
  form.category = row.category
  form.mainImage = row.mainImage
  form.description = row.description
  form.weight = row.weight || 0
  form.dimensions = row.dimensions || ''
  form.costPrice = row.costPrice || 0
  form.salePrice = row.salePrice || 0
  form.status = row.status
  dialogVisible.value = true
}

// 提交表单
const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    if (form.id) {
      await productApi.updateProduct(form)
      ElMessage.success('更新成功')
    } else {
      await productApi.createProduct(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadProducts()
  } catch (error) {
    console.error('保存商品失败:', error)
  } finally {
    submitLoading.value = false
  }
}

// 删除商品
const handleDelete = (row: Product) => {
  ElMessageBox.confirm('确定删除该商品吗？', '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      await productApi.deleteProduct(row.id)
      ElMessage.success('删除成功')
      loadProducts()
    } catch (error) {
      console.error('删除商品失败:', error)
    }
  }).catch(() => {})
}

// 上下架
const handleToggleStatus = async (row: Product) => {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 1 ? '上架' : '下架'
  try {
    await productApi.updateProduct({ id: row.id, status: newStatus })
    ElMessage.success(`${action}成功`)
    loadProducts()
  } catch (error) {
    console.error('操作失败:', error)
  }
}

// 预览图片
const handleImagePreview = () => {
  if (form.mainImage) {
    previewUrl.value = form.mainImage
    previewVisible.value = true
  } else {
    ElMessage.warning('请先输入图片URL')
  }
}

// 工具函数
const formatTime = (time: string) => {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

onMounted(() => {
  loadProducts()
})
</script>

<style scoped>
.product-page {
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

.no-image {
  color: #909399;
}
</style>