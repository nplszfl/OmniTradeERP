<template>
  <div class="customer-page">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon" style="background: #409eff">
            <el-icon :size="24"><User /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.totalCustomers }}</div>
            <div class="stat-label">客户总数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon" style="background: #e6a23c">
            <el-icon :size="24"><Star /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.vipCount }}</div>
            <div class="stat-label">VIP客户</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon" style="background: #67c23a">
            <el-icon :size="24"><CircleCheck /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.activeCount }}</div>
            <div class="stat-label">活跃客户</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon" style="background: #f56c6c">
            <el-icon :size="24"><Money /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">${{ formatAmount(stats.totalRevenue) }}</div>
            <div class="stat-label">总消费</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 标签页：客户 / 跟进 / 分组 / 标签 -->
    <el-tabs v-model="activeTab" class="feature-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="客户列表" name="customers">

      <!-- ========== 跟进记录 ========== -->
      <el-tab-pane label="跟进记录" name="follow">
        <el-card shadow="never">
          <el-form :inline="true">
            <el-form-item label="跟进结果">
              <el-select v-model="followSearch.result" placeholder="全部" clearable style="width: 120px">
                <el-option label="待跟进" value="PENDING" />
                <el-option label="已完成" value="SUCCESS" />
                <el-option label="失败" value="FAILED" />
              </el-select>
            </el-form-item>
            <el-form-item label="跟进方式">
              <el-select v-model="followSearch.followType" placeholder="全部" clearable style="width: 120px">
                <el-option label="电话" value="PHONE" />
                <el-option label="邮件" value="EMAIL" />
                <el-option label="微信" value="WECHAT" />
                <el-option label="拜访" value="VISIT" />
                <el-option label="其他" value="OTHER" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleFollowSearch">搜索</el-button>
              <el-button type="warning" :icon="Bell" @click="loadFollowPendingToday">今日待跟进</el-button>
              <el-button type="success" :icon="Plus" @click="handleAddFollow">新增跟进</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <el-table v-loading="followLoading" :data="followList" stripe>
            <el-table-column prop="customerId" label="客户ID" width="90" />
            <el-table-column prop="followType" label="方式" width="90">
              <template #default="{ row }">
                <el-tag size="small">{{ getFollowTypeName(row.followType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="content" label="跟进内容" min-width="240" show-overflow-tooltip />
            <el-table-column prop="followerName" label="跟进人" width="110" />
            <el-table-column prop="followTime" label="跟进时间" width="160">
              <template #default="{ row }">{{ row.followTime ? formatTime(row.followTime) : '-' }}</template>
            </el-table-column>
            <el-table-column prop="nextFollowTime" label="下次跟进" width="160">
              <template #default="{ row }">{{ row.nextFollowTime ? formatTime(row.nextFollowTime) : '-' }}</template>
            </el-table-column>
            <el-table-column prop="result" label="结果" width="100">
              <template #default="{ row }">
                <el-tag :type="getFollowResultType(row.result)" size="small">{{ getFollowResultName(row.result) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.result === 'PENDING'" link type="success" size="small" @click="handleCompleteFollow(row)">完成</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteFollow(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="followPagination.current"
            v-model:page-size="followPagination.size"
            :total="followPagination.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @size-change="loadFollowList"
            @current-change="loadFollowList"
          />
        </el-card>
      </el-tab-pane>

      <!-- ========== 分组 ========== -->
      <el-tab-pane label="客户分组" name="groups">
        <el-card shadow="never">
          <el-form :inline="true">
            <el-form-item label="关键词">
              <el-input v-model="groupKeyword" placeholder="分组名称" clearable style="width: 180px" @keyup.enter="loadGroupList" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="loadGroupList">搜索</el-button>
              <el-button type="success" :icon="Plus" @click="handleAddGroup">新增分组</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <el-table v-loading="groupLoading" :data="groupList" stripe>
            <el-table-column prop="name" label="分组名称" width="180" />
            <el-table-column prop="description" label="描述" min-width="260" show-overflow-tooltip />
            <el-table-column prop="memberCount" label="成员数" width="100" />
            <el-table-column prop="sortOrder" label="排序" width="90" />
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleEditGroup(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteGroup(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ========== 标签 ========== -->
      <el-tab-pane label="客户标签" name="tags">
        <el-card shadow="never">
          <el-form :inline="true">
            <el-form-item label="关键词">
              <el-input v-model="tagKeyword" placeholder="标签名称" clearable style="width: 180px" @keyup.enter="loadTagList" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="loadTagList">搜索</el-button>
              <el-button type="success" :icon="Plus" @click="handleAddTag">新增标签</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <el-table v-loading="tagLoading" :data="tagList" stripe>
            <el-table-column prop="name" label="标签名称" width="180">
              <template #default="{ row }">
                <el-tag :color="row.color" size="small" style="color: #fff">{{ row.name }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="260" show-overflow-tooltip />
            <el-table-column prop="sortOrder" label="排序" width="90" />
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleEditTag(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteTag(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

    <!-- 搜索筛选 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="姓名/邮箱/电话" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="平台">
          <el-select v-model="searchForm.platform" placeholder="全部平台" clearable style="width: 120px">
            <el-option label="亚马逊" value="amazon" />
            <el-option label="eBay" value="ebay" />
            <el-option label="Shopee" value="shopee" />
            <el-option label="Lazada" value="lazada" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户等级">
          <el-select v-model="searchForm.level" placeholder="全部等级" clearable style="width: 120px">
            <el-option label="VIP" value="VIP" />
            <el-option label="普通" value="NORMAL" />
            <el-option label="新客户" value="NEW" />
          </el-select>
        </el-form-item>
        <el-form-item label="国家">
          <el-input v-model="searchForm.country" placeholder="国家名称" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="Plus" @click="handleAdd">新增客户</el-button>
          <el-button :icon="Star" @click="handleShowVip">只看 VIP</el-button>
        </el-form-item>
      </el-form>
    </el-card>

      </el-tab-pane>
    </el-tabs>
    <!-- 客户列表 -->
    <el-card class="table-card" shadow="never">
      <el-table v-loading="loading" :data="customerList" stripe>
        <el-table-column prop="name" label="客户姓名" width="120" fixed>
          <template #default="{ row }">
            <el-link type="primary" @click="handleViewDetail(row)">{{ row.name }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" width="180" show-overflow-tooltip />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column prop="platform" label="平台" width="90">
          <template #default="{ row }">
            <el-tag :type="getPlatformType(row.platform)" size="small">{{ getPlatformName(row.platform) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="level" label="等级" width="80">
          <template #default="{ row }">
            <el-tag :type="getLevelType(row.level)" size="small">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalOrders" label="订单数" width="80" />
        <el-table-column prop="totalSpent" label="累计消费" width="120">
          <template #default="{ row }">
            ${{ formatAmount(row.totalSpent) }}
          </template>
        </el-table-column>
        <el-table-column prop="avgOrderValue" label="客单价" width="100">
          <template #default="{ row }">
            ${{ formatAmount(row.avgOrderValue) }}
          </template>
        </el-table-column>
        <el-table-column prop="country" label="国家" width="100" />
        <el-table-column prop="lastOrderAt" label="最近下单" width="150">
          <template #default="{ row }">
            {{ row.lastOrderAt ? formatTime(row.lastOrderAt) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleViewDetail(row)">详情</el-button>
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadCustomers"
        @current-change="loadCustomers"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <!-- 客户详情弹窗 -->
    <el-dialog v-model="detailVisible" title="客户详情" width="800px" append-to-body>
      <el-descriptions :column="2" border v-if="currentCustomer">
        <el-descriptions-item label="客户姓名">{{ currentCustomer.name }}</el-descriptions-item>
        <el-descriptions-item label="客户等级">
          <el-tag :type="getLevelType(currentCustomer.level)">{{ currentCustomer.level }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ currentCustomer.email }}</el-descriptions-item>
        <el-descriptions-item label="电话">{{ currentCustomer.phone }}</el-descriptions-item>
        <el-descriptions-item label="平台">
          <el-tag :type="getPlatformType(currentCustomer.platform)">{{ getPlatformName(currentCustomer.platform) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="国家">{{ currentCustomer.country }}</el-descriptions-item>
        <el-descriptions-item label="省份">{{ currentCustomer.state || '-' }}</el-descriptions-item>
        <el-descriptions-item label="城市">{{ currentCustomer.city || '-' }}</el-descriptions-item>
        <el-descriptions-item label="详细地址" :span="2">{{ currentCustomer.address || '-' }}</el-descriptions-item>
        <el-descriptions-item label="订单总数">{{ currentCustomer.totalOrders }}</el-descriptions-item>
        <el-descriptions-item label="累计消费">${{ formatAmount(currentCustomer.totalSpent) }}</el-descriptions-item>
        <el-descriptions-item label="平均客单价">${{ formatAmount(currentCustomer.avgOrderValue) }}</el-descriptions-item>
        <el-descriptions-item label="最近下单">{{ currentCustomer.lastOrderAt ? formatTime(currentCustomer.lastOrderAt) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户备注" :span="2">{{ currentCustomer.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="currentCustomer.status === 'ACTIVE' ? 'success' : 'info'">{{ currentCustomer.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(currentCustomer.createTime) }}</el-descriptions-item>
      </el-descriptions>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 新增跟进记录弹窗 -->
    <el-dialog v-model="followFormVisible" title="新增跟进记录" width="600px" append-to-body>
      <el-form ref="followFormRef" :model="followForm" :rules="followFormRules" label-width="100px">
        <el-form-item label="客户ID" prop="customerId">
          <el-input-number v-model="followForm.customerId" :min="1" :controls="false" style="width: 100%" placeholder="客户 ID" />
        </el-form-item>
        <el-form-item label="跟进方式" prop="followType">
          <el-select v-model="followForm.followType" style="width: 100%">
            <el-option label="电话" value="PHONE" />
            <el-option label="邮件" value="EMAIL" />
            <el-option label="微信" value="WECHAT" />
            <el-option label="拜访" value="VISIT" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="跟进内容" prop="content">
          <el-input v-model="followForm.content" type="textarea" :rows="3" placeholder="这次沟通聊了什么" />
        </el-form-item>
        <el-form-item label="跟进时间">
          <el-input v-model="followForm.followTime" placeholder="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="下次跟进">
          <el-input v-model="followForm.nextFollowTime" placeholder="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="跟进人">
          <el-input v-model="followForm.followUserName" placeholder="跟进人姓名" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="followForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="followFormVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitFollow">确定</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑客户分组弹窗 -->
    <el-dialog v-model="groupFormVisible" :title="groupEditId ? '编辑分组' : '新增分组'" width="520px" append-to-body>
      <el-form :model="groupForm" label-width="90px">
        <el-form-item label="分组名称">
          <el-input v-model="groupForm.name" placeholder="如：核心客户" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="groupForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="groupForm.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupFormVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitGroup">确定</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑客户标签弹窗 -->
    <el-dialog v-model="tagFormVisible" :title="tagEditId ? '编辑标签' : '新增标签'" width="520px" append-to-body>
      <el-form :model="tagForm" label-width="90px">
        <el-form-item label="标签名称">
          <el-input v-model="tagForm.name" placeholder="如：高价值" />
        </el-form-item>
        <el-form-item label="颜色">
          <el-color-picker v-model="tagForm.color" />
          <span style="margin-left: 12px; color: #909399">{{ tagForm.color }}</span>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="tagForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="tagForm.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tagFormVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitTag">确定</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑客户弹窗 -->
    <el-dialog v-model="formVisible" :title="isEdit ? '编辑客户' : '新增客户'" width="600px" append-to-body>
      <el-form :model="form" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item label="客户姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入客户姓名" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入电话" />
        </el-form-item>
        <el-form-item label="平台" prop="platform">
          <el-select v-model="form.platform" placeholder="请选择平台" style="width: 100%">
            <el-option label="亚马逊" value="amazon" />
            <el-option label="eBay" value="ebay" />
            <el-option label="Shopee" value="shopee" />
            <el-option label="Lazada" value="lazada" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户等级" prop="level">
          <el-select v-model="form.level" placeholder="请选择等级" style="width: 100%">
            <el-option label="VIP" value="VIP" />
            <el-option label="普通" value="NORMAL" />
            <el-option label="新客户" value="NEW" />
          </el-select>
        </el-form-item>
        <el-form-item label="国家">
          <el-input v-model="form.country" placeholder="请输入国家" />
        </el-form-item>
        <el-form-item label="省份">
          <el-input v-model="form.state" placeholder="请输入省份" />
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="form.city" placeholder="请输入城市" />
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model="form.address" type="textarea" :rows="2" placeholder="请输入详细地址" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Refresh, Plus, User, Star, CircleCheck, Money, Bell } from '@element-plus/icons-vue'
import {
  customerApi,
  customerFollowApi,
  customerGroupApi,
  customerTagApi,
  type Customer,
  type CustomerStats,
  type CustomerFollowRecord,
  type CustomerGroup,
  type CustomerTag
} from '@/api/customer'
import dayjs from 'dayjs'
import { unwrap, unwrapPage } from '@/utils/response'

const loading = ref(false)
const customerList = ref<Customer[]>([])
const detailVisible = ref(false)
const formVisible = ref(false)
const isEdit = ref(false)
const currentCustomer = ref<Customer | null>(null)
const formRef = ref<FormInstance>()

// 统计数据
const stats = reactive<CustomerStats>({
  totalCustomers: 0,
  vipCount: 0,
  activeCount: 0,
  totalRevenue: 0,
  avgSpent: 0
})

// 搜索表单
const searchForm = reactive({
  keyword: '',
  platform: '',
  level: '',
  country: ''
})

// 分页
const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

// 表单
const form = reactive<Customer>({
  id: 0,
  name: '',
  email: '',
  phone: '',
  platform: '',
  platformCustomerId: '',
  country: '',
  state: '',
  city: '',
  address: '',
  postalCode: '',
  level: 'NORMAL',
  totalOrders: 0,
  totalSpent: 0,
  avgOrderValue: 0,
  lastOrderAt: '',
  remark: '',
  status: 'ACTIVE',
  createTime: '',
  updateTime: ''
})

// 表单验证规则
const followFormRules = {
  customerId: [{ required: true, message: '请输入客户ID', trigger: 'blur' }],
  content: [{ required: true, message: '请输入跟进内容', trigger: 'blur' }]
}

const formRules = {
  name: [{ required: true, message: '请输入客户姓名', trigger: 'blur' }],
  email: [{ required: true, message: '请输入邮箱', trigger: 'blur' }],
  platform: [{ required: true, message: '请选择平台', trigger: 'change' }]
}

// 加载客户列表
const loadCustomers = async () => {
  loading.value = true
  try {
    const res = await customerApi.getList({
      current: pagination.current,
      size: pagination.size,
      keyword: searchForm.keyword || undefined,
      platform: searchForm.platform || undefined,
      level: searchForm.level || undefined,
      country: searchForm.country || undefined
    })
    const pageData = unwrapPage(res)
    customerList.value = pageData.records || []
    pagination.total = pageData.total || 0
  } catch (error) {
    console.error('加载客户失败', error)
  } finally {
    loading.value = false
  }
}

// 加载统计数据
const loadStats = async () => {
  try {
    const res = await customerApi.getStats()
    Object.assign(stats, res)
  } catch (error) {
    console.error('加载统计失败', error)
  }
}

// 搜索
const handleSearch = () => {
  pagination.current = 1
  loadCustomers()
}

// 重置
const handleReset = () => {
  searchForm.keyword = ''
  searchForm.platform = ''
  searchForm.level = ''
  searchForm.country = ''
  handleSearch()
}

// 新增
// 只看 VIP（用 /customer/vip 这个独立接口，不是前端过滤）
const handleShowVip = async () => {
  loading.value = true
  try {
    const res = await customerApi.getVipList()
    const rows = unwrap<Customer[]>(res)
    customerList.value = Array.isArray(rows) ? rows : []
    pagination.total = customerList.value.length
    pagination.current = 1
    searchForm.level = 'VIP'
  } catch (error) {
    console.error('加载VIP客户失败', error)
  } finally {
    loading.value = false
  }
}

const handleAdd = () => {
  isEdit.value = false
  resetForm()
  formVisible.value = true
}

// 编辑
const handleEdit = (customer: Customer) => {
  isEdit.value = true
  Object.assign(form, customer)
  formVisible.value = true
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (isEdit.value) {
          await customerApi.update(form.id, form)
          ElMessage.success('更新成功')
        } else {
          await customerApi.create(form)
          ElMessage.success('创建成功')
        }
        formVisible.value = false
        loadCustomers()
        loadStats()
      } catch (error) {
        console.error('操作失败', error)
      }
    }
  })
}

// 重置表单
const resetForm = () => {
  Object.assign(form, {
    id: 0,
    name: '',
    email: '',
    phone: '',
    platform: '',
    platformCustomerId: '',
    country: '',
    state: '',
    city: '',
    address: '',
    postalCode: '',
    level: 'NORMAL',
    totalOrders: 0,
    totalSpent: 0,
    avgOrderValue: 0,
    lastOrderAt: '',
    remark: '',
    status: 'ACTIVE'
  })
}

// 查看详情
const handleViewDetail = async (customer: Customer) => {
  // 先用列表行数据把弹窗打开（秒开），再异步拉完整详情补齐
  // 列表里没有的字段：平台客户号、地址、省市、备注、创建时间
  currentCustomer.value = customer
  detailVisible.value = true
  try {
    const res = await customerApi.getById(customer.id)
    const detail = unwrap<Customer>(res)
    if (detail && detail.id) {
      currentCustomer.value = { ...customer, ...detail }
    }
  } catch (error) {
    // 详情拉失败不影响弹窗（列表数据已经够看）
    console.error('加载客户详情失败', error)
  }
}

// 删除
const handleDelete = (customer: Customer) => {
  ElMessageBox.confirm(`确定删除客户 ${customer.name} 吗？`, '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      await customerApi.delete(customer.id)
      ElMessage.success('删除成功')
      loadCustomers()
      loadStats()
    } catch (error) {
      console.error('删除失败', error)
    }
  })
}

// 平台名称
const getPlatformName = (platform: string) => {
  const map: Record<string, string> = {
    amazon: '亚马逊',
    ebay: 'eBay',
    shopee: 'Shopee',
    lazada: 'Lazada'
  }
  return map[platform] || platform
}

// 平台标签类型
const getPlatformType = (platform: string) => {
  const map: Record<string, string> = {
    amazon: 'success',
    ebay: '',
    shopee: 'warning',
    lazada: 'info'
  }
  return map[platform] || ''
}

// 客户等级类型
const getLevelType = (level: string) => {
  const map: Record<string, string> = {
    VIP: 'danger',
    NORMAL: 'success',
    NEW: 'info'
  }
  return map[level] || ''
}

// 格式化金额
const formatAmount = (amount: number) => {
  return amount ? amount.toFixed(2) : '0.00'
}

// 格式化时间
const formatTime = (time: string) => {
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

// ========== 标签页状态 ==========
const activeTab = ref('customers')

// ---------- 跟进记录 ----------
const followLoading = ref(false)
const followList = ref<CustomerFollowRecord[]>([])
const followSearch = reactive({ result: '', followType: '' })
const followPagination = reactive({ current: 1, size: 10, total: 0 })
const followFormVisible = ref(false)
const followFormRef = ref<FormInstance>()
const followForm = reactive({
  customerId: undefined as number | undefined,
  followType: 'PHONE',
  content: '',
  followTime: '',
  nextFollowTime: '',
  result: 'PENDING',
  followUserName: 'admin',
  remark: ''
})

const loadFollowList = async () => {
  followLoading.value = true
  try {
    const res = await customerFollowApi.list({
      current: followPagination.current,
      size: followPagination.size,
      result: followSearch.result || undefined,
      followType: followSearch.followType || undefined
    })
    const pageData = unwrapPage(res)
    followList.value = pageData.records
    followPagination.total = pageData.total
  } catch (error) {
    console.error('加载跟进记录失败', error)
  } finally {
    followLoading.value = false
  }
}

const loadFollowPendingToday = async () => {
  followLoading.value = true
  try {
    const res = await customerFollowApi.pendingToday()
    const rows = unwrap<CustomerFollowRecord[]>(res)
    followList.value = Array.isArray(rows) ? rows : []
    followPagination.total = followList.value.length
  } catch (error) {
    console.error('加载今日待跟进失败', error)
  } finally {
    followLoading.value = false
  }
}

const handleFollowSearch = () => {
  followPagination.current = 1
  loadFollowList()
}

const handleAddFollow = () => {
  followForm.customerId = undefined
  followForm.followType = 'PHONE'
  followForm.content = ''
  followForm.followTime = ''
  followForm.nextFollowTime = ''
  followForm.result = 'PENDING'
  followForm.remark = ''
  followFormVisible.value = true
}

const handleSubmitFollow = async () => {
  if (!followFormRef.value) return
  await followFormRef.value.validate()
  if (!followForm.customerId) {
    ElMessage.warning('请选择客户ID')
    return
  }
  try {
    await customerFollowApi.create({ ...followForm })
    ElMessage.success('跟进记录已创建')
    followFormVisible.value = false
    loadFollowList()
  } catch (error) {
    console.error('创建跟进记录失败', error)
  }
}

const handleCompleteFollow = async (row: CustomerFollowRecord) => {
  try {
    await customerFollowApi.complete(row.id, 'SUCCESS')
    ElMessage.success('已标记完成')
    loadFollowList()
  } catch (error) {
    console.error('标记跟进完成失败', error)
  }
}

const handleDeleteFollow = async (row: CustomerFollowRecord) => {
  try {
    await ElMessageBox.confirm(`确认删除客户 ${row.customerId} 的这条跟进记录？`, '提示', { type: 'warning' })
    await customerFollowApi.remove(row.id)
    ElMessage.success('已删除')
    loadFollowList()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') console.error('删除跟进记录失败', error)
  }
}

const getFollowTypeName = (type: string) =>
  ({ PHONE: '电话', EMAIL: '邮件', WECHAT: '微信', VISIT: '拜访', OTHER: '其他' }[type] || type)

const getFollowResultName = (result: string) =>
  ({ SUCCESS: '已完成', PENDING: '待跟进', FAILED: '失败' }[result] || result)

const getFollowResultType = (result: string) =>
  ({ SUCCESS: 'success', PENDING: 'warning', FAILED: 'danger' }[result] || 'info')

// ---------- 客户分组 ----------
const groupLoading = ref(false)
const groupList = ref<CustomerGroup[]>([])
const groupKeyword = ref('')
const groupFormVisible = ref(false)
const groupEditId = ref<number | null>(null)
const groupForm = reactive({ name: '', description: '', sortOrder: 0, status: 'ACTIVE' })

const loadGroupList = async () => {
  groupLoading.value = true
  try {
    const res = await customerGroupApi.list(groupKeyword.value || undefined)
    const rows = unwrap<CustomerGroup[]>(res)
    groupList.value = Array.isArray(rows) ? rows : []
  } catch (error) {
    console.error('加载客户分组失败', error)
  } finally {
    groupLoading.value = false
  }
}

const handleAddGroup = () => {
  groupEditId.value = null
  groupForm.name = ''
  groupForm.description = ''
  groupForm.sortOrder = 0
  groupForm.status = 'ACTIVE'
  groupFormVisible.value = true
}

const handleEditGroup = (row: CustomerGroup) => {
  groupEditId.value = row.id
  groupForm.name = row.name
  groupForm.description = row.description
  groupForm.sortOrder = row.sortOrder
  groupForm.status = row.status
  groupFormVisible.value = true
}

const handleSubmitGroup = async () => {
  if (!groupForm.name) {
    ElMessage.warning('请输入分组名称')
    return
  }
  try {
    if (groupEditId.value) {
      await customerGroupApi.update(groupEditId.value, { ...groupForm })
    } else {
      await customerGroupApi.create({ ...groupForm })
    }
    ElMessage.success(groupEditId.value ? '分组已更新' : '分组已创建')
    groupFormVisible.value = false
    loadGroupList()
  } catch (error) {
    console.error('保存分组失败', error)
  }
}

const handleDeleteGroup = async (row: CustomerGroup) => {
  try {
    await ElMessageBox.confirm(`确认删除分组「${row.name}」？`, '提示', { type: 'warning' })
    await customerGroupApi.remove(row.id)
    ElMessage.success('已删除')
    loadGroupList()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') console.error('删除分组失败', error)
  }
}

// ---------- 客户标签 ----------
const tagLoading = ref(false)
const tagList = ref<CustomerTag[]>([])
const tagKeyword = ref('')
const tagFormVisible = ref(false)
const tagEditId = ref<number | null>(null)
const tagForm = reactive({ name: '', color: '#409EFF', description: '', sortOrder: 0, status: 'ACTIVE' })

const loadTagList = async () => {
  tagLoading.value = true
  try {
    const res = await customerTagApi.list(tagKeyword.value || undefined)
    const rows = unwrap<CustomerTag[]>(res)
    tagList.value = Array.isArray(rows) ? rows : []
  } catch (error) {
    console.error('加载客户标签失败', error)
  } finally {
    tagLoading.value = false
  }
}

const handleAddTag = () => {
  tagEditId.value = null
  tagForm.name = ''
  tagForm.color = '#409EFF'
  tagForm.description = ''
  tagForm.sortOrder = 0
  tagForm.status = 'ACTIVE'
  tagFormVisible.value = true
}

const handleEditTag = (row: CustomerTag) => {
  tagEditId.value = row.id
  tagForm.name = row.name
  tagForm.color = row.color
  tagForm.description = row.description
  tagForm.sortOrder = row.sortOrder
  tagForm.status = row.status
  tagFormVisible.value = true
}

const handleSubmitTag = async () => {
  if (!tagForm.name) {
    ElMessage.warning('请输入标签名称')
    return
  }
  try {
    if (tagEditId.value) {
      await customerTagApi.update(tagEditId.value, { ...tagForm })
    } else {
      await customerTagApi.create({ ...tagForm })
    }
    ElMessage.success(tagEditId.value ? '标签已更新' : '标签已创建')
    tagFormVisible.value = false
    loadTagList()
  } catch (error) {
    console.error('保存标签失败', error)
  }
}

const handleDeleteTag = async (row: CustomerTag) => {
  try {
    await ElMessageBox.confirm(`确认删除标签「${row.name}」？`, '提示', { type: 'warning' })
    await customerTagApi.remove(row.id)
    ElMessage.success('已删除')
    loadTagList()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') console.error('删除标签失败', error)
  }
}

// 切换标签页时才加载对应数据（避免一进页面就打 4 个接口）
const handleTabChange = (name: string | number) => {
  if (name === 'follow') loadFollowList()
  else if (name === 'groups') loadGroupList()
  else if (name === 'tags') loadTagList()
}

onMounted(() => {
  loadCustomers()
  loadStats()
})
</script>

<style scoped>
.customer-page {
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
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}
</style>