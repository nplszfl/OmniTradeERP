<template>
  <div class="tenant-page">
    <el-tabs v-model="tab">
      <!-- 租户列表 -->
      <el-tab-pane label="租户管理" name="list">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>租户列表（{{ list.length }}）</span>
              <el-button type="primary" @click="newTenant">新建租户</el-button>
            </div>
          </template>
          <el-table :data="list" v-loading="loading" stripe>
            <el-table-column prop="id"       label="租户ID"    width="220" show-overflow-tooltip />
            <el-table-column prop="name"     label="租户名称" />
            <el-table-column prop="domain"   label="域名"      width="160" show-overflow-tooltip />
            <el-table-column prop="plan"     label="套餐"      width="140">
              <template #default="{ row }">
                <el-tag size="small" :type="row.plan === 'ENTERPRISE' ? 'danger' : (row.plan === 'BUSINESS' ? 'warning' : '')">
                  {{ row.plan }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="region"   label="数据区域"  width="100" />
            <el-table-column prop="status"   label="状态"      width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="expiresAt" label="到期时间"  width="180">
              <template #default="{ row }">
                {{ row.expiresAt ? String(row.expiresAt).replace('T', ' ').slice(0, 19) : '永久' }}
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间"  width="180">
              <template #default="{ row }">
                {{ row.createdAt ? String(row.createdAt).replace('T', ' ').slice(0, 19) : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="goDashboard(row)">看板</el-button>
                <el-button link type="primary" size="small" @click="goBilling(row)">账单</el-button>
                <el-button link type="warning" size="small" @click="goConfig(row)">配置</el-button>
                <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 套餐说明 -->
      <el-tab-pane label="套餐" name="plans">
        <!-- 📌 套餐是**针对某个租户**的（原来看板/账单/配置三个按钮都只是弹个提示，什么都没做） -->
        <el-form inline class="tenant-picker">
          <el-form-item label="目标租户">
            <el-select v-model="planTenantId" placeholder="请选择要变更套餐的租户" style="width: 320px">
              <el-option v-for="t in list" :key="t.id" :label="`${t.name}（${t.id}）`" :value="t.id" />
            </el-select>
          </el-form-item>
        </el-form>
        <!-- 📌 套餐卡片字段**全部来自后端**（GET /tenant/config/plans）：
             name / price / orderLimit / platformLimit / aiQuota
             → 之前是前端写死的，连单量上限都写错过（STARTER 写 1000，后端是 3000） -->
        <el-row :gutter="16">
          <el-col :span="8" v-for="p in plans" :key="p.name">
            <el-card shadow="hover" class="plan-card">
              <h3>{{ p.name }}</h3>
              <div class="plan-price">
                {{ p.price > 0 ? `¥ ${p.price}` : '按需报价' }}<span v-if="p.price > 0"> / 月</span>
              </div>
              <ul class="plan-features">
                <li>✓ {{ p.platformLimit >= 999 ? '不限店铺' : `${p.platformLimit} 个店铺` }}</li>
                <li>✓ {{ p.orderLimit >= 999999 ? '不限单量' : `${p.orderLimit} 单/月` }}</li>
                <li>✓ AI 配额 {{ p.aiQuota >= 999999 ? '不限' : p.aiQuota }}</li>
              </ul>
              <el-button type="primary" plain class="plan-btn" :disabled="!planTenantId" @click="onPlanSelected(p.name)">选择</el-button>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>

    <!-- 📌 看板 / 账单 / 配置 三个按钮原来是**只弹个提示什么都不做**
         → 现在真的去后端取数并展示（取不到就如实报错） -->
    <el-dialog v-model="detailVisible" :title="detailTitle" width="720px">
      <el-table v-if="detailRows.length" :data="detailRows" stripe size="small">
        <el-table-column prop="label" label="字段" width="200" />
        <el-table-column prop="value" label="值" show-overflow-tooltip />
      </el-table>
      <pre v-else class="detail-json">{{ detailJson }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { tenantApi } from '@/api/tenant'
import { unwrap } from '@/utils/response'

const tab = ref('list')
const list = ref<any[]>([])
const loading = ref(false)

// 📌 套餐**不再写死**：价格 / 单量上限 / 店铺数 / AI 配额都从后端取。
//    之前这里写死了价格和「1000 单/月」等限额，而 STARTER 后端实际是 3000 单
//    → **界面上的数字和真实合同不一致**（规则 56）。
const plans = ref<any[]>([])
/** 变更套餐的目标租户（套餐是挂在某个租户上的） */
const planTenantId = ref('')

/** 看板 / 账单 / 配置 的展示态 */
const detailVisible = ref(false)
const detailTitle = ref('')
const detailRows = ref<{ label: string; value: string }[]>([])
const detailJson = ref('')

onMounted(() => {
  load()
  loadPlans()
})

/** 套餐列表也从后端取（价格/限额不能写死） */
async function loadPlans() {
  try {
    plans.value = await tenantApi.plans()
  } catch (e) {
    console.error('加载套餐列表失败', e)
    plans.value = []
    ElMessage.error('加载套餐列表失败')
  }
}

async function load() {
  loading.value = true
  try {
    // 后端返回裸数组（erp-tenant-service 的列表接口不包 Result）；
    // 用 unwrap 兼容「裸数组」与「Result 包装」两种约定，后端将来统一包装也不会炸。
    const rows = await tenantApi.list()
    const payload = unwrap<any[]>(rows)
    list.value = Array.isArray(payload) ? payload : []
  } catch (e) {
    // ⚠️ 不要再用 mock 数据兜底 —— 接口真的调不通时，页面看着“有数据”实则是假数据。
    //    这里直接暴露错误，让用户知道这个页面是没数据的。
    console.error('租户列表加载失败', e)
    list.value = []
    ElMessage.error('租户列表加载失败')
  } finally { loading.value = false }
}

function newTenant() { ElMessage.info('新建租户 dialog（demo）') }
// ⚠️ 这四个动作原来都只弹一句「已xxx / 打开xxx」——**什么都没做**
//    → 用户以为打开看板了、套餐选好了，其实**数据一条没取、套餐一点没改**
//    （规则 51 / 72）。
//    → 现在真调后端；取不到就如实报错，成功提示只出现在**真的成功之后**。
async function goDashboard(row: any) {
  await showTenantData('看板', row, () => tenantApi.dashboard(row.id))
}

async function goBilling(row: any) {
  await showTenantData('账单', row, () => tenantApi.billing(row.id))
}

async function goConfig(row: any) {
  await showTenantData('套餐配置', row, () => tenantApi.config(row.id))
}

/** 取租户数据并展示（取不到就如实报错，不弹「成功」） */
async function showTenantData(kind: string, row: any, fetcher: () => Promise<any>) {
  detailTitle.value = `${row.name} · ${kind}`
  detailRows.value = []
  detailJson.value = ''
  detailVisible.value = true
  try {
    const data = await fetcher()
    if (Array.isArray(data)) {
      detailRows.value = data.length
        ? Object.keys(data[0]).map(k => ({ label: k, value: String(data[0][k] ?? '') }))
        : []
      if (!data.length) detailJson.value = '（该租户暂无数据）'
    } else if (data && typeof data === 'object') {
      detailRows.value = Object.keys(data).map(k => ({ label: k, value: String(data[k] ?? '') }))
    } else {
      detailJson.value = String(data ?? '（无数据）')
    }
  } catch (e) {
    console.error(`加载租户${kind}失败`, e)
    detailJson.value = '加载失败（详见控制台）'
    ElMessage.error(`加载${kind}失败`)
  }
}

async function onPlanSelected(code: string) {
  if (!planTenantId.value) {
    ElMessage.warning('请先选择目标租户')
    return
  }
  try {
    await tenantApi.changePlan(code, planTenantId.value)
    ElMessage.success(`已将租户 ${planTenantId.value} 的套餐变更为 ${code}`)
    await load()          // 📌 重新拉列表：套餐以服务端为准
  } catch (e) {
    console.error('变更套餐失败', e)
    ElMessage.error('变更套餐失败')
  }
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除租户 ${row.name}？`, '提示', { type: 'warning' })
  // ⚠️ 原来这里**只改本地数组**然后弹「已删除」→ **后端数据一条没动**
  //    → 刷新页面租户又回来了（规则 51 / 72：界面在骗人）。
  //    → 现在真正调后端（软删：status=DELETED），失败就**不假装成功**。
  try {
    await tenantApi.remove(row.id)
    list.value = list.value.filter(r => r.id !== row.id)
    ElMessage.success('已删除')
  } catch (e) {
    console.error('删除租户失败', e)
    ElMessage.error('删除租户失败')
  }
}
</script>

<style scoped>
.tenant-page { padding: 16px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.plan-card { text-align: center; padding: 16px; }
.plan-card h3 { margin-top: 0; }
.plan-price { font-size: 28px; color: #f56c6c; font-weight: 600; margin: 12px 0; }
.plan-price span { font-size: 14px; color: #999; font-weight: normal; }
.plan-features { text-align: left; list-style: none; padding: 0; color: #666; }
.plan-features li { padding: 4px 0; }
.plan-btn { width: 100%; margin-top: 12px; }
</style>
