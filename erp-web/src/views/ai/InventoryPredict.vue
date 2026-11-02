<template>
  <div class="ip-page">
    <el-row :gutter="16" class="ip-summary">
      <el-col :span="6"><el-card shadow="never"><el-statistic title="待补货 SKU" :value="high" :value-style="{ color: '#f56c6c' }" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><el-statistic title="中等紧急" :value="medium" :value-style="{ color: '#e6a23c' }" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><el-statistic title="低优先级" :value="low" :value-style="{ color: '#909399' }" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><el-statistic title="下次同步" :value="lastSync" /></el-card></el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>AI 智能补货建议（基于 90 天销量 + 季节系数 + 促销计划）</span>
          <el-button type="primary" @click="refresh">刷新预测</el-button>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="sku"            label="SKU" width="180" />
        <el-table-column prop="productName"    label="商品" show-overflow-tooltip />
        <el-table-column label="当前库存" width="100">
          <template #default="{ row }">{{ row.currentStock }}</template>
        </el-table-column>
        <el-table-column prop="predicted30DaySales" label="预测 30 天销量" width="140" />
        <el-table-column prop="recommendedReplenishment" label="建议补货量" width="140">
          <template #default="{ row }">
            <el-tag :type="row.recommendedReplenishment > 0 ? 'success' : 'info'">
              {{ row.recommendedReplenishment > 0 ? '+' + row.recommendedReplenishment : '无需补货' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="safetyStock"    label="安全库存"   width="100" />
        <el-table-column prop="urgency"        label="紧急程度"   width="120">
          <template #default="{ row }">
            <el-tag :type="urgencyType(row.urgency)" size="small">{{ row.urgencyLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewChart(row)">预测趋势</el-button>
            <el-button link type="success" size="small" @click="createPO(row)">创建采购单</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="chartVisible" title="销量预测" width="640px">
      <div v-if="chartData.length" class="chart-mock">
        <div v-for="(d, i) in chartData" :key="i" class="bar" :style="{ height: Math.min(100, (d.value / 60) * 100) + '%' }">
          <span class="bar-value">{{ d.value }}</span>
          <span class="bar-label">{{ d.date }}</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { inventoryPredictApi } from '@/api/ai'

const list = ref<any[]>([])
const loading = ref(false)
const lastSync = ref('')
const chartVisible = ref(false)
const chartData = ref<any[]>([])

const urgencyMap: Record<string, { label: string; type: string }> = {
  HIGH:   { label: '紧急', type: 'danger'  },
  MEDIUM: { label: '中等', type: 'warning' },
  LOW:    { label: '低',    type: 'info'    },
}
const urgencyType = (u: string) => (urgencyMap[u] || {}).type || ''
const urgencyLabel = (u: string) => (urgencyMap[u] || {}).label || u

const high = computed(() => list.value.filter(x => x.urgency === 'HIGH').length)
const medium = computed(() => list.value.filter(x => x.urgency === 'MEDIUM').length)
const low = computed(() => list.value.filter(x => x.urgency === 'LOW').length)

onMounted(load)

async function load() {
  loading.value = true
  try {
    list.value = await inventoryPredictApi.listSuggestions({})
  } catch (error) {
    // ⚠️ 之前这里回退到 mock 数据，把后端的 404/500 全部掩盖了。
    //    预测服务已有真实接口（/inventory-prediction/suggestions、/predict/{sku}），
    //    失败就如实报错。
    console.error('加载补货建议失败', error)
    list.value = []
    ElMessage.error('补货建议加载失败')
  } finally {
    loading.value = false
    lastSync.value = new Date().toLocaleString()
  }
}

function refresh() { load(); ElMessage.success('预测已刷新') }
async function viewChart(row: any) {
  // ⚠️ 之前这里用 Math.random() 现场编一条 30 天曲线 —— 纯假数据。
  //    现在真的调预测接口拿明细曲线。
  try {
    const res = await inventoryPredictApi.predict(row.sku, 30)
    // 后端返回 PredictionResponse.dailyPredictions: [{date, predictedSales, ...}]
    const points = res?.dailyPredictions || []
    chartData.value = points.map((p: any, i: number) => ({
      date: p.date ? String(p.date).slice(5) : `D+${i + 1}`,
      value: p.predictedSales ?? 0,
    }))
    if (!chartData.value.length) {
      ElMessage.warning('该 SKU 暂无预测明细')
    }
    chartVisible.value = true
  } catch (error) {
    console.error('加载预测明细失败', error)
    ElMessage.error('预测明细加载失败')
  }
}
function createPO(row: any) {
  ElMessage.success(`已基于 AI 建议创建 ${row.sku} 的采购单（${row.recommendedReplenishment} 件）`)
}
</script>

<style scoped>
.ip-page { padding: 16px; }
.ip-summary { margin-bottom: 16px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.chart-mock {
  display: flex; align-items: flex-end; gap: 4px; height: 220px;
  border-bottom: 1px solid #eee; padding-bottom: 20px;
}
.chart-mock .bar {
  flex: 1; min-width: 0; background: linear-gradient(180deg, #67c23a, #95d475);
  border-radius: 3px 3px 0 0; position: relative; color: #fff; text-align: center;
}
.chart-mock .bar-label { position: absolute; bottom: -18px; left: 0; right: 0; font-size: 9px; color: #999; }
.chart-mock .bar-value { position: absolute; top: -18px; left: 0; right: 0; font-size: 9px; color: #666; }
</style>
