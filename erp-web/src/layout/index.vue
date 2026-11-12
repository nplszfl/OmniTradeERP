<template>
  <el-container class="layout-container">
    <el-aside width="220px">
      <div class="logo">跨境电商ERP</div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#001529"
        text-color="#fff"
        active-text-color="#1890ff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据看板</span>
        </el-menu-item>
        
        <el-sub-menu index="order-group">
          <template #title>
            <el-icon><Document /></el-icon>
            <span>订单管理</span>
          </template>
          <el-menu-item index="/order">订单列表</el-menu-item>
        </el-sub-menu>
        
        <el-sub-menu index="product-group">
          <template #title>
            <el-icon><Goods /></el-icon>
            <span>商品管理</span>
          </template>
          <el-menu-item index="/product">商品列表</el-menu-item>
        </el-sub-menu>
        
        <el-sub-menu index="customer-group">
          <template #title>
            <el-icon><User /></el-icon>
            <span>客户管理</span>
          </template>
          <el-menu-item index="/customer">客户列表</el-menu-item>
        </el-sub-menu>
        
        <el-sub-menu index="supplier-group">
          <template #title>
            <el-icon><OfficeBuilding /></el-icon>
            <span>供应商管理</span>
          </template>
          <el-menu-item index="/supplier">供应商列表</el-menu-item>
        </el-sub-menu>
        
        <el-sub-menu index="inventory-group">
          <template #title>
            <el-icon><Box /></el-icon>
            <span>库存管理</span>
          </template>
          <el-menu-item index="/inventory">库存列表</el-menu-item>
          <el-menu-item index="/inventory-alert">库存预警</el-menu-item>
        </el-sub-menu>
        
        <el-sub-menu index="warehouse-group">
          <template #title>
            <el-icon><House /></el-icon>
            <span>仓库管理</span>
          </template>
          <el-menu-item index="/warehouse">仓库列表</el-menu-item>
        </el-sub-menu>
        
        <el-sub-menu index="logistics-group">
          <template #title>
            <el-icon><Van /></el-icon>
            <span>物流管理</span>
          </template>
          <el-menu-item index="/logistics">物流中心</el-menu-item>
        </el-sub-menu>
        
        <el-menu-item index="/platform">
          <el-icon><Setting /></el-icon>
          <span>平台配置</span>
        </el-menu-item>
        
        <el-menu-item index="/finance">
          <el-icon><Money /></el-icon>
          <span>财务管理</span>
        </el-menu-item>
        
        <el-menu-item index="/user">
          <el-icon><UserFilled /></el-icon>
          <span>用户管理</span>
        </el-menu-item>

        <!-- ====== 数据分析（新增）====== -->
        <el-sub-menu index="analytics-group">
          <template #title>
            <el-icon><DataLine /></el-icon>
            <span>数据分析</span>
          </template>
          <el-menu-item index="/analytics">数据看板</el-menu-item>
          <el-menu-item index="/report">报表中心</el-menu-item>
        </el-sub-menu>

        <!-- ====== AI 智能中心（新增）====== -->
        <el-sub-menu index="ai-group">
          <template #title>
            <el-icon><MagicStick /></el-icon>
            <span>AI 智能中心</span>
          </template>
          <el-menu-item index="/ai/assistant">AI 客服助手</el-menu-item>
          <el-menu-item index="/ai/pricing">智能定价</el-menu-item>
          <el-menu-item index="/ai/recommend">选品推荐</el-menu-item>
          <el-menu-item index="/ai/anomaly">异常检测</el-menu-item>
          <el-menu-item index="/ai/description">描述生成</el-menu-item>
          <el-menu-item index="/ai/inventory-predict">库存预测</el-menu-item>
        </el-sub-menu>

        <!-- ====== 采购管理（新增）====== -->
        <el-menu-item index="/purchase">
          <el-icon><ShoppingCart /></el-icon>
          <span>采购管理</span>
        </el-menu-item>

        <!-- ====== 社交电商（新增）====== -->
        <el-menu-item index="/social">
          <el-icon><Share /></el-icon>
          <span>社交电商</span>
        </el-menu-item>

        <!-- ====== 租户管理（新增）====== -->
        <el-menu-item index="/tenant">
          <el-icon><Key /></el-icon>
          <span>租户管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header>
        <div class="header-title">{{ pageTitle }}</div>
        <div class="header-user">
          <el-dropdown>
            <span class="el-dropdown-link">
              管理员 <el-icon class="el-icon--right"><arrow-down /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <!-- 📌 **修改密码** —— 后端 `POST /user/change-password` + api 层 `userApi.changePassword`
                     都已就绪，但**原来没有任何视图调用它** → 用户在界面上改不了自己的密码。
                     （2026-11-12 接上：死接口清单里「api 层写了但视图没调」的一类。） -->
                <el-dropdown-item @click="handleChangePassword">修改密码</el-dropdown-item>
                <el-dropdown-item>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main>
        <router-view />
      </el-main>
    </el-container>

    <!-- 📌 修改密码对话框（自助）—— 旧密码 + 新密码 + 确认新密码 -->
    <el-dialog v-model="pwdDialogVisible" title="修改密码" width="440px" :close-on-click-modal="false">
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdFormRules" label-width="90px">
        <el-form-item label="当前密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再输入一次新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSubmitting" @click="submitChangePassword">确认修改</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/user'
import { DataAnalysis, Document, Goods, User, OfficeBuilding, Box, House, Van, Setting, Money, UserFilled, ArrowDown, DataLine, MagicStick, ShoppingCart, Share, Key } from '@element-plus/icons-vue'

const route = useRoute()

const activeMenu = computed(() => route.path)
const pageTitle = computed(() => route.meta.title || '首页')

// ====== 修改密码（自助）======
const pwdDialogVisible = ref(false)
const pwdSubmitting = ref(false)
const pwdFormRef = ref()
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const pwdFormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码至少 6 位', trigger: 'blur' }
  ],
  // 📌 **两次输入必须一致** —— 不校验的话用户会以为改成功了，其实后端拒了
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, cb: (e?: Error) => void) => {
        if (value !== pwdForm.newPassword) cb(new Error('两次输入的新密码不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

const handleChangePassword = () => {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdDialogVisible.value = true
}

const submitChangePassword = async () => {
  const valid = await pwdFormRef.value?.validate().catch(() => false)
  if (!valid) return
  pwdSubmitting.value = true
  try {
    await userApi.changePassword(pwdForm.oldPassword, pwdForm.newPassword)
    ElMessage.success('密码修改成功')
    pwdDialogVisible.value = false
  } catch (error) {
    // 📌 **不自己拼错误文案** —— 后端已经返回了「旧密码错误」等具体原因
    console.error('修改密码失败:', error)
  } finally {
    pwdSubmitting.value = false
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.el-aside {
  background-color: #001529;
  overflow-x: hidden;
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}

.el-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #fff;
  border-bottom: 1px solid #f0f0f0;
}

.header-title {
  font-size: 18px;
  font-weight: 500;
}

.header-user {
  cursor: pointer;
}

.el-dropdown-link {
  display: flex;
  align-items: center;
}

.el-main {
  background-color: #f0f2f5;
  padding: 0;
}
</style>
