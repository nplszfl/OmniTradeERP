<template>
  <div class="analytics-page">
    <!-- 顶部 4 大指标卡 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <el-card shadow="never" class="kpi-card">
          <div class="kpi-label">订单总数（近 30 天）</div>
          <div class="kpi-value">{{ data.totalOrders ?? '--' }}</div>
          <div v-if="ordersTrendPct === null" class="kpi-trend none">环比 --</div>
          <div v-else :class="['kpi-trend', ordersTrendPct >= 0 ? 'up' : 'down']">
            {{ ordersTrendPct >= 0 ? '▲' : '▼' }} {{ Math.abs(ordersTrendPct).toFixed(1) }}%
            <span class="kpi-trend-note">（对比昨日）</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="kpi-card">
          <div class="kpi-label">营收（USD 等值）</div>
          <div class="kpi-value">$ {{ (data.totalRevenue ?? 0).toLocaleString() }}</div>
          <div v-if="salesTrendPct === null" class="kpi-trend none">环比 --</div>
          <div v-else :class="['kpi-trend', salesTrendPct >= 0 ? 'up' : 'down']">
            {{ salesTrendPct >= 0 ? '▲' : '▼' }} {{ Math.abs(salesTrendPct).toFixed(1) }}%
            <span class="kpi-trend-note">（今日 vs 昨日）</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="kpi-card">
          <div class="kpi-label">活跃客户</div>
          <div class="kpi-value">{{ data.totalCustomers ?? '--' }}</div>
          <div class="kpi-trend none">暂无同比数据</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="kpi-card">
          <div class="kpi-label">在售 SKU</div>
          <div class="kpi-value">{{ data.totalProducts ?? '--' }}</div>
          <div class="kpi-trend none">暂无同比数据</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势图 -->
    <el-card shadow="never" class="block">
      <template #header>
        <div class="card-head">
          <span>订单与营收趋势</span>
          <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
            <el-radio-button :value="7">7 天</el-radio-button>
            <el-radio-button :value="30">30 天</el-radio-button>
            <el-radio-button :value="90">90 天</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div v-if="trend.length === 0" class="placeholder">暂无数据</div>
      <div v-else class="chart-mock">
        <div v-for="(d, i) in trend" :key="i" class="bar" :style="{ height: Math.min(100, d.count * 8) + '%' }">
          <span class="bar-label">{{ d.date }}</span>
          <span class="bar-value">{{ d.count }}</span>
        </div>
      </div>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" class="block">
          <template #header><span>各平台营收占比</span></template>
          <el-table :data="data.revenueByPlatform || []" stripe size="small">
            <el-table-column prop="platform" label="平台" />
            <el-table-column prop="revenue" label="营收（USD）" />
            <el-table-column label="占比">
              <template #default="{ row }">
                <el-progress
                  :percentage="Math.round((row.revenue / totalPlatformRevenue) * 100) || 0"
                  :show-text="true"
                />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="block">
          <template #header><span>Top 10 商品</span></template>
          <el-table :data="data.topProducts || []" stripe size="small">
            <el-table-column prop="name" label="商品" show-overflow-tooltip />
            <el-table-column prop="sold" label="销量" width="100" />
            <el-table-column prop="revenue" label="营收（USD）" width="140" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { analyticsApi } from '@/api/analytics'

const data = ref<any>({})
const trend = ref<{ date: string; count: number; revenue: number }[]>([])
const trendDays = ref(30)
const overview = ref<any>({})

const totalPlatformRevenue = computed(() => {
  if (!data.value.revenueByPlatform) return 0
  return data.value.revenueByPlatform.reduce((s: number, x: any) => s + (x.revenue || 0), 0)
})

/**
 * 📌 **环比百分比**（2026-11-04 修）：
 * 之前 KPI 卡上的「▲ 12.4%」是**写死的字符串** → 界面在报一个**不存在的**趋势
 * （规则 19：事实标志不能撒谎 —— 这属于「界面在撒谎」）。
 *
 * → 现在用后端 `OverviewStats` 的**真实** `todaySales` / `yesterdaySales` 算。
 * 📌 **除零 / 缺数据一律返回 null** → 模板显示「环比 --」，
 *    **绝不显示一个编造的数字**。
 */
function calcTrendPct(current?: number | null, previous?: number | null): number | null {
  if (current == null || previous == null) return null
  if (!previous) return null // 昨日为 0 → 百分比无意义（不能写 Infinity，也不能写 0）
  return ((current - previous) / previous) * 100
}

const salesTrendPct = computed(() =>
  calcTrendPct(overview.value?.todaySales, overview.value?.yesterdaySales)
)

/** 📌 订单环比：后端 overview 里**只有** todayOrders（笔数），
 *    没有 yesterdayOrders → 拿不到真实的订单环比。
 *    → 显示 null（模板渲染「环比 --」），**不编造**。 */
const ordersTrendPct = computed<number | null>(() => null)

onMounted(async () => {
  try {
    data.value = await analyticsApi.dashboard()
  } catch (e) {
    // ⚠️ 之前这里回退到 mock 数据，把后端的 404/500 全部掩盖了，
    //    页面看起来「有数据」但其实全是假的。现在改成显式报错。
    ElMessage.error('看板数据加载失败，请检查分析服务状态')
    data.value = {}
  }
  loadOverview()
  loadTrend()
})

/** 概览统计（KPI 卡的真实环比数据来源） */
async function loadOverview() {
  try {
    overview.value = await analyticsApi.overview()
  } catch {
    // 📌 拿不到就**如实置空** → 模板显示「环比 --」
    //    绝不能像以前那样回退到写死的 12.4%。
    overview.value = {}
  }
}

async function loadTrend() {
  try {
    trend.value = await analyticsApi.ordersTrend(trendDays.value)
  } catch {
    trend.value = []
    ElMessage.error('趋势数据加载失败')
  }
}
</script>

<style scoped>
.analytics-page { padding: 16px; }
.kpi-row { margin-bottom: 16px; }
.kpi-card .kpi-label { color: #999; font-size: 13px; }
.kpi-card .kpi-value { font-size: 28px; font-weight: 600; margin: 8px 0; }
.kpi-card .kpi-trend { font-size: 12px; }
.kpi-card .kpi-trend.up { color: #67c23a; }
.kpi-card .kpi-trend.down { color: #f56c6c; }
/* 📌 「暂无数据」用灰色 —— 不能用 up/down 的颜色，
      否则「没有数据」看起来像「有数据」 */
.kpi-card .kpi-trend.none { color: #999; }
.kpi-trend-note { color: #bbb; }
.block { margin-bottom: 16px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.chart-mock {
  display: flex; align-items: flex-end; gap: 4px; height: 220px;
  border-bottom: 1px solid #eee; padding-bottom: 20px;
}
.chart-mock .bar {
  flex: 1; min-width: 0; background: linear-gradient(180deg, #409eff, #79bbff);
  border-radius: 3px 3px 0 0; position: relative; color: #fff; text-align: center;
}
.chart-mock .bar-label { position: absolute; bottom: -18px; left: 0; right: 0; font-size: 10px; color: #999; }
.chart-mock .bar-value { position: absolute; top: -18px; left: 0; right: 0; font-size: 10px; color: #666; }
.placeholder { text-align: center; color: #999; padding: 40px 0; }
</style>
