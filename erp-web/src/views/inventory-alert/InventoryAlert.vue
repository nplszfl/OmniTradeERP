<template>
  <div class="inventory-alert-page">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon" style="background: #f56c6c">
            <el-icon :size="24"><Warning /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.pendingAlerts }}</div>
            <div class="stat-label">待处理预警</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon" style="background: #e6a23c">
            <el-icon :size="24"><WarningFilled /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.lowStockCount }}</div>
            <div class="stat-label">库存不足</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon" style="background: #909399">
            <el-icon :size="24"><CircleClose /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.outOfStockCount }}</div>
            <div class="stat-label">缺货</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon" style="background: #67c23a">
            <el-icon :size="24"><CircleCheck /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.handledAlerts }}</div>
            <div class="stat-label">已处理</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Tab切换 -->
    <el-tabs v-model="activeTab">
      <!-- 预警规则 -->
      <el-tab-pane label="预警规则" name="rules">
        <el-card class="search-card" shadow="never">
          <el-form :inline="true" :model="ruleSearchForm">
            <el-form-item label="仓库">
              <el-input v-model="ruleSearchForm.warehouseId" placeholder="仓库ID" clearable style="width: 120px" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="ruleSearchForm.enabled" placeholder="全部" clearable style="width: 100px">
                <el-option label="启用" :value="true" />
                <el-option label="禁用" :value="false" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="loadRules">搜索</el-button>
              <el-button type="success" :icon="Plus" @click="handleAddRule">新增规则</el-button>
              <el-button type="warning" :icon="Refresh" @click="handleScanAlert">扫描库存预警</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="table-card" shadow="never">
          <el-table v-loading="ruleLoading" :data="ruleList" stripe>
            <el-table-column prop="sku" label="SKU" width="140" />
            <el-table-column prop="productName" label="产品名称" min-width="200" show-overflow-tooltip />
            <el-table-column prop="warehouseName" label="仓库" width="120" />
            <el-table-column prop="safeStock" label="安全库存" width="90" />
            <el-table-column prop="alertStock" label="预警库存" width="90" />
            <el-table-column prop="minStock" label="最低库存" width="90" />
            <el-table-column prop="alertType" label="预警类型" width="100">
              <template #default="{ row }">
                <el-tag :type="getAlertTypeType(row.alertType)" size="small">{{ getAlertTypeName(row.alertType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="notifyType" label="通知方式" width="100">
              <template #default="{ row }">
                {{ getNotifyTypeName(row.notifyType) }}
              </template>
            </el-table-column>
            <el-table-column prop="enabled" label="状态" width="80">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" @change="handleToggleRule(row)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleEditRule(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteRule(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="rulePagination.current"
            v-model:page-size="rulePagination.size"
            :total="rulePagination.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadRules"
            @current-change="loadRules"
            style="margin-top: 20px; justify-content: flex-end"
          />
        </el-card>
      </el-tab-pane>

      <!-- 预警记录 -->
      <el-tab-pane label="预警记录" name="alerts">
        <el-card class="search-card" shadow="never">
          <el-form :inline="true" :model="alertSearchForm">
            <el-form-item label="仓库">
              <el-input v-model="alertSearchForm.warehouseId" placeholder="仓库ID" clearable style="width: 120px" />
            </el-form-item>
            <el-form-item label="预警类型">
              <el-select v-model="alertSearchForm.alertType" placeholder="全部" clearable style="width: 120px">
                <el-option label="库存不足" value="LOW" />
                <el-option label="缺货" value="OUT" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="alertSearchForm.status" placeholder="全部" clearable style="width: 120px">
                <el-option label="待处理" value="PENDING" />
                <el-option label="已处理" value="HANDLED" />
                <el-option label="已忽略" value="IGNORED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadAlerts">搜索</el-button>
              <el-button type="success" :icon="Check" @click="handleBatchHandle">批量处理</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="table-card" shadow="never">
          <el-table v-loading="alertLoading" :data="alertList" stripe>
            <el-table-column type="selection" width="55" />
            <el-table-column prop="sku" label="SKU" width="140" />
            <el-table-column prop="productName" label="产品名称" min-width="200" show-overflow-tooltip />
            <el-table-column prop="warehouseName" label="仓库" width="120" />
            <el-table-column prop="currentStock" label="当前库存" width="90" />
            <el-table-column prop="alertStock" label="预警阈值" width="90" />
            <el-table-column prop="alertType" label="预警类型" width="100">
              <template #default="{ row }">
                <el-tag :type="getAlertTypeType(row.alertType)" size="small">{{ getAlertTypeName(row.alertType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusName(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="预警时间" width="160">
              <template #default="{ row }">
                {{ formatTime(row.createTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button link type="success" size="small" v-if="row.status === 'PENDING'" @click="handleAlert(row)">处理</el-button>
                <el-button link type="warning" size="small" v-if="row.status === 'PENDING'" @click="handleIgnoreAlert(row)">忽略</el-button>
                <el-button link type="primary" size="small" v-if="row.status === 'PENDING'" @click="handleCreateReplenishment(row)">补货</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="alertPagination.current"
            v-model:page-size="alertPagination.size"
            :total="alertPagination.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadAlerts"
            @current-change="loadAlerts"
            style="margin-top: 20px; justify-content: flex-end"
          />
        </el-card>
      </el-tab-pane>

      <!-- 补货建议 -->
      <el-tab-pane label="补货建议" name="replenishments">
        <el-card class="search-card" shadow="never">
          <el-form :inline="true" :model="replenishSearchForm">
            <el-form-item label="仓库">
              <el-input v-model="replenishSearchForm.warehouseId" placeholder="仓库ID" clearable style="width: 120px" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="replenishSearchForm.status" placeholder="全部" clearable style="width: 120px">
                <el-option label="待确认" value="PENDING" />
                <el-option label="已确认" value="CONFIRMED" />
                <el-option label="已取消" value="CANCELLED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadReplenishments">搜索</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="table-card" shadow="never">
          <el-table v-loading="replenishLoading" :data="replenishmentList" stripe>
            <el-table-column prop="sku" label="SKU" width="140" />
            <el-table-column prop="productName" label="产品名称" min-width="200" show-overflow-tooltip />
            <el-table-column prop="warehouseName" label="仓库" width="120" />
            <el-table-column prop="currentStock" label="当前库存" width="100" />
            <el-table-column prop="suggestedQuantity" label="建议数量" width="100" />
            <el-table-column prop="estimatedPrice" label="预估金额" width="100">
              <template #default="{ row }">
                ${{ row.estimatedPrice?.toFixed(2) }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="getReplenishStatusType(row.status)" size="small">{{ getReplenishStatusName(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="160">
              <template #default="{ row }">
                {{ formatTime(row.createTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="success" size="small" v-if="row.status === 'PENDING'" @click="handleConfirmReplenishment(row)">确认</el-button>
                <el-button link type="danger" size="small" v-if="row.status === 'PENDING'" @click="handleCancelReplenishment(row)">取消</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="replenishPagination.current"
            v-model:page-size="replenishPagination.size"
            :total="replenishPagination.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadReplenishments"
            @current-change="loadReplenishments"
            style="margin-top: 20px; justify-content: flex-end"
          />
        </el-card>
      </el-tab-pane>

      <!-- 预警设置 -->
      <el-tab-pane label="预警设置" name="settings">
        <el-card shadow="never">
          <template #header>
            <span>系统预警设置</span>
          </template>
          <el-form label-width="150px">
            <el-form-item label="启用库存预警">
              <el-switch v-model="systemSettings.enableAlert" />
            </el-form-item>
            <el-form-item label="默认预警提前天数">
              <el-input-number v-model="systemSettings.defaultAdvanceDays" :min="1" :max="30" />
            </el-form-item>
            <el-form-item label="默认通知方式">
              <el-checkbox-group v-model="systemSettings.defaultNotifyTypes">
                <el-checkbox label="EMAIL">邮件</el-checkbox>
                <el-checkbox label="SMS">短信</el-checkbox>
                <el-checkbox label="SYSTEM">系统消息</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <el-form-item label="默认通知人">
              <el-input v-model="systemSettings.defaultNotifyTo" placeholder="多个用逗号分隔" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSaveSettings">保存设置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增/编辑规则弹窗 -->
    <el-dialog v-model="ruleFormVisible" :title="isEditRule ? '编辑规则' : '新增规则'" width="600px" append-to-body>
      <el-form :model="ruleForm" :rules="ruleFormRules" ref="ruleFormRef" label-width="100px">
        <el-form-item label="产品SKU" prop="sku">
          <el-input v-model="ruleForm.sku" placeholder="请输入产品SKU" />
        </el-form-item>
        <el-form-item label="产品名称" prop="productName">
          <el-input v-model="ruleForm.productName" placeholder="请输入产品名称" />
        </el-form-item>
        <el-form-item label="仓库" prop="warehouseId">
          <el-input v-model="ruleForm.warehouseId" placeholder="请输入仓库ID" />
        </el-form-item>
        <el-form-item label="安全库存" prop="safeStock">
          <el-input-number v-model="ruleForm.safeStock" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="预警库存" prop="alertStock">
          <el-input-number v-model="ruleForm.alertStock" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="最低库存" prop="minStock">
          <el-input-number v-model="ruleForm.minStock" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="预警类型">
          <el-select v-model="ruleForm.alertType" style="width: 100%">
            <el-option label="库存不足预警" value="LOW" />
            <el-option label="缺货预警" value="OUT" />
          </el-select>
        </el-form-item>
        <el-form-item label="通知方式">
          <el-checkbox-group v-model="ruleForm.notifyTypes">
            <el-checkbox label="EMAIL">邮件</el-checkbox>
            <el-checkbox label="SMS">短信</el-checkbox>
            <el-checkbox label="SYSTEM">系统消息</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="通知人">
          <el-input v-model="ruleForm.notifyTo" placeholder="多个用逗号分隔" />
        </el-form-item>
        <el-form-item label="启用规则">
          <el-switch v-model="ruleForm.enabled" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="ruleForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="ruleFormVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitRule">确定</el-button>
      </template>
    </el-dialog>

    <!-- 处理预警弹窗 -->
    <el-dialog v-model="handleAlertVisible" title="处理预警" width="500px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="SKU">
          <el-input :value="currentAlert?.sku" disabled />
        </el-form-item>
        <el-form-item label="处理方式">
          <el-radio-group v-model="handleAlertForm.action">
            <el-radio label="handle">处理</el-radio>
            <el-radio label="ignore">忽略</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理备注">
          <el-input v-model="handleAlertForm.remark" type="textarea" :rows="3" placeholder="请输入处理备注" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="handleAlertVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmHandleAlert">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Check, Warning, WarningFilled, CircleClose, CircleCheck, Refresh } from '@element-plus/icons-vue'
import { inventoryAlertApi, type InventoryAlertRule, type InventoryAlert, type ReplenishmentSuggestion, type AlertStatistics } from '@/api/inventory-alert'
import dayjs from 'dayjs'
import { unwrap, unwrapPage } from '@/utils/response'

const activeTab = ref('rules')
const ruleLoading = ref(false)
const alertLoading = ref(false)
const replenishLoading = ref(false)

// 统计数据
const stats = reactive<AlertStatistics>({
  totalAlerts: 0,
  pendingAlerts: 0,
  handledAlerts: 0,
  ignoredAlerts: 0,
  todayAlerts: 0,
  lowStockCount: 0,
  outOfStockCount: 0
})

// 预警规则
const ruleList = ref<InventoryAlertRule[]>([])
const ruleSearchForm = reactive({ warehouseId: '', enabled: undefined as boolean | undefined })
const rulePagination = reactive({ current: 1, size: 20, total: 0 })
const ruleFormVisible = ref(false)
const isEditRule = ref(false)
const ruleForm = reactive({
  id: '',sku: '', productName: '', warehouseId: '', warehouseName: '',
  safeStock: 10, alertStock: 5, minStock: 0, alertType: 'LOW',
  notifyType: 'SYSTEM', notifyTo: '', enabled: true, remark: '',
  notifyTypes: ['SYSTEM'] as string[]
})
const ruleFormRules = {
  sku: [{ required: true, message: '请输入SKU', trigger: 'blur' }],
  warehouseId: [{ required: true, message: '请输入仓库ID', trigger: 'blur' }]
}

// 预警记录
const alertList = ref<InventoryAlert[]>([])
const alertSearchForm = reactive({ warehouseId: '', alertType: '', status: '' })
const alertPagination = reactive({ current: 1, size: 20, total: 0 })
const handleAlertVisible = ref(false)
const currentAlert = ref<InventoryAlert | null>(null)
const handleAlertForm = reactive({ action: 'handle', remark: '' })

// 补货建议
const replenishmentList = ref<ReplenishmentSuggestion[]>([])
const replenishSearchForm = reactive({ warehouseId: '', status: '' })
const replenishPagination = reactive({ current: 1, size: 20, total: 0 })

// 系统设置
const systemSettings = reactive({
  enableAlert: true,
  defaultAdvanceDays: 3,
  defaultNotifyTypes: ['SYSTEM'] as string[],
  defaultNotifyTo: ''
})

// 加载统计数据
const loadStats = async () => {
  try {
    const res = await inventoryAlertApi.getRealTimeStatistics()
    Object.assign(stats, res)
  } catch (error) {
    console.error('加载统计失败', error)
  }
}

// 手动扫描库存并生成预警
// 📌 预警引擎现在有定时任务（默认 5 分钟一次），这个按钮是给「想立刻看到结果」用的。
const handleScanAlert = async () => {
  try {
    // 后端返回 Result<Integer>，拦截器已经剥掉 axios 外壳，
    // 这里还要再剥一层 Result —— unwrap 会自动判断有没有 Result 外壳
    const created = unwrap<number>(await inventoryAlertApi.scanInventory())
    ElMessage.success(`扫描完成，新增 ${created ?? 0} 条预警`)
    loadStats()
    loadAlerts()
    loadReplenishments()
  } catch (error) {
    console.error('扫描库存预警失败', error)
  }
}

// 加载规则
const loadRules = async () => {
  ruleLoading.value = true
  try {
    const res = await inventoryAlertApi.getRules({
      page: rulePagination.current,
      size: rulePagination.size,
      warehouseId: ruleSearchForm.warehouseId || undefined,
      enabled: ruleSearchForm.enabled
    })
    const pageData = unwrapPage(res)
    ruleList.value = pageData.records || []
    rulePagination.total = pageData.total || 0
  } catch (error) {
    console.error('加载规则失败', error)
  } finally {
    ruleLoading.value = false
  }
}

// 加载预警记录
const loadAlerts = async () => {
  alertLoading.value = true
  try {
    const res = await inventoryAlertApi.getAlerts({
      page: alertPagination.current,
      size: alertPagination.size,
      warehouseId: alertSearchForm.warehouseId || undefined,
      alertType: alertSearchForm.alertType || undefined,
      status: alertSearchForm.status || undefined
    })
    const pageData = unwrapPage(res)
    alertList.value = pageData.records || []
    alertPagination.total = pageData.total || 0
  } catch (error) {
    console.error('加载预警记录失败', error)
  } finally {
    alertLoading.value = false
  }
}

// 加载补货建议
const loadReplenishments = async () => {
  replenishLoading.value = true
  try {
    const res = await inventoryAlertApi.getReplenishments({
      page: replenishPagination.current,
      size: replenishPagination.size,
      warehouseId: replenishSearchForm.warehouseId || undefined,
      status: replenishSearchForm.status || undefined
    })
    const pageData = unwrapPage(res)
    replenishmentList.value = pageData.records || []
    replenishPagination.total = pageData.total || 0
  } catch (error) {
    console.error('加载补货建议失败', error)
  } finally {
    replenishLoading.value = false
  }
}

// 新增规则
const handleAddRule = () => {
  isEditRule.value = false
  Object.assign(ruleForm, {
    id: '',sku: '', productName: '', warehouseId: '', warehouseName: '',
    safeStock: 10, alertStock: 5, minStock: 0, alertType: 'LOW',
    notifyType: 'SYSTEM', notifyTo: '', enabled: true, remark: '',
    notifyTypes: ['SYSTEM']
  })
  ruleFormVisible.value = true
}

// 编辑规则
const handleEditRule = (rule: InventoryAlertRule) => {
  isEditRule.value = true
  Object.assign(ruleForm, rule)
  ruleForm.notifyTypes = rule.notifyType?.split(',') || ['SYSTEM']
  ruleFormVisible.value = true
}

// 提交规则
const handleSubmitRule = async () => {
  ruleForm.notifyType = ruleForm.notifyTypes.join(',')
  try {
    if (isEditRule.value) {
      await inventoryAlertApi.updateRule(ruleForm.id, ruleForm as any)
      ElMessage.success('更新成功')
    } else {
      await inventoryAlertApi.createRule(ruleForm as any)
      ElMessage.success('创建成功')
    }
    ruleFormVisible.value = false
    loadRules()
  } catch (error) {
    console.error('操作失败', error)
  }
}

// 删除规则
const handleDeleteRule = (rule: InventoryAlertRule) => {
  ElMessageBox.confirm(`确定删除规则 ${rule.sku} 吗？`, '提示', { type: 'warning' }).then(async () => {
    try {
      await inventoryAlertApi.deleteRule(rule.id)
      ElMessage.success('删除成功')
      loadRules()
    } catch (error) {
      console.error('删除失败', error)
    }
  })
}

// 切换规则状态
const handleToggleRule = async (rule: InventoryAlertRule) => {
  try {
    await inventoryAlertApi.updateRule(rule.id, rule)
  } catch (error) {
    console.error('更新失败', error)
  }
}

// 处理预警
const handleAlert = (alert: InventoryAlert) => {
  currentAlert.value = alert
  handleAlertForm.action = 'handle'
  handleAlertForm.remark = ''
  handleAlertVisible.value = true
}

// 确认处理预警
const confirmHandleAlert = async () => {
  if (!currentAlert.value) return
  try {
    const handlerId = '1', handlerName = 'admin'
    if (handleAlertForm.action === 'handle') {
      await inventoryAlertApi.handleAlert(currentAlert.value.id, handlerId, handlerName, handleAlertForm.remark)
    } else {
      await inventoryAlertApi.ignoreAlert(currentAlert.value.id, handlerId, handlerName, handleAlertForm.remark)
    }
    ElMessage.success('处理成功')
    handleAlertVisible.value = false
    loadAlerts()
    loadStats()
  } catch (error) {
    console.error('处理失败', error)
  }
}

// 忽略预警
const handleIgnoreAlert = (alert: InventoryAlert) => {
  currentAlert.value = alert
  handleAlertForm.action = 'ignore'
  handleAlertForm.remark = ''
  handleAlertVisible.value = true
}

// 批量处理
const handleBatchHandle = () => {
  ElMessage.info('请先选择要处理的预警记录')
}

// 创建补货
const handleCreateReplenishment = (alert: InventoryAlert) => {
  ElMessage.info('补货功能需要跳转到补货页面')
}

// 确认补货
const handleConfirmReplenishment = async (item: ReplenishmentSuggestion) => {
  try {
    await inventoryAlertApi.confirmReplenishment(item.id, '1', 'admin')
    ElMessage.success('确认成功')
    loadReplenishments()
    loadStats()
  } catch (error) {
    console.error('确认失败', error)
  }
}

// 取消补货
const handleCancelReplenishment = async (item: ReplenishmentSuggestion) => {
  try {
    await inventoryAlertApi.cancelReplenishment(item.id)
    ElMessage.success('取消成功')
    loadReplenishments()
  } catch (error) {
    console.error('取消失败', error)
  }
}

// 保存设置
const handleSaveSettings = () => {
  ElMessage.success('设置已保存')
}

// 工具函数
const getAlertTypeName = (type: string) => {
  const map: Record<string, string> = { LOW: '库存不足', OUT: '缺货', SAFE: '安全' }
  return map[type] || type
}

const getAlertTypeType = (type: string) => {
  const map: Record<string, string> = { LOW: 'warning', OUT: 'danger', SAFE: 'success' }
  return map[type] || ''
}

const getStatusName = (status: string) => {
  const map: Record<string, string> = { PENDING: '待处理', HANDLED: '已处理', IGNORED: '已忽略' }
  return map[status] || status
}

const getStatusType = (status: string) => {
  const map: Record<string, string> = { PENDING: 'warning', HANDLED: 'success', IGNORED: 'info' }
  return map[status] || ''
}

const getNotifyTypeName = (type: string) => {
  const map: Record<string, string> = { EMAIL: '邮件', SMS: '短信', SYSTEM: '系统', ALL: '全部' }
  return map[type] || type
}

const getReplenishStatusName = (status: string) => {
  const map: Record<string, string> = { PENDING: '待确认', CONFIRMED: '已确认', CANCELLED: '已取消' }
  return map[status] || status
}

const getReplenishStatusType = (status: string) => {
  const map: Record<string, string> = { PENDING: 'warning', CONFIRMED: 'success', CANCELLED: 'info' }
  return map[status] || ''
}

const formatTime = (time: string) => time ? dayjs(time).format('YYYY-MM-DD HH:mm') : '-'

onMounted(() => {
  loadStats()
  loadRules()
  loadAlerts()
  loadReplenishments()
})
</script>

<style scoped>
.inventory-alert-page {
  padding: 20px;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 20px;
}

.stat-icon {
  width: 50px;
  height: 50px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  margin-right: 15px;
}

.stat-content {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
}

.stat-label {
  color: #999;
  font-size: 14px;
}

.search-card {
  margin-bottom: 15px;
}

.table-card {
  margin-bottom: 20px;
}
</style>