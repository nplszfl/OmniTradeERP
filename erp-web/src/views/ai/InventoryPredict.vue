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

    <!-- 📌 创建采购单对话框：AI 建议要变成真实采购单，
         缺一个输入（供应商 / 数量）→ 让用户确认，而不是**假装已经建好了** -->
    <el-dialog v-model="poVisible" title="创建采购单" width="480px">
      <el-form label-width="90px">
        <el-form-item label="SKU">
          <span>{{ poRow?.sku }}（{{ poRow?.productName }}）</span>
        </el-form-item>
        <el-form-item label="建议补货量">
          <span>{{ poRow?.recommendedReplenishment }} 件</span>
        </el-form-item>
        <el-form-item label="供应商" required>
          <el-select v-model="poForm.supplierId" placeholder="请选择供应商" style="width: 100%">
            <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="采购数量" required>
          <el-input-number v-model="poForm.quantity" :min="1" :step="10" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="poVisible = false">取消</el-button>
        <el-button type="primary" :loading="poSaving" @click="submitPO">创建</el-button>
      </template>
    </el-dialog>

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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { inventoryPredictApi } from '@/api/ai'
import { purchaseApi } from '@/api/purchase'

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

// 📌 onMounted 里**吞掉 rejection** —— load() 现在会 rethrow，
//    直接 `onMounted(load)` 会变成 unhandled rejection。
onMounted(() => { load().catch(() => {}) })

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
    // 📌 **rethrow**：让调用方（refresh）知道这次加载**失败了**，
    //    否则 refresh 会在失败后照样弹「预测已刷新」（自相矛盾）。
    throw error
  } finally {
    loading.value = false
    lastSync.value = new Date().toLocaleString()
  }
}

// ⚠️ 原来 `load()` **没 await** 就弹「预测已刷新」——
//    → 提示先于数据到达，用户看到「已刷新」但表格还是旧数据；
//    → 而且 load() 失败时 catch 里报的是「加载失败」，成功提示却已经弹过了（自相矛盾）。
//    → 现在 await 完再提示。
async function refresh() {
  loading.value = true
  try {
    await load()
    ElMessage.success('预测已刷新')
  } catch (e) {
    // load() 内部已经报过错，这里**不再弹成功**
    console.error('刷新预测失败', e)
  } finally {
    loading.value = false
  }
}
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
// ⚠️ 原来直接弹「已基于 AI 建议创建 xxx 的采购单（N 件）」——
//    **采购单根本没建**，后端一个请求都没发（规则 51 / 72）。
//    → 后端 `POST /api/purchase/orders` 是真实现，但**必须有 supplierId**，
//      而 AI 建议里没有供应商 → 所以这里**弹对话框让用户选**，
//      而不是替他编一个供应商。
const poVisible = ref(false)
const poSaving = ref(false)
const poRow = ref<any>(null)
const suppliers = ref<any[]>([])
const poForm = reactive({ supplierId: '' as string, quantity: 1 })

async function createPO(row: any) {
  poRow.value = row
  poForm.supplierId = ''
  poForm.quantity = row.recommendedReplenishment > 0 ? row.recommendedReplenishment : 1
  poVisible.value = true
  if (!suppliers.value.length) {
    try {
      suppliers.value = await purchaseApi.suppliers()
      if (!suppliers.value.length) {
        ElMessage.warning('没有可选的供应商，请先维护供应商')
      }
    } catch (e) {
      console.error('加载供应商失败', e)
      ElMessage.error('加载供应商失败')
    }
  }
}

async function submitPO() {
  if (!poForm.supplierId) {
    ElMessage.warning('请选择供应商')
    return
  }
  if (!poForm.quantity || poForm.quantity <= 0) {
    ElMessage.warning('采购数量必须大于 0')
    return
  }
  poSaving.value = true
  try {
    await purchaseApi.create({
      supplierId: poForm.supplierId,
      currencyCode: 'CNY',
      status: 'PENDING',
      remark: `基于 AI 补货建议创建（建议量 ${poRow.value?.recommendedReplenishment} 件）`,
      items: [{
        sku: poRow.value?.sku,
        productName: poRow.value?.productName,
        quantity: poForm.quantity,
      }],
    })
    ElMessage.success(`采购单已创建：${poRow.value?.sku} × ${poForm.quantity}`)
    poVisible.value = false
  } catch (e) {
    console.error('创建采购单失败', e)
    ElMessage.error('创建采购单失败')
  } finally {
    poSaving.value = false
  }
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
