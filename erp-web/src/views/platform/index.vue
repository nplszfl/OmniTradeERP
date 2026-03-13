<template>
  <div class="platform-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>平台配置</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">添加配置</el-button>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="configList"
        stripe
        style="width: 100%"
      >
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
        <el-table-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleSync(row)">同步订单</el-button>
            <el-button link type="primary" size="small" @click="handleTest(row)">测试连接</el-button>
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 配置弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑配置' : '添加配置'"
      width="600px"
      append-to-body
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="平台" prop="platform">
          <el-select v-model="form.platform" placeholder="请选择平台" style="width: 100%" :disabled="isEdit">
            <el-option label="亚马逊" value="amazon" />
            <el-option label="eBay" value="ebay" />
            <el-option label="Shopee" value="shopee" />
            <el-option label="Lazada" value="lazada" />
            <el-option label="TikTok Shop" value="tiktok" />
            <el-option label="Temu" value="temu" />
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

        <el-form-item label="API地址" prop="apiBaseUrl">
          <el-input v-model="form.apiBaseUrl" placeholder="请输入API基础URL" />
        </el-form-item>

        <el-form-item label="回调URL" prop="callbackUrl">
          <el-input v-model="form.callbackUrl" placeholder="请输入回调URL" />
        </el-form-item>

        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { platformApi, type PlatformConfig } from '@/api/platform'
import dayjs from 'dayjs'

const loading = ref(false)
const configList = ref<PlatformConfig[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()

const form = reactive({
  id: undefined as number | undefined,
  platform: '',
  shopId: '',
  shopName: '',
  apiKey: '',
  apiSecret: '',
  apiBaseUrl: '',
  callbackUrl: '',
  status: 1
})

const rules = {
 {
  platform: [{ required: true, message: '请选择平台', trigger: 'change' }],
  shopId: [{ required: true, message: '请输入店铺ID', trigger: 'blur' }],
  shopName: [{ required: true, message: '请输入店铺名称', trigger: 'blur' }],
  apiKey: [{ required: true, message: '请输入API Key', trigger: 'blur' }],
  apiSecret: [{ required: true, message: '请输入API Secret', trigger: 'blur' }]
}

const loadConfigs = async () => {
  loading.value = true
  try {
    const res = await platformApi.getConfigs()
    configList.value = res || []
  } catch (error) {
    console.error('加载配置失败', error)
  } finally {
    loading.value = false
  }
}

const handleAdd = () => {
  isEdit.value = false
  Object.assign(form, {
    id: undefined,
    platform: '',
    shopId: '',
    shopName: '',
    apiKey: '',
    apiSecret: '',
    apiBaseUrl: '',
    callbackUrl: '',
    status: 1
  })
  dialogVisible.value = true
}

const handleEdit = (row: PlatformConfig) => {
  isEdit.value = true
  Object.assign(form, row)
  dialogVisible.value = true
}

const handleSave = async () => {
  try {await formRef.value.validate()
    await platformApi.saveConfig(form)
    ElMessage.success(isEdit.value ? '更新成功' : '添加成功')
    dialogVisible.value = false
    loadConfigs()
  } catch (error) {
    console.error('保存失败', error)
  }
}

const handleDelete = (row: PlatformConfig) => {
  ElMessageBox.confirm(`确定删除配置 ${row.shopName} 吗？`, '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      await platformApi.deleteConfig(row.id)
      ElMessage.success('删除成功')
      loadConfigs()
    } catch (error) {
      console.error('删除失败', error)
    }
  })
}

const handleTest = async (row: PlatformConfig) => {
  try {
    await platformApi.testConnection(row.id)
    ElMessage.success('连接测试成功')
  } catch (error) {
    ElMessage.error('连接测试失败')
    console.error('连接测试失败', error)
  }
}

const handleSync = async (row: PlatformConfig) => {
  try {
    await platformApi.syncOrders(row.platform, row.shopId)
    ElMessage.success('订单同步已开始')
  } catch (error) {
    console.error('同步失败', error)
  }
}

const getPlatformName = (platform: string) => {
  const map: Record<string, string> = {
    amazon: '亚马逊',
    ebay: 'eBay',
    shopee: 'Shopee',
    lazada: 'Lazada',
    tiktok: 'TikTok Shop',
    temu: 'Temu'
  }
  return map[platform] || platform
}

const formatTime = (time: string) => {
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
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
</style>
