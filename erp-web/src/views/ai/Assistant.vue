<template>
  <div class="ai-assistant">
    <el-tabs v-model="activeTab" class="ai-tabs">
      <!-- ========== 对话 ========== -->
      <el-tab-pane label="对话" name="chat">
    <el-card shadow="never" class="chat-card">
      <template #header>
        <div class="card-head">
          <span><el-icon><MagicStick /></el-icon> AI 客服助手（基于 RAG 知识库）</span>
          <el-button size="small" @click="newSession">新会话</el-button>
        </div>
      </template>

      <div class="chat-messages" ref="msgBox">
        <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
          <div class="avatar">{{ m.role === 'user' ? '我' : 'AI' }}</div>
          <div class="bubble">
            <div>{{ m.content }}</div>
            <!-- 📌 **降级要看得见** —— 后端真实 LLM 挂了会降级成模板回复，
                 mock=true 时必须告诉用户「这是模板，不是真 AI 生成的」 -->
            <el-tag v-if="m.mock" size="small" type="warning" class="mock-tag">
              模拟回复{{ m.mockReason ? '（' + m.mockReason + '）' : '' }}
            </el-tag>
          </div>
        </div>
        <div v-if="loading" class="msg bot">
          <div class="avatar">AI</div>
          <div class="bubble typing">正在思考...</div>
        </div>
      </div>

      <div class="chat-input">
        <el-input v-model="input" type="textarea" :rows="2" placeholder="问点什么：如何查询订单？运费怎么算？" @keydown.enter.exact.prevent="send" />
        <div class="input-actions">
          <el-button @click="askFAQ('如何查询我的订单？')">订单查询</el-button>
          <el-button @click="askFAQ('运费怎么算？')">运费</el-button>
          <el-button @click="askFAQ('如何申请退款？')">退款</el-button>
          <el-button type="primary" :loading="loading" @click="send">发送 (Enter)</el-button>
        </div>
      </div>
    </el-card>
      </el-tab-pane>

      <!-- ========== 知识库管理 ========== -->
      <el-tab-pane label="知识库" name="knowledge">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span><el-icon><Collection /></el-icon> 知识库管理</span>
              <div>
                <el-button size="small" type="primary" :icon="Plus" @click="openAddDialog">新增知识</el-button>
                <el-button size="small" :icon="Refresh" @click="loadKnowledge">刷新</el-button>
              </div>
            </div>
          </template>

          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
            title="这里的知识就是 AI 客服回答问题时检索的问答对（表 knowledge_base）。新增后立即生效，聊天检索会用到。" />

          <el-form inline style="margin-bottom: 8px">
            <el-form-item label="分类">
              <el-select v-model="kbCategory" placeholder="全部分类" clearable style="width: 160px" @change="handleKbSearch">
                <el-option v-for="c in kbCategories" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleKbSearch">查询</el-button>
            </el-form-item>
          </el-form>

          <el-table :data="kbList" v-loading="kbLoading" border stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="category" label="分类" width="100" />
            <el-table-column prop="question" label="问题" min-width="220" show-overflow-tooltip />
            <el-table-column prop="answer" label="答案" min-width="260" show-overflow-tooltip />
            <el-table-column label="标签" width="150">
              <template #default="{ row }">
                <el-tag v-for="t in row.tags || []" :key="t" size="small" style="margin-right: 4px">{{ t }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" size="small" @click="handleDeleteKb(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            style="margin-top: 12px; justify-content: flex-end"
            layout="total, prev, pager, next"
            :total="kbTotal"
            :page-size="kbPageSize"
            :current-page="kbPage"
            @current-change="handleKbPageChange" />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增知识对话框 -->
    <el-dialog v-model="kbDialogVisible" title="新增知识" width="520px">
      <el-form :model="kbForm" label-width="80px">
        <el-form-item label="分类" required>
          <el-input v-model="kbForm.category" placeholder="如：订单 / 物流 / 售后" />
        </el-form-item>
        <el-form-item label="问题" required>
          <el-input v-model="kbForm.question" type="textarea" :rows="2" placeholder="用户可能会问的问题" />
        </el-form-item>
        <el-form-item label="答案" required>
          <el-input v-model="kbForm.answer" type="textarea" :rows="4" placeholder="标准答案" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="kbForm.tagsText" placeholder="逗号分隔，如：订单,查询" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="kbDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="kbSaving" @click="submitKb">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { aiAssistantApi, knowledgeBaseApi, type KnowledgeBaseItem } from '@/api/ai'
import { MagicStick, Collection, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { unwrapPage, unwrap } from '@/utils/response'

const messages = ref<{ role: 'user' | 'bot'; content: string; mock?: boolean; mockReason?: string }[]>([
  // 📌 开场白里的知识库条数**必须来自真实数据**（2026-11-04 修）：
  //    原来写死「基于 330+ 行业知识库」，而真实种子只有 31 条
  //    → **界面在承诺一个不存在的**（规则 19：能力不能只是说说）。
  //    onMounted 里会用 /ai/knowledge/stats 的 activeCount 覆盖这个数字。
  // 📌📌 且这个 activeCount 必须是 **knowledge_base**（聊天实际检索的那张表）
  //    的条数 —— 原来它返回的是 knowledge_document（5 条），与检索口径不符。
  { role: 'bot', content: '您好，我是 AI 助手。正在加载行业知识库…' },
])
const input = ref('')
const loading = ref(false)
const sessionId = ref<string>('')
const msgBox = ref<HTMLElement>()

const FAQ: Record<string, string> = {
  '如何查询我的订单？': '您可以在「订单管理」页面查看所有订单信息，包括待付款、待发货、已发货等状态。支持按订单号、买家国家、商品 SKU 等多条件筛选。',
  '运费怎么算？': '国际运费根据目的地、重量、物流方式（DHL/YANWEN/SF）综合计算。具体可在「订单详情 → 物流信息」查看分摊的运费。',
  '如何申请退款？': '在订单详情页点击「申请售后」，选择退款类型和原因，提交后客服将在 24h 内处理。',
}

function askFAQ(q: string) { input.value = q; send() }

async function send() {
  if (!input.value.trim() || loading.value) return
  const text = input.value.trim()
  messages.value.push({ role: 'user', content: text })
  input.value = ''
  loading.value = true
  await nextTick(); scrollBottom()

  try {
    const r = await aiAssistantApi.chat({ sessionId: sessionId.value || undefined, message: text })
    // 📌 后端字段是 `content`（不是 `reply`！）——
    //    之前读 `r.reply` 恒为 undefined → **每次聊天气泡都是空的**
    messages.value.push({
      role: 'bot',
      content: r.content ?? '',
      mock: r.mock,
      mockReason: r.mockReason,
    })
    sessionId.value = r.sessionId
  } catch {
    // 后端无响应时降级到 FAQ 或模板回答
    const fallback = FAQ[text] || '抱歉，AI 服务暂不可用。请稍后再试，或在左侧菜单「报表中心」查看数据。'
    messages.value.push({ role: 'bot', content: fallback })
  } finally {
    loading.value = false
    await nextTick(); scrollBottom()
  }
}

function scrollBottom() {
  if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
}

function newSession() {
  messages.value = [{ role: 'bot', content: '新会话已开始，请提问。' }]
  sessionId.value = ''
}

// ========== 知识库管理 ==========
const activeTab = ref('chat')
const kbLoading = ref(false)
const kbSaving = ref(false)
const kbList = ref<KnowledgeBaseItem[]>([])
const kbCategories = ref<string[]>([])
const kbCategory = ref<string>('')
const kbPage = ref(1)
const kbPageSize = 20
const kbTotal = ref(0)
const kbDialogVisible = ref(false)
const kbForm = ref({ category: '', question: '', answer: '', tagsText: '' })

const loadKnowledge = async () => {
  kbLoading.value = true
  try {
    const res = await knowledgeBaseApi.list({
      page: kbPage.value,
      size: kbPageSize,
      category: kbCategory.value || undefined
    })
    const pageData = unwrapPage(res)
    kbList.value = pageData.records || []
    kbTotal.value = pageData.total || 0
  } catch (error) {
    console.error('加载知识库失败', error)
  } finally {
    kbLoading.value = false
  }
}

const loadKbCategories = async () => {
  try {
    const res = await knowledgeBaseApi.categories()
    kbCategories.value = Array.isArray(res) ? res : []
  } catch (error) {
    console.error('加载知识分类失败', error)
  }
}

/**
 * 加载知识库统计，用**真实条数**改写开场白。
 *
 * 📌 2026-11-04：开场白原本写死「基于 330+ 行业知识库」，
 *    而 `knowledge_base` 真实种子只有 31 条
 *    📌 这两个条数是**声明**，由 `scripts/check-seed-row-claims.py` 当场核验：
 *    种子改了而这里没改 → 闸门报红（规则 51：手写结论会静默过期）。
 *    rows: knowledge_base=31
 *    rows: knowledge_document=5
 *    → **界面在承诺一个不存在的东西**。
 * 📌 改成动态取数 → 以后无论加多少条、减多少条，**都不会再骗人**。
 * 📌 取不到就**降级为不说数字**（而不是编一个）—— 降级必须可见。
 *
 * 📌📌 **2026-11-04 修口径不一致（界面自相矛盾）**：
 *    这个接口原来统计的是 `knowledge_document`（向量文档）表 = **5** 条，
 *    而**聊天实际检索的是 `knowledge_base` 表 = 31 条**，
 *    同一个页面的「知识库」页签里也列着 31 条
 *    → 开场白说「基于 5 条」、页签里 31 条，**界面自己打自己的脸**。
 *    → 后端已把 `/ai/knowledge/stats` 改成统计 `knowledge_base`，
 *      现在「数字 / 文案 / 检索」三处口径一致。
 */
const loadKbStats = async () => {
  const fallback = '您好，我是 AI 助手。可以回答订单、物流、库存、财务、退款等问题。'
  try {
    const res = unwrap<{ activeCount?: number }>(await knowledgeBaseApi.stats())
    const activeCount = res?.activeCount ?? 0
    messages.value[0].content = activeCount > 0
      ? `您好，我是 AI 助手。基于 ${activeCount} 条行业知识库，可以回答订单、物流、库存、财务、退款等问题。`
      : fallback
  } catch (error) {
    console.error('加载知识库统计失败', error)
    messages.value[0].content = fallback
  }
}

const handleKbSearch = () => {
  kbPage.value = 1
  loadKnowledge()
}

const handleKbPageChange = (p: number) => {
  kbPage.value = p
  loadKnowledge()
}

const openAddDialog = () => {
  kbForm.value = { category: '', question: '', answer: '', tagsText: '' }
  kbDialogVisible.value = true
}

const submitKb = async () => {
  const f = kbForm.value
  if (!f.category.trim() || !f.question.trim() || !f.answer.trim()) {
    ElMessage.warning('分类、问题、答案都要填')
    return
  }
  kbSaving.value = true
  try {
    await knowledgeBaseApi.add({
      category: f.category.trim(),
      question: f.question.trim(),
      answer: f.answer.trim(),
      tags: f.tagsText.split(',').map(t => t.trim()).filter(Boolean)
    })
    ElMessage.success('知识已新增')
    kbDialogVisible.value = false
    loadKnowledge()
    loadKbCategories()
  } catch (error) {
    console.error('新增知识失败', error)
  } finally {
    kbSaving.value = false
  }
}

const handleDeleteKb = async (row: KnowledgeBaseItem) => {
  try {
    await ElMessageBox.confirm(`确定删除知识「${row.question}」？`, '提示', { type: 'warning' })
  } catch {
    return   // 用户取消
  }
  try {
    await knowledgeBaseApi.remove(row.id)
    ElMessage.success('已删除')
    loadKnowledge()
  } catch (error) {
    console.error('删除知识失败', error)
  }
}

onMounted(() => {
  loadKnowledge()
  loadKbCategories()
  loadKbStats()
})
</script>

<style scoped>
.ai-assistant { padding: 16px; height: calc(100vh - 100px); }
.ai-tabs { height: 100%; }
.ai-tabs :deep(.el-tabs__content) { height: calc(100% - 40px); overflow-y: auto; }
.chat-card { display: flex; flex-direction: column; height: 100%; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.chat-messages {
  flex: 1; overflow-y: auto; padding: 16px; background: #fafafa;
  border-radius: 4px; margin-bottom: 12px;
}
.msg { display: flex; gap: 10px; margin-bottom: 16px; }
.msg.user { flex-direction: row-reverse; }
.msg.user .bubble { background: #409eff; color: #fff; }
.msg .avatar {
  width: 36px; height: 36px; border-radius: 50%; display: flex; align-items: center;
  justify-content: center; background: #409eff; color: #fff; font-weight: 600; flex-shrink: 0;
}
.msg.bot .avatar { background: #67c23a; }
.msg .bubble {
  max-width: 75%; background: #fff; padding: 12px 16px; border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.06); line-height: 1.6;
}
.msg .bubble.typing { color: #999; font-style: italic; }
/* 📌 模拟回复的标记 —— 要显眼，让用户知道这不是真 AI 答的 */
.mock-tag { margin-top: 8px; }
.chat-input { padding-top: 8px; }
.input-actions { margin-top: 8px; display: flex; gap: 8px; }
</style>
