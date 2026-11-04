<template>
  <div class="recommend-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>AI 选品推荐（基于历史销量 + 平台热点 + 利润率）</span>
          <el-button type="primary" :loading="refreshing" @click="refresh">刷新推荐</el-button>
        </div>
      </template>

      <el-row :gutter="16" class="summary">
        <el-col :span="6"><el-statistic title="候选商品数" :value="list.length" /></el-col>
        <el-col :span="6"><el-statistic title="平均推荐得分" :value="avgScore" :precision="2" suffix="/ 100" /></el-col>
        <el-col :span="6"><el-statistic title="平均预期利润率" :value="avgMargin" :precision="2" suffix="%" /></el-col>
        <el-col :span="6"><el-statistic title="刷新时间" :value="lastRefresh" /></el-col>
      </el-row>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="sku"          label="SKU" width="180" />
        <el-table-column prop="productName"  label="推荐商品" />
        <el-table-column prop="category"     label="类目" width="120" />
        <el-table-column label="推荐得分" width="140">
          <template #default="{ row }">
            <el-progress :percentage="row.score" :stroke-width="14" :format="() => row.score + ' 分'" />
          </template>
        </el-table-column>
        <el-table-column prop="expectedMargin" label="预期利润率" width="120">
          <template #default="{ row }">
            <el-tag :type="row.expectedMargin > 30 ? 'success' : 'warning'" size="small">{{ row.expectedMargin }}%</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reason"       label="推荐理由"  show-overflow-tooltip />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewDetail(row)">详情</el-button>
            <el-button link type="success" size="small" @click="addToShelf(row)">加入选品库</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="detailVisible" title="推荐详情" width="600px">
      <div v-if="detail">
        <h3>{{ detail.productName }}</h3>
        <p>{{ detail.reason }}</p>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="SKU">{{ detail.sku }}</el-descriptions-item>
          <el-descriptions-item label="类目">{{ detail.category }}</el-descriptions-item>
          <el-descriptions-item label="推荐得分">{{ detail.score }} 分</el-descriptions-item>
          <el-descriptions-item label="预期利润率">{{ detail.expectedMargin }}%</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { recommendApi } from '@/api/ai'

const list = ref<any[]>([])
const loading = ref(false)
const refreshing = ref(false)
const detailVisible = ref(false)
const detail = ref<any>(null)
const lastRefresh = ref('')

const avgScore = computed(() => {
  if (!list.value.length) return 0
  return list.value.reduce((s, x) => s + (x.score || 0), 0) / list.value.length
})
const avgMargin = computed(() => {
  if (!list.value.length) return 0
  return list.value.reduce((s, x) => s + (x.expectedMargin || 0), 0) / list.value.length
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    list.value = await recommendApi.list({})
  } catch (e) {
    // ⚠️ **不要用 mock 数据兜底**（2026-11-04 修）——
    //    这里原来在接口失败时填 5 条**看起来完全像真的**的选品推荐
    //    （连「近 90 天搜索量上涨 320%」这种细节都编好了）。
    //    → 接口挂了页面看着「有推荐」，实则**全是假的**（规则 12 / 19）。
    //    → 而且这些推荐**没有真实依据**，用户照着选品会亏钱。
    //    失败就**如实置空 + 报错**。
    console.error('选品推荐加载失败', e)
    list.value = []
    ElMessage.error('选品推荐加载失败')
  } finally {
    loading.value = false
    lastRefresh.value = new Date().toLocaleTimeString()
  }
}

async function refresh() {
  refreshing.value = true
  try {
    await recommendApi.refresh()
    await load()
  } catch { await load() }
  refreshing.value = false
  ElMessage.success('推荐已刷新')
}

function viewDetail(row: any) { detail.value = row; detailVisible.value = true }
function addToShelf(row: any) { ElMessage.success(`已加入选品库：${row.sku}`) }
</script>

<style scoped>
.recommend-page { padding: 16px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.summary { margin-bottom: 16px; padding: 12px; background: #fafafa; border-radius: 4px; }
</style>
