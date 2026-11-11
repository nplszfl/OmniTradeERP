<template>
  <div class="logistics-page">
    <!-- 运费计算器 -->
    <el-card class="calculator-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>运费计算器</span>
          <el-button type="primary" :icon="Refresh" @click="resetCalculator">重置</el-button>
        </div>
      </template>
      
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form label-width="100px">
            <el-form-item label="发货国家" required>
              <el-select v-model="calculator.fromCountry" placeholder="请选择" style="width: 100%">
                <el-option label="中国" value="CN" />
                <el-option label="美国" value="US" />
                <el-option label="英国" value="UK" />
                <el-option label="德国" value="DE" />
                <el-option label="日本" value="JP" />
              </el-select>
            </el-form-item>
            <el-form-item label="邮编">
              <el-input v-model="calculator.fromPostalCode" placeholder="发货地邮编" />
            </el-form-item>
          </el-form>
        </el-col>
        
        <el-col :span="8">
          <el-form label-width="100px">
            <el-form-item label="收货国家" required>
              <el-select v-model="calculator.toCountry" placeholder="请选择" style="width: 100%">
                <el-option label="美国" value="US" />
                <el-option label="英国" value="UK" />
                <el-option label="德国" value="DE" />
                <el-option label="法国" value="FR" />
                <el-option label="加拿大" value="CA" />
                <el-option label="澳大利亚" value="AU" />
                <el-option label="日本" value="JP" />
                <el-option label="中国" value="CN" />
              </el-select>
            </el-form-item>
            <el-form-item label="收货邮编" required>
              <el-input v-model="calculator.toPostalCode" placeholder="收货地邮编" />
            </el-form-item>
          </el-form>
        </el-col>
        
        <el-col :span="8">
          <el-form label-width="100px">
            <el-form-item label="重量(kg)" required>
              <el-input-number v-model="calculator.weight" :min="0.1" :precision="2" :step="0.1" style="width: 100%" />
            </el-form-item>
            <el-form-item label="长(cm)">
              <el-input-number v-model="calculator.length" :min="0" :precision="1" style="width: 100%" />
            </el-form-item>
            <el-form-item label="宽(cm)">
              <el-input-number v-model="calculator.width" :min="0" :precision="1" style="width: 100%" />
            </el-form-item>
            <el-form-item label="高(cm)">
              <el-input-number v-model="calculator.height" :min="0" :precision="1" style="width: 100%" />
            </el-form-item>
          </el-form>
        </el-col>
      </el-row>
      
      <div style="text-align: center; margin-top: 20px">
        <el-button type="primary" size="large" :icon="DataAnalysis" @click="calculateRates" :loading="calculating">
          计算运费
        </el-button>
      </div>
    </el-card>

    <!-- 计算结果 -->
    <el-card v-if="rateResults.length > 0" class="result-card" shadow="never">
      <template #header>
        <span>运费计算结果</span>
      </template>
      
      <el-radio-group v-model="sortBy" style="margin-bottom: 15px">
        <el-radio label="price">按价格</el-radio>
        <el-radio label="days">按时效</el-radio>
      </el-radio-group>

      <!-- 📌 展示**当前选中的渠道**（原来点「选择」只弹了个 toast，
           选择结果**当场就被丢掉** → 用户以为选好了，其实什么都没发生） -->
      <el-alert v-if="selectedRate" type="success" :closable="false" show-icon
        style="margin-bottom: 12px"
        :title="`已选择渠道：${selectedRate.channelName}（${selectedRate.carrier}）`"
        :description="`运费 $${selectedRate.totalPrice?.toFixed(2)} ${selectedRate.currency} · 时效 ${selectedRate.transitTime}`" />

      <el-table :data="sortedResults" stripe>
        <el-table-column label="推荐" width="70">
          <template #default="{ row }">
            <el-tag v-if="row.isRecommended" type="success" size="small">推荐</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag v-if="selectedRate && selectedRate.channelCode === row.channelCode"
                    type="primary" size="small">已选</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="channelName" label="物流渠道" min-width="150" />
        <el-table-column prop="carrier" label="承运商" width="120" />
        <el-table-column label="运费" width="120">
          <template #default="{ row }">
            <span class="price">${{ row.totalPrice?.toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时效" width="150">
          <template #default="{ row }">
            <span class="days">{{ row.transitTime }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="selectChannel(row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 物流渠道 -->
    <el-card class="channels-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>物流渠道</span>
          <el-button type="primary" size="small" @click="showChannelDialog = true">添加渠道</el-button>
        </div>
      </template>
      
      <el-row :gutter="20">
        <el-col :span="8" v-for="channel in channels" :key="channel.code">
          <el-card class="channel-card" shadow="hover">
            <div class="channel-header">
              <span class="channel-name">{{ channel.name }}</span>
              <el-tag size="small">{{ channel.carrier }}</el-tag>
            </div>
            <div class="channel-info">
              <div class="info-item">
                <span class="label">类型：</span>
                <span class="value">{{ getChannelTypeName(channel.type) }}</span>
              </div>
              <div class="info-item">
                <span class="label">覆盖：</span>
                <span class="value">{{ channel.coverage?.slice(0, 3).join(', ') }}</span>
              </div>
              <div class="info-item">
                <span class="label">平均时效：</span>
                <span class="value">{{ channel.avgDeliveryDays }}天</span>
              </div>
              <div class="info-item">
                <span class="label">追踪：</span>
                <el-tag :type="channel.trackingSupport ? 'success' : 'info'" size="small">
                  {{ channel.trackingSupport ? '支持' : '不支持' }}
                </el-tag>
              </div>
            </div>
            <div class="channel-features">
              <el-tag v-for="f in channel.features?.slice(0, 3)" :key="f" size="small" type="info" style="margin-right: 5px">
                {{ f }}
              </el-tag>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <!-- 物流渠道管理 -->
    <el-card class="rates-card" shadow="never">
      <template #header>
        <span>物流费率管理</span>
      </template>
      
      <el-form :inline="true" :model="rateSearchForm" style="margin-bottom: 15px">
        <el-form-item label="渠道">
          <el-input v-model="rateSearchForm.channelCode" placeholder="渠道代码" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="发货国">
          <el-input v-model="rateSearchForm.fromCountry" placeholder="发货国" clearable style="width: 100px" />
        </el-form-item>
        <el-form-item label="目的国">
          <el-input v-model="rateSearchForm.toCountry" placeholder="目的国" clearable style="width: 100px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadRates">搜索</el-button>
          <el-button type="success" :icon="Plus" @click="showRateForm = true">新增费率</el-button>
        </el-form-item>
      </el-form>
      
      <el-table v-loading="ratesLoading" :data="rateList" stripe>
        <el-table-column prop="channelCode" label="渠道代码" width="120" />
        <el-table-column prop="channelName" label="渠道名称" width="150" />
        <el-table-column prop="carrier" label="承运商" width="100" />
        <el-table-column prop="fromCountry" label="发货国" width="80" />
        <el-table-column prop="toCountry" label="目的国" width="80" />
        <el-table-column prop="weightRangeMin" label="最小重量" width="90">
          <template #default="{ row }">
            {{ row.weightRangeMin }}kg
          </template>
        </el-table-column>
        <el-table-column prop="weightRangeMax" label="最大重量" width="90">
          <template #default="{ row }">
            {{ row.weightRangeMax }}kg
          </template>
        </el-table-column>
        <el-table-column prop="basePrice" label="基础价格" width="100">
          <template #default="{ row }">
            ${{ row.basePrice?.toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column prop="pricePerKg" label="单价/kg" width="90">
          <template #default="{ row }">
            ${{ row.pricePerKg?.toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column prop="estimatedDays" label="时效(天)" width="90" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.isActive" @change="toggleRateActive(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="editRate(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="deleteRate(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <el-pagination
        v-model:current-page="ratePagination.current"
        v-model:page-size="ratePagination.size"
        :total="ratePagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadRates"
        @current-change="loadRates"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <!-- 添加渠道弹窗 -->
    <el-dialog v-model="showChannelDialog" title="添加物流渠道" width="600px" append-to-body>
      <el-form :model="channelForm" label-width="100px">
        <el-form-item label="渠道代码" required>
          <el-input v-model="channelForm.code" placeholder="如: DHL_EXPRESS" />
        </el-form-item>
        <el-form-item label="渠道名称" required>
          <el-input v-model="channelForm.name" placeholder="如: DHL国际快递" />
        </el-form-item>
        <el-form-item label="承运商" required>
          <el-input v-model="channelForm.carrier" placeholder="如: DHL" />
        </el-form-item>
        <el-form-item label="渠道类型">
          <el-select v-model="channelForm.type" style="width: 100%">
            <el-option label="国际快递" value="EXPRESS" />
            <el-option label="专线物流" value="SPECIAL" />
            <el-option label="邮政小包" value="POST" />
            <el-option label="海运" value="SEA" />
            <el-option label="空运" value="AIR" />
          </el-select>
        </el-form-item>
        <el-form-item label="覆盖国家">
          <el-input v-model="channelForm.coverageText" placeholder="用逗号分隔，如: US,UK,CA" />
        </el-form-item>
        <el-form-item label="平均时效">
          <el-input-number v-model="channelForm.avgDeliveryDays" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="支持追踪">
          <el-switch v-model="channelForm.trackingSupport" />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="showChannelDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddChannel">确定</el-button>
      </template>
    </el-dialog>

    <!-- 新增费率弹窗 -->
    <el-dialog v-model="showRateForm" title="新增物流费率" width="600px" append-to-body>
      <el-form :model="rateForm" label-width="100px">
        <el-form-item label="渠道" required>
          <el-select v-model="rateForm.channelCode" style="width: 100%">
            <el-option v-for="c in channels" :key="c.code" :label="c.name" :value="c.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="发货国" required>
          <el-input v-model="rateForm.fromCountry" placeholder="如: CN" />
        </el-form-item>
        <el-form-item label="目的国" required>
          <el-input v-model="rateForm.toCountry" placeholder="如: US" />
        </el-form-item>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="最小重量(kg)">
              <el-input-number v-model="rateForm.weightRangeMin" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大重量(kg)">
              <el-input-number v-model="rateForm.weightRangeMax" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="基础价格">
              <el-input-number v-model="rateForm.basePrice" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单价/kg">
              <el-input-number v-model="rateForm.pricePerKg" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="预计时效">
          <el-input-number v-model="rateForm.estimatedDays" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="币种">
          <el-select v-model="rateForm.currency" style="width: 100%">
            <el-option label="美元 (USD)" value="USD" />
            <el-option label="欧元 (EUR)" value="EUR" />
            <el-option label="英镑 (GBP)" value="GBP" />
            <el-option label="人民币 (CNY)" value="CNY" />
          </el-select>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="showRateForm = false">取消</el-button>
        <el-button type="primary" @click="handleAddRate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DataAnalysis, Refresh, Plus } from '@element-plus/icons-vue'
import { logisticsApi, type LogisticsChannel, type LogisticsRate, type ShippingRateQuery, type ShippingRateResult } from '@/api/inventory-alert'

const calculating = ref(false)
const sortBy = ref('price')

// 运费计算器
const calculator = reactive<ShippingRateQuery>({
  fromCountry: 'CN',
  fromPostalCode: '',
  toCountry: 'US',
  toPostalCode: '',
  weight: 1,
  length: 20,
  width: 15,
  height: 10
})

const rateResults = ref<ShippingRateResult[]>([])

// 物流渠道
const channels = ref<LogisticsChannel[]>([])

// 费率管理
const ratesLoading = ref(false)
const rateList = ref<LogisticsRate[]>([])
const rateSearchForm = reactive({ channelCode: '', fromCountry: '', toCountry: '' })
const ratePagination = reactive({ current: 1, size: 20, total: 0 })

// 弹窗
const showChannelDialog = ref(false)
const showRateForm = ref(false)

// 表单
const channelForm = reactive({
  code: '', name: '', carrier: '', type: 'EXPRESS', coverageText: '', avgDeliveryDays: 7, trackingSupport: true
})

const rateForm = reactive({
  channelCode: '', fromCountry: '', toCountry: '',
  weightRangeMin: 0, weightRangeMax: 5, basePrice: 10, pricePerKg: 5, estimatedDays: 7, currency: 'USD'
})

// 排序结果
const sortedResults = computed(() => {
  const results = [...rateResults.value]
  if (sortBy.value === 'price') {
    return results.sort((a, b) => a.totalPrice - b.totalPrice)
  } else {
    return results.sort((a, b) => a.estimatedDays - b.estimatedDays)
  }
})

// 计算运费
const calculateRates = async () => {
  if (!calculator.toCountry || !calculator.toPostalCode) {
    ElMessage.warning('请填写完整的收货信息')
    return
  }
  
  calculating.value = true
  try {
    const res = await logisticsApi.calculateRate(calculator)
    rateResults.value = res
    // 📌 重新计算后旧的选择**不再成立**（运费/时效都变了）→ 清掉
    selectedRate.value = null
  } catch (error) {
    console.error('计算运费失败', error)
    // ⚠️ 之前这里回退到 mock 数据，把后端的 404/500 全部掩盖了。
    //    现在后端已有真实的物流服务（erp-logistics-service），失败就如实报错。
    rateResults.value = []
    ElMessage.error('运费计算失败，请检查物流服务状态')
  } finally {
    calculating.value = false
  }
}

// 重置计算器
const resetCalculator = () => {
  calculator.weight = 1
  calculator.fromPostalCode = ''
  calculator.toPostalCode = ''
  rateResults.value = []
  // 📌 结果清空了，之前选的渠道**不再成立** → 必须一起清掉
  selectedRate.value = null
}

/**
 * 当前选中的物流渠道
 *
 * ⚠️ 原来 `selectChannel` 只弹了个「已选择 X」，
 *    **但选择结果当场就被丢掉** —— 页面上没有任何地方记着它
 *    → 用户以为选好了渠道，**其实什么都没发生**（规则 51 / 72 / 82）。
 *    → 现在真的记住选择（`selectedRate`），
 *      表格里标「已选」，上方摘要显示渠道/运费/时效 → **「已选择」这句话才是真的**。
 *
 * 📌 后端 erp-logistics-service **没有「创建物流单」接口**
 *    （只有 channels / calculate / recommend / rates 的 CRUD），
 *    所以这里做到「选择真实生效且可见」为止；
 *    后续接了发货流程，直接用 `selectedRate` 即可。
 */
const selectedRate = ref<ShippingRateResult | null>(null)

// 选择渠道
const selectChannel = (rate: ShippingRateResult) => {
  selectedRate.value = rate
  ElMessage.success(`已选择 ${rate.channelName}，运费 $${rate.totalPrice.toFixed(2)}`)
}

// 加载渠道
const loadChannels = async () => {
  try {
    channels.value = await logisticsApi.getChannels()
  } catch (error) {
    console.error('加载渠道失败', error)
    channels.value = []
    ElMessage.error('渠道列表加载失败')
  }
}

// 加载费率
const loadRates = async () => {
  ratesLoading.value = true
  try {
    const res = await logisticsApi.getRates({
      page: ratePagination.current,
      size: ratePagination.size,
      channelCode: rateSearchForm.channelCode || undefined,
      fromCountry: rateSearchForm.fromCountry || undefined,
      toCountry: rateSearchForm.toCountry || undefined
    })
    // 后端返回裸分页对象 {current,size,total,records}
    rateList.value = res.records || []
    ratePagination.total = res.total || 0
  } catch (error) {
    console.error('加载费率失败', error)
    rateList.value = []
    ratePagination.total = 0
    ElMessage.error('费率列表加载失败')
  } finally {
    ratesLoading.value = false
  }
}

// 添加渠道
const handleAddChannel = async () => {
  try {
    // 📌 之前这里只是弹个「渠道添加成功」就完事，**一个请求都没发** —— 刷新就没了。
    //    现在真的调后端落库（POST /logistics/channel）。
    await logisticsApi.createChannel({
      code: channelForm.code.trim(),
      name: channelForm.name.trim(),
      carrier: channelForm.carrier.trim(),
      type: channelForm.type,
      coverage: channelForm.coverageText
        .split(',')
        .map(s => s.trim())
        .filter(s => s.length > 0),
      features: [],
      avgDeliveryDays: channelForm.avgDeliveryDays,
      trackingSupport: channelForm.trackingSupport,
      enabled: true
    } as any)
    ElMessage.success('渠道添加成功')
    showChannelDialog.value = false
    Object.assign(channelForm, {
      code: '', name: '', carrier: '', type: 'EXPRESS',
      coverageText: '', avgDeliveryDays: 7, trackingSupport: true
    })
    loadChannels()
  } catch (error) {
    console.error('添加失败', error)
    ElMessage.error('渠道添加失败')
  }
}

// 添加费率
const handleAddRate = async () => {
  try {
    // ⚠️ 之前这里只是弹个「添加成功」就完事，什么都没存 —— 刷新就没了。
    //    现在真的调后端落库。
    await logisticsApi.createRate({ ...rateForm } as any)
    ElMessage.success('费率添加成功')
    showRateForm.value = false
    loadRates()
  } catch (error) {
    console.error('添加失败', error)
    ElMessage.error('费率添加失败')
  }
}

// 编辑费率
const editRate = (rate: LogisticsRate) => {
  Object.assign(rateForm, rate)
  showRateForm.value = true
}

// 删除费率
const deleteRate = (rate: LogisticsRate) => {
  ElMessageBox.confirm('确定删除此费率吗？', '提示', { type: 'warning' }).then(async () => {
    try {
      await logisticsApi.deleteRate(rate.id)
      ElMessage.success('删除成功')
      loadRates()
    } catch (error) {
      console.error('删除失败', error)
    }
  })
}

// 切换费率状态
const toggleRateActive = async (rate: LogisticsRate) => {
  try {
    await logisticsApi.updateRate(rate.id, rate)
  } catch (error) {
    console.error('更新失败', error)
  }
}

// 工具函数
const getChannelTypeName = (type: string) => {
  const map: Record<string, string> = { EXPRESS: '国际快递', SPECIAL: '专线物流', POST: '邮政小包', SEA: '海运', AIR: '空运' }
  return map[type] || type
}

onMounted(() => {
  loadChannels()
  loadRates()
})
</script>

<style scoped>
.logistics-page {
  padding: 20px;
}

.calculator-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.result-card {
  margin-bottom: 20px;
}

.price {
  font-size: 18px;
  font-weight: bold;
  color: #409eff;
}

.days {
  color: #67c23a;
}

.channels-card {
  margin-bottom: 20px;
}

.channel-card {
  margin-bottom: 15px;
}

.channel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.channel-name {
  font-weight: bold;
  font-size: 16px;
}

.channel-info {
  margin-bottom: 10px;
}

.info-item {
  display: flex;
  margin-bottom: 5px;
  font-size: 13px;
}

.info-item .label {
  color: #999;
  width: 70px;
}

.info-item .value {
  flex: 1;
}

.channel-features {
  margin-top: 10px;
}

.rates-card {
  margin-bottom: 20px;
}
</style>