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
        <el-row :gutter="16">
          <el-col :span="8" v-for="p in plans" :key="p.code">
            <el-card shadow="hover" class="plan-card">
              <h3>{{ p.name }}</h3>
              <div class="plan-price">¥ {{ p.price }}<span> / 月</span></div>
              <ul class="plan-features">
                <li v-for="f in p.features" :key="f">✓ {{ f }}</li>
              </ul>
              <el-button type="primary" plain class="plan-btn" @click="onPlanSelected(p.code)">选择</el-button>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>
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

const plans = [
  {
    code: 'STARTER', name: '入门版', price: 2999,
    features: ['1 个店铺', '基础订单/商品管理', '社区支持', '1000 单/月'],
  },
  {
    code: 'PROFESSIONAL', name: '专业版', price: 4999,
    features: ['3 个店铺', '完整 ERP + 智能定价', '邮件支持', '15000 单/月', 'AI 智能客服'],
  },
  {
    code: 'BUSINESS', name: '商务版', price: 12999,
    features: ['5 个店铺', '全模块 + 定制开发', '7×24 专属客服', '50000 单/月', '私有部署支持'],
  },
  {
    code: 'ENTERPRISE', name: '企业版', price: 0,
    features: ['不限店铺', '全模块 + 定制开发', '7×24 专属客服', '不限单量', '私有部署支持'],
  },
]

onMounted(load)

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
function goDashboard(row: any)   { ElMessage.success(`打开 ${row.name} 看板`) }
function goBilling(row: any)     { ElMessage.success(`打开 ${row.name} 账单`) }
function goConfig(row: any)      { ElMessage.success(`打开 ${row.name} 配置`) }
function onPlanSelected(code: string) { ElMessage.success(`已选择套餐：${code}`) }

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除租户 ${row.name}？`, '提示', { type: 'warning' })
  list.value = list.value.filter(r => r.id !== row.id)
  ElMessage.success('已删除')
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
