<template>
  <div class="desc-page">
    <el-row :gutter="16">
      <el-col :span="10">
        <el-card shadow="never">
          <template #header><span>商品信息</span></template>
          <el-form :model="form" label-width="100px">
            <el-form-item label="商品 SKU">
              <el-select v-model="form.sku" filterable placeholder="选择或输入 SKU" style="width: 100%">
                <el-option v-for="p in products" :key="p.sku" :label="p.sku + ' - ' + p.name" :value="p.sku" />
              </el-select>
            </el-form-item>
            <el-form-item label="目标语言">
              <el-radio-group v-model="form.language">
                <el-radio-button value="zh">中文</el-radio-button>
                <el-radio-button value="en">英文</el-radio-button>
                <el-radio-button value="ja">日语</el-radio-button>
                <el-radio-button value="es">西班牙语</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="风格语气">
              <el-select v-model="form.tone" style="width: 100%">
                <el-option label="专业严谨" value="professional" />
                <el-option label="轻松幽默" value="casual" />
                <el-option label="高端奢华" value="luxury" />
                <el-option label="平实日常" value="everyday" />
              </el-select>
            </el-form-item>
            <el-form-item label="关键词">
              <el-input v-model="form.keywordsText" placeholder="逗号分隔，例如：主动降噪, 30小时续航, IPX5" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="generating" @click="generate">一键生成</el-button>
              <el-button @click="form.tone = 'professional'; form.keywordsText = ''">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <span>生成结果</span>
            <el-button v-if="result" link type="primary" size="small" style="margin-left: 8px" @click="copy">复制</el-button>
            <el-button v-if="result" link type="success" size="small" :loading="saving" @click="saveToProduct">保存到商品</el-button>
          </template>
          <div v-if="generating" class="placeholder">AI 正在生成高质量商品描述...</div>
          <div v-else-if="result" class="result">
            <!-- 📌 **降级要看得见** —— 后端真实 LLM 挂了会降级成模板文案，
                 mock=true 时必须告诉用户「这是模板，不是真 AI 写的」 -->
            <el-alert
              v-if="result.mock"
              type="warning"
              show-icon
              :closable="false"
              class="mock-alert"
              :title="'这是**模拟生成**的文案，不是真实 LLM 生成'"
              :description="result.mockReason || '后端未配置 LLM，或真实调用失败后降级'"
            />
            <h3>{{ result.title }}</h3>
            <p class="subtitle">{{ result.subtitle }}</p>
            <el-divider />
            <!-- 📌 **必须走 renderPlainText()，不许直接 v-html**（规则 162）
                 修复前这里是 `v-html="result.body.replace(/\n/g, '<br/>')"` →
                 `result.body` 是 **LLM 的原始输出**（后端只 trim 了一下）→
                 LLM 输出里出现 `<img src=x onerror=...>` 就会被浏览器**当 HTML 执行** →
                 而且这段内容会**先落库**（save() → t_product_description）→
                 属于**存储型 XSS**：一次生成，全站每个打开该描述页的人都被打。
                 提示词注入面：SKU / 关键词 / 商品名都是用户可控输入，会进 prompt。
                 ✅ 现在只放行 `<br>`（换行），其余标签全部转义成可见文本。 -->
            <p class="body" v-html="renderPlainText(result.body)" />
            <el-divider />
            <h4>核心卖点</h4>
            <ul>
              <li v-for="(p, i) in result.bulletPoints" :key="i">{{ p }}</li>
            </ul>
            <h4>SEO 关键词</h4>
            <el-tag v-for="k in result.seoKeywords" :key="k" style="margin-right: 6px">{{ k }}</el-tag>
          </div>
          <div v-else class="placeholder">填写左侧表单后点击「一键生成」</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { descriptionApi } from '@/api/ai'
import { productApi } from '@/api/product'
import { unwrapPage } from '@/utils/response'
import { renderPlainText } from '@/utils/sanitize'

