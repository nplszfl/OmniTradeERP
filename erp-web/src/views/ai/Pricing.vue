<template>
  <div class="pricing-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>智能定价（基于竞品 + 库存 + 利润率）</span>
          <el-input v-model="sku" placeholder="输入 SKU 查询" style="width: 280px" @keyup.enter="load" clearable>
            <template #append><el-button @click="load">查询</el-button></template>
          </el-input>
        </div>
      </template>

      <el-row v-if="current" :gutter="16" class="current-block">
        <el-col :span="6">
          <el-statistic title="当前价格" :value="current.currentPrice" :precision="2" prefix="$" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="建议价格" :value="current.suggestedPrice" :precision="2" prefix="$" :value-style="{ color: '#67c23a' }" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="竞品均价" :value="current.competitorAvg" :precision="2" prefix="$" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="预期利润" :value="current.expectedProfit" :precision="2" prefix="$" :value-style="{ color: '#409eff' }" />
        </el-col>
      </el-row>

      <el-table :data="list" v-loading="loading" stripe class="task-table">
        <el-table-column prop="sku"          label="SKU" width="180" />
        <el-table-column prop="productName"  label="商品" />
        <el-table-column prop="strategy"     label="定价策略" width="120" />
        <el-table-column label="建议幅度" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="row.suggestedPrice > row.currentPrice ? 'success' : 'danger'">
              {{ ((row.suggestedPrice - row.currentPrice) / row.currentPrice * 100).toFixed(1) }}%
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showDetail(row)">查看</el-button>
            <el-button link type="success" size="small" @click="apply(row)" :disabled="row.applied">采纳</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { pricingApi } from '@/api/ai'

const sku = ref('')
const current = ref<any>(null)
const list = ref<any[]>([])
const loading = ref(false)

onMounted(load)

async function load() {
  loading.value = true
  try {
    list.value = await pricingApi.listTasks({})
    if (sku.value) current.value = await pricingApi.suggest(sku.value)
  } catch (error) {
    // ⚠️ 之前这里回退到 mock 数据，把后端的 404/500 全部掩盖了。
    //    现在定价服务已有真实接口（/api/pricing/tasks、/api/pricing/suggest/{sku}），
    //    失败就如实报错。
    console.error('加载定价建议失败', error)
    list.value = []
    current.value = null
    ElMessage.error('定价建议加载失败')
  } finally { loading.value = false }
}

// ⚠️ 之前这里用 Math.random() 现场造竞品均价和预期利润 —— 每次点开都不一样，纯属假数据。
//    现在直接展示后端返回的字段。
function showDetail(row: any) { current.value = row }
function apply(row: any) { row.applied = true; ElMessage.success(`已采纳 ${row.sku} 的建议价`) }
</script>

<style scoped>
.pricing-page { padding: 16px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.current-block { margin-bottom: 24px; padding: 16px; background: #fafafa; border-radius: 4px; }
.task-table { margin-top: 12px; }
</style>
