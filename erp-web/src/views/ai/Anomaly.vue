<template>
  <div class="anomaly-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>订单异常检测（AI 自动评分）</span>
          <div>
            <el-select v-model="filter.status" placeholder="状态" clearable style="width: 140px; margin-right: 8px" @change="load">
              <el-option label="待处理" value="PENDING" />
              <el-option label="已确认" value="CONFIRMED" />
              <el-option label="已驳回" value="REJECTED" />
            </el-select>
            <el-button type="primary" :loading="running" @click="runAll">对所有未审核订单重新检测</el-button>
          </div>
        </div>
      </template>

      <el-row :gutter="16" class="stats">
        <el-col :span="6"><el-card shadow="never"><el-statistic title="总订单" :value="stats.total" /></el-card></el-col>
        <el-col :span="6"><el-card shadow="never"><el-statistic title="正常订单" :value="stats.normal" :value-style="{ color: '#67c23a' }" /></el-card></el-col>
        <el-col :span="6"><el-card shadow="never"><el-statistic title="异常订单" :value="stats.anomaly" :value-style="{ color: '#f56c6c' }" /></el-card></el-col>
        <el-col :span="6"><el-card shadow="never"><el-statistic title="异常率" :value="stats.rate" :precision="2" suffix="%" :value-style="{ color: '#e6a23c' }" /></el-card></el-col>
      </el-row>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="orderNo"       label="订单号"    width="180" />
        <el-table-column prop="platform"      label="平台"      width="120" />
        <el-table-column label="金额" width="140">
          <template #default="{ row }">$ {{ row.amount.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="异常分数" width="180">
          <template #default="{ row }">
            <el-progress :percentage="row.anomalyScore" :stroke-width="14"
              :status="row.anomalyScore > 80 ? 'exception' : (row.anomalyScore > 50 ? 'warning' : 'success')" />
          </template>
        </el-table-column>
        <el-table-column prop="anomalyType"   label="异常类型"  width="150" />
        <el-table-column prop="detectedAt"    label="检测时间"  width="180" />
        <el-table-column prop="status"        label="状态"      width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" size="small" v-if="row.status === 'PENDING'" @click="confirm(row, 'CONFIRMED')">确认</el-button>
            <el-button link type="danger"  size="small" v-if="row.status === 'PENDING'" @click="confirm(row, 'REJECTED')">驳回</el-button>
            <el-tag size="small" v-else-if="row.status === 'CONFIRMED'">已确认异常</el-tag>
            <el-tag size="small" type="info" v-else>已驳回</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { anomalyApi } from '@/api/ai'

const list = ref<any[]>([])
const loading = ref(false)
/** 「重新检测」逐单跑，别让用户以为没反应 */
const running = ref(false)
const stats = ref({ total: 0, normal: 0, anomaly: 0, rate: 0 })
const filter = ref({ status: '' })

const statusType = (s: string) => ({ PENDING: 'warning', CONFIRMED: 'danger', REJECTED: 'info' }[s] || '')

onMounted(load)

async function load() {
  loading.value = true
  try {
    list.value = await anomalyApi.list(filter.value)
    stats.value = await anomalyApi.stats()
  } catch (e) {
    // ⚠️ **不要用 mock 数据兜底**（2026-11-04 修）——
    //    这里原来在接口失败时填 6 条**看起来完全像真的**的异常订单，
    //    外加一组写死的 KPI（total: 1247 / anomaly: 49 / rate: 3.93）。
    //    → 接口 500 时页面看着「有数据」，实则**全是假的**（规则 12 / 19 / 56）。
    //    → 用户可能照着假数据做判断。失败就**如实置空 + 报错**。
    console.error('异常订单加载失败', e)
    list.value = []
    stats.value = { total: 0, normal: 0, anomaly: 0, rate: 0 }
    ElMessage.error('异常订单加载失败')
  } finally { loading.value = false }
}

async function confirm(row: any, status: string) {
  await ElMessageBox.confirm(`确认将订单 ${row.orderNo} 标记为${status === 'CONFIRMED' ? '异常' : '正常'}？`, '提示')
  // ⚠️ 原来只改本地 `row.status` 就弹「已处理」→ **后端没收到任何请求**
  //    → 审核结果没落库 → 刷新页面又变回 PENDING（规则 51 / 72）。
  //    → 后端有 `POST /api/v1/anomaly/{id}/confirm`（anomalyApi.confirm）→ 真调。
  try {
    await anomalyApi.confirm(row.id, status)
    ElMessage.success('已处理')
    await load()          // 📌 状态以服务端为准
  } catch (e) {
    console.error('异常订单处理失败', e)
    ElMessage.error('处理失败')
  }
}

async function runAll() {
  // ⚠️ 原来直接弹「已对所有 30 单未审核订单重新检测」——**30 这个数字也是写死的**
  //    （实际待审核数可能不是 30）→ 双重骗人（规则 56）。
  //    → 后端有 `POST /api/v1/anomaly/detect/{orderId}`（按订单 ID 重新检测）
  //    → 现在按**真实列表**里 PENDING 的订单逐个重跑，并如实报告实际条数。
  const pending = list.value.filter((r: any) => r.status === 'PENDING')
  if (!pending.length) {
    ElMessage.info('没有待审核的订单')
    return
  }
  running.value = true
  let ok = 0
  const failed: string[] = []
  for (const r of pending) {
    try {
      await anomalyApi.detect(r.orderNo)
      ok++
    } catch (e) {
      console.error('重新检测失败', r.orderNo, e)
      failed.push(r.orderNo)
    }
  }
  running.value = false
  if (failed.length) {
    ElMessage.warning(`重新检测完成：成功 ${ok} 单，失败 ${failed.length} 单（${failed.join('、')}）`)
  } else {
    ElMessage.success(`已重新检测 ${ok} 单未审核订单`)
  }
  await load()
}
</script>

<style scoped>
.anomaly-page { padding: 16px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.stats { margin-bottom: 16px; }
</style>
