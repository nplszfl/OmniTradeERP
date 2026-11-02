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
            <el-button v-if="result" link type="success" size="small" @click="saveToProduct">保存到商品</el-button>
          </template>
          <div v-if="generating" class="placeholder">AI 正在生成高质量商品描述...</div>
          <div v-else-if="result" class="result">
            <h3>{{ result.title }}</h3>
            <p class="subtitle">{{ result.subtitle }}</p>
            <el-divider />
            <p class="body" v-html="result.body.replace(/\n/g, '<br/>')" />
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
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { descriptionApi } from '@/api/ai'

const form = reactive({
  sku: 'SKU-BLU-001-BLK',
  language: 'en',
  tone: 'professional',
  keywordsText: '',
})

const products = [
  { sku: 'SKU-BLU-001-BLK', name: '无线蓝牙耳机 Pro' },
  { sku: 'SKU-WAT-002-BLK', name: '智能手表 X1' },
  { sku: 'SKU-USB-003-US',  name: '65W GaN 充电器' },
  { sku: 'SKU-PWB-005-BLK', name: '20000mAh 充电宝' },
  { sku: 'SKU-BTY-010',     name: '精华液 30ml' },
]

const generating = ref(false)
const result = ref<any>(null)

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

function saveToProduct() {
  ElMessage.success('已保存到商品描述（demo）')
}
</script>

<style scoped>
.desc-page { padding: 16px; }
.result h3 { margin-top: 0; }
.subtitle { color: #999; margin: 0 0 12px 0; }
.body { line-height: 1.8; color: #333; }
h4 { margin: 16px 0 8px 0; color: #333; }
ul { padding-left: 20px; line-height: 1.8; }
.placeholder { color: #999; text-align: center; padding: 60px 0; }
</style>
