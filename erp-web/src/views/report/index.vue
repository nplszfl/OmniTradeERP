<template>
  <div class="report-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>报表中心</span>
          <div>
            <el-select v-model="type" placeholder="报表类型" style="width: 160px; margin-right: 8px">
              <el-option label="销售报表" value="sales" />
              <el-option label="库存报表" value="inventory" />
              <el-option label="财务月报" value="finance" />
            </el-select>
            <el-button type="primary" @click="generate">生成报表</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="name" label="报表名称" />
        <el-table-column prop="type" label="类型" width="120" />
        <el-table-column prop="period" label="周期" width="200">
          <template #default="{ row }">
            {{ row.period || '-' }}
            <el-tag v-if="row.granularity" size="small" type="info">{{ row.granularity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="generatedAt" label="生成时间" width="200">
          <template #default="{ row }">
            {{ row.generatedAt ? String(row.generatedAt).replace('T', ' ').slice(0, 19) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'GENERATED' ? 'success' : 'warning'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="download(row)" :disabled="row.status !== 'GENERATED'">下载</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { reportApi } from '@/api/tenant'

const type = ref('sales')
const list = ref<any[]>([])
const loading = ref(false)

onMounted(async () => {
  await loadList()
})

async function loadList() {
  loading.value = true
  try {
    // erp-reporting-service 的接口统一包 Result（{code, message, data}）
    const res = await reportApi.list({})
    list.value = res?.data ?? (Array.isArray(res) ? res : [])
  } catch (e) {
    // ⚠️ 原来这里用 mock 数据兜底，接口 404 时页面看着有数据，其实是前端写死的假数据。
    console.error('报表列表加载失败', e)
    list.value = []
    ElMessage.error('报表列表加载失败')
  } finally {
    loading.value = false
  }
}

async function generate() {
  try {
    const res = await reportApi.generate({ type: type.value })
    ElMessage.success(res?.message || '报表生成任务已提交')
    await loadList()
  } catch (e) {
    console.error('报表生成失败', e)
    ElMessage.error('报表生成失败')
  }
}

function download(row: any) {
  if (!row?.type) {
    ElMessage.warning('该报表没有类型，无法下载')
    return
  }
  reportApi.download(row.type, {
    startDate: row.period ? `${row.period}-01` : undefined,
    endDate: row.period ? row.period : undefined,
  })
    .then(() => ElMessage.success(`下载报表：${row.name}`))
    .catch(e => {
      console.error('报表下载失败', e)
      ElMessage.error('报表下载失败')
    })
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除 ${row.name}？`, '提示', { type: 'warning' })
  list.value = list.value.filter(r => r.id !== row.id)
  ElMessage.success('已删除')
}
</script>

<style scoped>
.report-page { padding: 16px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
</style>
