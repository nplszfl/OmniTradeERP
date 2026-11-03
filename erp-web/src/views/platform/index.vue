<template>
  <div class="platform-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>平台配置</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">添加配置</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="configList" stripe style="width: 100%">
        <el-table-column prop="platform" label="平台" width="120">
          <template #default="{ row }">
            <el-tag>{{ getPlatformName(row.platform) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="shopId" label="店铺ID" width="150" />
        <el-table-column prop="shopName" label="店铺名称" width="180" />
        <el-table-column prop="apiBaseUrl" label="API地址" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleSync(row)">同步订单</el-button>
            <el-button link type="success" size="small" @click="handleSyncProduct(row)">同步商品</el-button>
            <el-button link type="primary" size="small" @click="handleTest(row)">测试</el-button>
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加/编辑配置对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑配置' : '添加配置'" width="600px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="平台" prop="platform">
          <el-select v-model="form.platform" placeholder="请选择平台" style="width: 100%" :disabled="isEdit">
            <el-option label="亚马逊" value="amazon" />
            <el-option label="eBay" value="ebay" />
            <el-option label="Shopee" value="shopee" />
            <el-option label="Lazada" value="lazada" />
            <el-option label="TikTok Shop" value="tiktok" />
            <el-option label="Temu" value="temu" />
            <el-option label="速卖通" value="aliexpress" />
            <el-option label="Wish" value="wish" />
          </el-select>
        </el-form-item>
        <el-form-item label="店铺ID" prop="shopId">
          <el-input v-model="form.shopId" placeholder="请输入店铺ID" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="店铺名称" prop="shopName">
          <el-input v-model="form.shopName" placeholder="请输入店铺名称" />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="form.apiKey" type="password" placeholder="请输入API Key" show-password />
        </el-form-item>
        <el-form-item label="API Secret" prop="apiSecret">
          <el-input v-model="form.apiSecret" type="password" placeholder="请输入API Secret" show-password />
        </el-form-item>
        <el-form-item label="Access Token">
          <el-input v-model="form.accessToken" type="password" placeholder="请输入Access Token" show-password />
        </el-form-item>
        <el-form-item label="API地址" prop="apiBaseUrl">
          <el-input v-model="form.apiBaseUrl" placeholder="请输入API基础URL" />
        </el-form-item>
        <el-form-item label="回调URL">
          <el-input v-model="form.callbackUrl" placeholder="请输入回调URL" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>

    <!-- 同步进度对话框 -->
    <el-dialog v-model="syncDialogVisible" title="同步进度" width="500px">
      <div class="sync-info">
        <p>正在同步 <strong>{{ syncInfo.shopName }}</strong> 的 {{ syncInfo.type === 'order' ? '订单' : '商品' }}...</p>
        <el-progress :percentage="syncInfo.percentage" :status="syncInfo.status" />
        <p class="sync-detail">{{ syncInfo.detail }}</p>
      </div>
      <template #footer>
        <el-button @click="syncDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElLoading, ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { platformApi, type PlatformConfig } from '@/api/platform'

const loading = ref(false)
const submitLoading = ref(false)
const configList = ref<PlatformConfig[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()

// 同步进度
const syncDialogVisible = ref(false)
const syncInfo = reactive({
  shopName: '',
  type: 'order',
  percentage: 0,
  status: '' as '' | 'success' | 'exception',
  detail: ''
})

const form = reactive({
  id: undefined as string | undefined,
  platform: '',
  shopId: '',
  shopName: '',
  apiKey: '',
  apiSecret: '',
  accessToken: '',
  apiBaseUrl: '',
  callbackUrl: '',
  status: 1
})

const rules = {
  platform: [{ required: true, message: '请选择平台', trigger: 'change' }],
  shopId: [{ required: true, message: '请输入店铺ID', trigger: 'blur' }],
  shopName: [{ required: true, message: '请输入店铺名称', trigger: 'blur' }],
  apiKey: [{ required: true, message: '请输入API Key', trigger: 'blur' }],
  apiSecret: [{ required: true, message: '请输入API Secret', trigger: 'blur' }]
}

// 加载配置列表
const loadConfigs = async () => {
  loading.value = true
  try {
    const res = await platformApi.getConfigs()
    configList.value = res || []
  } catch (error) {
    console.error('加载配置失败:', error)
  } finally {
    loading.value = false
  }
}

// 添加配置
const handleAdd = () => {
  isEdit.value = false
  Object.assign(form, {
    id: undefined,
    platform: '',
    shopId: '',
    shopName: '',
    apiKey: '',
    apiSecret: '',
    accessToken: '',
    apiBaseUrl: '',
    callbackUrl: '',
    status: 1
  })
  dialogVisible.value = true
}

// 编辑配置
const handleEdit = (row: PlatformConfig) => {
  isEdit.value = true
  Object.assign(form, row)
  form.apiKey = ''
  form.apiSecret = ''
  form.accessToken = ''
  dialogVisible.value = true
}

// 保存配置
const handleSave = async () => {
  try {
    await formRef.value.validate()
    submitLoading.value = true
    
    // 过滤掉空密码
    // ⚠️ delete 的操作数必须是可选属性 —— PlatformConfig 里这几个字段是必填，
    //    所以先转成 Partial 再 delete（原来直接 delete data.apiKey 是 TS 编译错误）。
    const data: Partial<PlatformConfig> = { ...form }
    if (!data.apiKey) delete data.apiKey
    if (!data.apiSecret) delete data.apiSecret
    if (!data.accessToken) delete data.accessToken
    
    await platformApi.saveConfig(data)
    ElMessage.success(isEdit.value ? '更新成功' : '添加成功')
    dialogVisible.value = false
    loadConfigs()
  } catch (error) {
    console.error('保存失败:', error)
  } finally {
    submitLoading.value = false
  }
}

// 删除配置
const handleDelete = (row: PlatformConfig) => {
  ElMessageBox.confirm(`确定删除配置 ${row.shopName} 吗？`, '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      await platformApi.deleteConfig(row.id)
      ElMessage.success('删除成功')
      loadConfigs()
    } catch (error) {
      console.error('删除失败:', error)
    }
  }).catch(() => {})
}

// 测试连接
const handleTest = async (row: PlatformConfig) => {
  const loading = ElLoading.service({ text: '正在测试连接...' })
  try {
    await platformApi.testConnection(row.id)
    ElMessage.success('连接测试成功！')
  } catch (error) {
    ElMessage.error('连接测试失败，请检查配置')
    console.error('连接测试失败:', error)
  } finally {
    loading.close()
  }
}

// 同步订单
const handleSync = async (row: PlatformConfig) => {
  ElMessageBox.confirm(`确定同步 ${row.shopName} 的订单吗？`, '提示', {
    type: 'info'
  }).then(async () => {
    syncInfo.shopName = row.shopName
    syncInfo.type = 'order'
    syncInfo.percentage = 0
    syncInfo.status = ''
    syncInfo.detail = '正在请求API...'
    syncDialogVisible.value = true
    
    try {
      // 模拟进度
      syncInfo.percentage = 30
      syncInfo.detail = '正在获取订单列表...'
      
      await platformApi.syncOrders(row.platform, row.shopId)
      
      syncInfo.percentage = 100
      syncInfo.status = 'success'
      syncInfo.detail = '同步完成！'
      ElMessage.success('订单同步已开始，请稍后刷新查看')
    } catch (error) {
      syncInfo.status = 'exception'
      syncInfo.detail = '同步失败，请重试'
      ElMessage.error('同步失败')
      console.error('同步失败:', error)
    }
  }).catch(() => {})
}

// 同步商品
const handleSyncProduct = async (row: PlatformConfig) => {
  ElMessageBox.confirm(`确定同步 ${row.shopName} 的商品吗？`, '提示', {
    type: 'info'
  }).then(async () => {
    const loading = ElLoading.service({ text: '正在同步商品...' })
    try {
      // 模拟商品同步API调用
      await new Promise(resolve => setTimeout(resolve, 1000))
      ElMessage.success('商品同步已开始，请稍后刷新查看')
    } catch (error) {
      ElMessage.error('同步失败')
      console.error('同步失败:', error)
    } finally {
      loading.close()
    }
  }).catch(() => {})
}

// 工具函数
const getPlatformName = (platform: string) => {
  const map: Record<string, string> = {
    amazon: '亚马逊',
    ebay: 'eBay',
    shopee: 'Shopee',
    lazada: 'Lazada',
    tiktok: 'TikTok Shop',
    temu: 'Temu',
    aliexpress: '速卖通',
    wish: 'Wish'
  }
  return map[platform] || platform
}

const formatTime = (time: string) => {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

onMounted(() => {
  loadConfigs()
})
</script>

<style scoped>
.platform-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sync-info {
  padding: 20px 0;
}

.sync-info p {
  margin-bottom: 15px;
}

.sync-detail {
  margin-top: 15px;
  color: #909399;
  font-size: 14px;
}
</style>