const form = reactive({
  sku: 'SKU-BLU-001-BLK',
  language: 'en',
  tone: 'professional',
  keywordsText: '',
})

/**
 * 可选商品（**从 erp-product-service 真加载**）
 *
 * ⚠️ 原来这里是**写死的 5 个 SKU**（2026-11-04 修）——
 *    → 用户只能给这 5 个「不存在的商品」写描述，
 *    → 而且保存接口要 sku → productId，**写死的 SKU 里 4 个是变体 SKU**
 *       （SKU-BLU-001-BLK 在 t_product_sku 里，商品级 internal_sku 是 SKU-BLU-001）
 *    → 口径本来就对不上。
 * → 现在真加载商品列表（返回的是**商品级 SKU**，与 internal_sku 同口径）。
 */
const products = ref<{ sku: string; name: string }[]>([])
const productsLoading = ref(false)

async function loadProducts() {
  productsLoading.value = true
  try {
    const res = await productApi.getProducts({ current: 1, size: 200 } as any)
    const page = unwrapPage<any>(res)
    products.value = (page.records || [])
      .map((p: any) => ({ sku: p.sku, name: p.name }))
      .filter((p: any) => !!p.sku)
  } catch (e) {
    // ⚠️ 失败就**如实置空** + 报错，别拿假数据兜底（规则 12 / 19）
    console.error('商品列表加载失败', e)
    products.value = []
    ElMessage.error('商品列表加载失败')
  } finally {
    productsLoading.value = false
  }
}

const generating = ref(false)
const result = ref<any>(null)
/** 保存中（防重复点击） */
const saving = ref(false)

async function generate() {
  generating.value = true
  result.value = null
  try {
    const r = await descriptionApi.generate({
      sku: form.sku,
      language: form.language as any,
      tone: form.tone,
      keywords: form.keywordsText.split(',').map(s => s.trim()).filter(Boolean),
    })
    result.value = r
  } catch (error) {
    // ⚠️ 之前这里回退到 mock 数据，把后端的失败全部掩盖了。
    //    后端现在返回结构化的 {title, subtitle, body, bulletPoints, seoKeywords}。
    console.error('生成失败', error)
    result.value = null
    ElMessage.error('描述生成失败，请稍后重试')
  } finally {
    generating.value = false
  }
}

function copy() {
  if (!result.value) return
  navigator.clipboard.writeText(JSON.stringify(result.value, null, 2))
  ElMessage.success('已复制')
}

// ⚠️ 原来直接弹「已保存到商品描述（demo）」——**什么都没保存**
//    → 用户以为文案存进商品库了，实际一条没写（规则 51 / 72）。
//    → 后端 `POST /api/v1/product-description/save`（前端契约，按 sku 存）
//    → 现在真调；后端落库失败会抛错，这里如实报错。
async function saveToProduct() {
  if (!result.value) return
  saving.value = true
  try {
    await descriptionApi.save({
      sku: form.sku,
      title: result.value.title,
      subtitle: result.value.subtitle,
      body: result.value.body,
      bulletPoints: result.value.bulletPoints,
      language: form.language,
      platform: 'general',
      templateId: result.value.templateId,
      generationTime: result.value.generationTime,
    })
    ElMessage.success('已保存到商品描述')
  } catch (e) {
    console.error('保存商品描述失败', e)
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

// 📌 进页面就把真实商品拉出来（不再用写死的 SKU 列表）
onMounted(loadProducts)
</script>

<style scoped>
.desc-page { padding: 16px; }
.mock-alert { margin-bottom: 12px; }
.result h3 { margin-top: 0; }
.subtitle { color: #999; margin: 0 0 12px 0; }
.body { line-height: 1.8; color: #333; }
h4 { margin: 16px 0 8px 0; color: #333; }
ul { padding-left: 20px; line-height: 1.8; }
.placeholder { color: #999; text-align: center; padding: 60px 0; }
</style>
