<template>
  <div class="supplier-page">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon" style="background: #409eff">
            <el-icon :size="24"><OfficeBuilding /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.totalSuppliers }}</div>
            <div class="stat-label">供应商总数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon" style="background: #67c23a">
            <el-icon :size="24"><CircleCheck /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.activeSuppliers }}</div>
            <div class="stat-label">活跃供应商</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon" style="background: #e6a23c">
            <el-icon :size="24"><Star /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.avgRating.toFixed(1) }}</div>
            <div class="stat-label">平均评分</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-icon" style="background: #f56c6c">
            <el-icon :size="24"><Money /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">${{ formatAmount(stats.totalPurchaseAmount) }}</div>
            <div class="stat-label">累计采购</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Tab切换 -->
    <el-tabs v-model="activeTab">
      <!-- 供应商列表 -->
      <el-tab-pane label="供应商管理" name="supplier">
        <!-- 搜索筛选 -->
        <el-card class="search-card" shadow="never">
          <el-form :inline="true" :model="searchForm">
            <el-form-item label="关键词">
              <el-input v-model="searchForm.keyword" placeholder="供应商名称/联系人" clearable style="width: 150px" />
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="searchForm.type" placeholder="全部类型" clearable style="width: 120px">
                <el-option label="工厂" value="FACTORY" />
                <el-option label="贸易商" value="TRADER" />
                <el-option label="代理商" value="AGENT" />
              </el-select>
            </el-form-item>
            <el-form-item label="等级">
              <el-select v-model="searchForm.level" placeholder="全部等级" clearable style="width: 100px">
                <el-option label="A级" value="A" />
                <el-option label="B级" value="B" />
                <el-option label="C级" value="C" />
              </el-select>
            </el-form-item>
            <el-form-item label="国家">
              <el-input v-model="searchForm.country" placeholder="国家名称" clearable style="width: 120px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
              <el-button :icon="Refresh" @click="handleReset">重置</el-button>
              <el-button :icon="Trophy" @click="loadTopSuppliers">头部供应商</el-button>
              <el-button type="success" :icon="Plus" @click="handleAddSupplier">新增供应商</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 供应商列表 -->
        <el-card class="table-card" shadow="never">
          <el-table v-loading="loading" :data="supplierList" stripe>
            <el-table-column prop="name" label="供应商名称" width="180" fixed>
              <template #default="{ row }">
                <el-link type="primary" @click="handleViewSupplier(row)">{{ row.name }}</el-link>
              </template>
            </el-table-column>
            <el-table-column prop="contactPerson" label="联系人" width="100" />
            <el-table-column prop="phone" label="电话" width="130" />
            <el-table-column prop="email" label="邮箱" width="180" show-overflow-tooltip />
            <el-table-column prop="type" label="类型" width="90">
              <template #default="{ row }">
                <el-tag size="small">{{ getTypeName(row.type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="level" label="等级" width="70">
              <template #default="{ row }">
                <el-tag :type="getLevelType(row.level)" size="small">{{ row.level }}级</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="rating" label="评分" width="80">
              <template #default="{ row }">
                <el-rate v-model="row.rating" disabled :max="5" style="display: inline-block" />
              </template>
            </el-table-column>
            <el-table-column prop="totalOrders" label="采购次数" width="90" />
            <el-table-column prop="totalPurchaseAmount" label="累计采购" width="120">
              <template #default="{ row }">
                ${{ formatAmount(row.totalPurchaseAmount) }}
              </template>
            </el-table-column>
            <el-table-column prop="country" label="国家" width="100" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleViewSupplier(row)">详情</el-button>
                <el-button link type="primary" size="small" @click="handleEditSupplier(row)">编辑</el-button>
                <el-button link type="success" size="small" @click="handleCreatePurchase(row)">采购</el-button>
                <el-button link type="danger" size="small" @click="removeSupplier(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="pagination.current"
            v-model:page-size="pagination.size"
            :total="pagination.total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadSuppliers"
            @current-change="loadSuppliers"
            style="margin-top: 20px; justify-content: flex-end"
          />
        </el-card>
      </el-tab-pane>

      <!-- 采购订单 -->
      <el-tab-pane label="采购订单" name="purchase">
        <el-card class="search-card" shadow="never">
          <el-form :inline="true" :model="purchaseSearchForm">
            <el-form-item label="供应商">
              <el-select v-model="purchaseSearchForm.supplierId" placeholder="全部供应商" clearable style="width: 180px">
                <el-option v-for="s in supplierList" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="订单状态">
              <el-select v-model="purchaseSearchForm.status" placeholder="全部状态" clearable style="width: 130px">
                <el-option label="待审批" value="PENDING" />
                <el-option label="已审批" value="APPROVED" />
                <el-option label="采购中" value="PURCHASING" />
                <el-option label="运输中" value="IN_TRANSIT" />
                <el-option label="已收货" value="RECEIVED" />
                <el-option label="已取消" value="CANCELLED" />
              </el-select>
            </el-form-item>
            <el-form-item label="付款状态">
              <el-select v-model="purchaseSearchForm.paymentStatus" placeholder="全部" clearable style="width: 120px">
                <el-option label="未付款" value="UNPAID" />
                <el-option label="部分付款" value="PARTIAL" />
                <el-option label="已付款" value="PAID" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadPurchaseOrders">搜索</el-button>
              <el-button :icon="Van" @click="loadInTransitOrders">在途采购</el-button>
              <el-button :icon="DataAnalysis" @click="loadPurchaseStats">采购统计</el-button>
              <el-button type="success" :icon="Plus" @click="openPurchaseDialog()">新建采购单</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="table-card" shadow="never">
          <el-table v-loading="purchaseLoading" :data="purchaseOrderList" stripe>
            <el-table-column prop="orderNo" label="采购单号" width="160" fixed>
              <template #default="{ row }">
                <el-link type="primary">{{ row.orderNo }}</el-link>
              </template>
            </el-table-column>
            <el-table-column prop="supplierName" label="供应商" width="150" />
            <el-table-column prop="totalAmount" label="订单金额" width="120">
              <template #default="{ row }">
                {{ row.totalAmount }} {{ row.currency }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="订单状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getPurchaseStatusType(row.status)" size="small">{{ getPurchaseStatusName(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="paymentStatus" label="付款状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getPaymentStatusType(row.paymentStatus)" size="small">{{ getPaymentStatusName(row.paymentStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="expectedArrivalAt" label="预计到货" width="150">
              <template #default="{ row }">
                {{ row.expectedArrivalAt ? formatTime(row.expectedArrivalAt) : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="purchaserName" label="采购员" width="100" />
            <el-table-column prop="createTime" label="创建时间" width="150">
              <template #default="{ row }">
                {{ formatTime(row.createTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="viewPurchaseOrder(row)">详情</el-button>
                <el-button link type="success" size="small" v-if="row.status === 'PENDING'" @click="advancePurchase(row, 'approve')">审批</el-button>
                <el-button link type="warning" size="small" v-if="row.status === 'APPROVED'" @click="advancePurchase(row, 'purchasing')">采购中</el-button>
                <el-button link type="success" size="small" v-if="row.status === 'PURCHASING' || row.status === 'IN_TRANSIT'" @click="advancePurchase(row, 'received')">收货</el-button>
                <el-button link type="danger" size="small" v-if="!['RECEIVED', 'CANCELLED'].includes(row.status)" @click="advancePurchase(row, 'cancel')">取消</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="purchasePagination.current"
            v-model:page-size="purchasePagination.size"
            :total="purchasePagination.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadPurchaseOrders"
            @current-change="loadPurchaseOrders"
            style="margin-top: 20px; justify-content: flex-end"
          />
        </el-card>
      </el-tab-pane>

      <!-- 供应商绩效 -->
      <el-tab-pane label="供应商绩效" name="evaluation">
        <el-card class="search-card" shadow="never">
          <el-form :inline="true">
            <el-form-item label="选择供应商">
              <el-select v-model="evalSupplierId" placeholder="请选择供应商" clearable style="width: 200px" @change="loadEvaluations">
                <el-option v-for="s in supplierList" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="评估结论">
              <el-select v-model="evalConclusion" placeholder="全部" clearable style="width: 140px" @change="loadEvaluations">
                <el-option label="优秀" value="EXCELLENT" />
                <el-option label="良好" value="GOOD" />
                <el-option label="合格" value="ACCEPTABLE" />
                <el-option label="不合格" value="POOR" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="loadEvaluations">查询</el-button>
              <el-button type="success" :icon="Plus" :disabled="!evalSupplierId" @click="openEvalDialog()">新增评估</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 最新一次评估的评分卡 -->
        <el-row :gutter="20" v-if="latestEvaluation">
          <el-col :span="6">
            <el-card shadow="never">
              <div class="eval-score">
                <div class="score-label">质量评分</div>
                <div class="score-value">{{ latestEvaluation.qualityScore ?? '-' }}</div>
                <el-progress :percentage="(latestEvaluation.qualityScore || 0) * 20" :stroke-width="10" />
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never">
              <div class="eval-score">
                <div class="score-label">交货评分</div>
                <div class="score-value">{{ latestEvaluation.deliveryScore ?? '-' }}</div>
                <el-progress :percentage="(latestEvaluation.deliveryScore || 0) * 20" :stroke-width="10" status="success" />
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never">
              <div class="eval-score">
                <div class="score-label">价格评分</div>
                <div class="score-value">{{ latestEvaluation.priceScore ?? '-' }}</div>
                <el-progress :percentage="(latestEvaluation.priceScore || 0) * 20" :stroke-width="10" status="warning" />
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never">
              <div class="eval-score">
                <div class="score-label">综合得分</div>
                <div class="score-value">{{ latestEvaluation.totalScore ?? '-' }}</div>
                <el-progress :percentage="(latestEvaluation.totalScore || 0) * 20" :stroke-width="10" type="success" />
              </div>
            </el-card>
          </el-col>
        </el-row>

        <el-empty v-else-if="evalSupplierId" description="该供应商还没有绩效评估记录" style="padding: 24px" />

        <el-card class="table-card" shadow="never" v-if="evalSupplierId">
          <el-table v-loading="evalLoading" :data="evaluationList" stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="qualityScore" label="质量" width="70" />
            <el-table-column prop="deliveryScore" label="交货" width="70" />
            <el-table-column prop="priceScore" label="价格" width="70" />
            <el-table-column prop="serviceScore" label="服务" width="70" />
            <el-table-column prop="techScore" label="技术" width="70" />
            <el-table-column prop="totalScore" label="综合" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="row.totalScore >= 80 ? 'success' : (row.totalScore >= 60 ? 'warning' : 'danger')">
                  {{ row.totalScore }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="conclusion" label="结论" width="90" />
            <el-table-column prop="evaluatorName" label="评估人" width="100" />
            <el-table-column prop="evaluateAt" label="评估时间" width="160">
              <template #default="{ row }">{{ formatTime(row.evaluateAt) }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="viewEvaluation(row)">详情</el-button>
                <el-button link type="danger" size="small" @click="removeEvaluation(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 供应商详情弹窗 -->
    <el-dialog v-model="supplierDetailVisible" title="供应商详情" width="800px" append-to-body>
      <el-descriptions :column="2" border v-if="currentSupplier">
        <el-descriptions-item label="供应商名称">{{ currentSupplier.name }}</el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag>{{ getTypeName(currentSupplier.type) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="联系人">{{ currentSupplier.contactPerson }}</el-descriptions-item>
        <el-descriptions-item label="电话">{{ currentSupplier.phone }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ currentSupplier.email }}</el-descriptions-item>
        <el-descriptions-item label="等级">
          <el-tag :type="getLevelType(currentSupplier.level)">{{ currentSupplier.level }}级</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="国家">{{ currentSupplier.country }}</el-descriptions-item>
        <el-descriptions-item label="城市">{{ currentSupplier.city || '-' }}</el-descriptions-item>
        <el-descriptions-item label="详细地址" :span="2">{{ currentSupplier.address || '-' }}</el-descriptions-item>
        <el-descriptions-item label="主要产品">{{ currentSupplier.category || '-' }}</el-descriptions-item>
        <el-descriptions-item label="合作开始">{{ currentSupplier.cooperationStartAt ? formatTime(currentSupplier.cooperationStartAt) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="采购次数">{{ currentSupplier.totalOrders }}</el-descriptions-item>
        <el-descriptions-item label="累计采购">${{ formatAmount(currentSupplier.totalPurchaseAmount) }}</el-descriptions-item>
        <el-descriptions-item label="评分">
          <el-rate v-model="currentSupplier.rating" disabled :max="5" />
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ currentSupplier.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <template #footer>
        <el-button @click="supplierDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 新增供应商评估弹窗 -->
    <el-dialog v-model="evalDialogVisible" title="新增绩效评估" width="560px" append-to-body>
      <el-form :model="evalForm" label-width="90px">
        <el-form-item label="质量评分">
          <el-rate v-model="evalForm.qualityScore" :max="5" show-score @change="syncTotalScore" />
        </el-form-item>
        <el-form-item label="交货评分">
          <el-rate v-model="evalForm.deliveryScore" :max="5" show-score @change="syncTotalScore" />
        </el-form-item>
        <el-form-item label="价格评分">
          <el-rate v-model="evalForm.priceScore" :max="5" show-score @change="syncTotalScore" />
        </el-form-item>
        <el-form-item label="服务评分">
          <el-rate v-model="evalForm.serviceScore" :max="5" show-score @change="syncTotalScore" />
        </el-form-item>
        <el-form-item label="技术评分">
          <el-rate v-model="evalForm.techScore" :max="5" show-score @change="syncTotalScore" />
        </el-form-item>
        <el-form-item label="综合得分">
          <el-input-number v-model="evalForm.totalScore" :min="0" :max="5" :step="0.05" :precision="2" disabled />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">按后端权重自动计算</span>
        </el-form-item>
        <el-form-item label="评估结论">
          <el-input value="保存时由后端按综合得分自动判定" disabled />
        </el-form-item>
        <el-form-item label="评估人">
          <el-input v-model="evalForm.evaluatorName" placeholder="如：采购部 张三" />
        </el-form-item>
        <el-form-item label="优势">
          <el-input v-model="evalForm.advantages" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="改进建议">
          <el-input v-model="evalForm.improvements" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="evalForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="evalDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="evalDialogSaving" @click="saveEvaluation">保存</el-button>
      </template>
    </el-dialog>

    <!-- 评估详情弹窗 -->
    <el-dialog v-model="evalDetailVisible" title="评估详情" width="640px" append-to-body>
      <el-descriptions :column="2" border v-if="currentEvaluation">
        <el-descriptions-item label="质量评分">{{ currentEvaluation.qualityScore ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="交货评分">{{ currentEvaluation.deliveryScore ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="价格评分">{{ currentEvaluation.priceScore ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="服务评分">{{ currentEvaluation.serviceScore ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="技术评分">{{ currentEvaluation.techScore ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="综合得分">{{ currentEvaluation.totalScore ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="评估结论">{{ currentEvaluation.conclusion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="评估人">{{ currentEvaluation.evaluatorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="评估时间">{{ formatTime(currentEvaluation.evaluateAt) }}</el-descriptions-item>
        <el-descriptions-item label="供应商 ID">{{ currentEvaluation.supplierId }}</el-descriptions-item>
        <el-descriptions-item label="优势" :span="2">{{ currentEvaluation.advantages || '-' }}</el-descriptions-item>
        <el-descriptions-item label="改进建议" :span="2">{{ currentEvaluation.improvements || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ currentEvaluation.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="evalDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 采购单详情弹窗 -->
    <el-dialog v-model="purchaseDetailVisible" title="采购单详情" width="800px" append-to-body>
      <el-descriptions :column="2" border v-if="currentPurchaseOrder">
        <el-descriptions-item label="采购单号">{{ currentPurchaseOrder.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ currentPurchaseOrder.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="订单金额">
          {{ formatAmount(currentPurchaseOrder.totalAmount) }} {{ currentPurchaseOrder.currency }}
        </el-descriptions-item>
        <el-descriptions-item label="已付金额">{{ formatAmount(currentPurchaseOrder.paidAmount) }}</el-descriptions-item>
        <el-descriptions-item label="订单状态">{{ getPurchaseStatusName(currentPurchaseOrder.status) }}</el-descriptions-item>
        <el-descriptions-item label="付款状态">{{ getPaymentStatusName(currentPurchaseOrder.paymentStatus) }}</el-descriptions-item>
        <el-descriptions-item label="预计到货">{{ formatTime(currentPurchaseOrder.expectedArrivalAt) }}</el-descriptions-item>
        <el-descriptions-item label="实际到货">{{ formatTime(currentPurchaseOrder.actualArrivalAt) }}</el-descriptions-item>
        <el-descriptions-item label="采购员">{{ currentPurchaseOrder.purchaserName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="付款方式">{{ currentPurchaseOrder.paymentMethod || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ currentPurchaseOrder.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <h4 v-if="purchaseOrderItems.length">明细</h4>
      <el-table v-if="purchaseOrderItems.length" :data="purchaseOrderItems" size="small" border>
        <el-table-column prop="productName" label="商品" min-width="160" />
        <el-table-column prop="sku" label="SKU" width="140" />
        <el-table-column prop="quantity" label="数量" width="80" />
        <el-table-column prop="unitPrice" label="单价" width="100" />
        <el-table-column prop="subtotal" label="小计" width="110" />
        <el-table-column prop="receivedQuantity" label="已收" width="80" />
      </el-table>
      <el-empty v-else description="没有明细" style="padding: 16px" />

      <template #footer>
        <el-button @click="purchaseDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑供应商弹窗 -->
    <el-dialog v-model="supplierFormVisible" :title="isEditSupplier ? '编辑供应商' : '新增供应商'" width="600px" append-to-body>
      <el-form :model="supplierForm" :rules="supplierFormRules" ref="supplierFormRef" label-width="100px">
        <el-form-item label="供应商名称" prop="name">
          <el-input v-model="supplierForm.name" placeholder="请输入供应商名称" />
        </el-form-item>
        <el-form-item label="联系人" prop="contactPerson">
          <el-input v-model="supplierForm.contactPerson" placeholder="请输入联系人" />
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="supplierForm.phone" placeholder="请输入电话" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="supplierForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="supplierForm.type" placeholder="请选择类型" style="width: 100%">
            <el-option label="工厂" value="FACTORY" />
            <el-option label="贸易商" value="TRADER" />
            <el-option label="代理商" value="AGENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="supplierForm.level" placeholder="请选择等级" style="width: 100%">
            <el-option label="A级" value="A" />
            <el-option label="B级" value="B" />
            <el-option label="C级" value="C" />
          </el-select>
        </el-form-item>
        <el-form-item label="国家">
          <el-input v-model="supplierForm.country" placeholder="请输入国家" />
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="supplierForm.city" placeholder="请输入城市" />
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model="supplierForm.address" type="textarea" :rows="2" placeholder="请输入详细地址" />
        </el-form-item>
        <el-form-item label="主要产品">
          <el-input v-model="supplierForm.category" placeholder="请输入主要产品类别" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="supplierForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="supplierFormVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitSupplier">确定</el-button>
      </template>
    </el-dialog>

    <!-- 新建采购单弹窗 -->
    <el-dialog v-model="showPurchaseDialog" title="新建采购单" width="700px" append-to-body>
      <el-form :model="purchaseForm" label-width="100px">
        <el-form-item label="供应商" required>
          <el-select v-model="purchaseForm.supplierId" placeholder="请选择供应商" filterable style="width: 100%">
            <el-option v-for="s in purchaseSupplierOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="预计到货">
          <el-date-picker v-model="purchaseForm.expectedArrivalAt" type="date" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="付款方式">
          <el-select v-model="purchaseForm.paymentMethod" placeholder="请选择付款方式" style="width: 100%">
            <el-option label="TT电汇" value="TT" />
            <el-option label="信用证" value="LC" />
            <el-option label="付款交单" value="DP" />
            <el-option label="承兑交单" value="DA" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="purchaseForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showPurchaseDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreatePurchaseOrder">创建采购单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, OfficeBuilding, CircleCheck, Star, Money, Trophy, Van, DataAnalysis } from '@element-plus/icons-vue'
import {
  supplierApi,
  type Supplier,
  type PurchaseOrder,
  type PurchaseOrderItem,
  type SupplierStats,
  type SupplierEvaluation,
} from '@/api/supplier'
import dayjs from 'dayjs'
import { unwrapPage } from '@/utils/response'

const activeTab = ref('supplier')
const loading = ref(false)
const purchaseLoading = ref(false)

// 统计数据
const stats = reactive<SupplierStats>({
  totalSuppliers: 0,
  activeSuppliers: 0,
  avgRating: 0,
  totalPurchaseAmount: 0
})

// 供应商列表
const supplierList = ref<Supplier[]>([])
const supplierDetailVisible = ref(false)
const supplierFormVisible = ref(false)
const isEditSupplier = ref(false)
const currentSupplier = ref<Supplier | null>(null)

// 搜索表单
const searchForm = reactive({
  keyword: '',
  type: '',
  level: '',
  country: ''
})

// 分页
const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

// 供应商表单
const supplierForm = reactive<Supplier>({
  id: '',
  name: '',
  contactPerson: '',
  phone: '',
  email: '',
  type: 'TRADER',
  country: '',
  city: '',
  address: '',
  category: '',
  cooperationStartAt: '',
  totalPurchaseAmount: 0,
  totalOrders: 0,
  rating: 5,
  level: 'B',
  remark: '',
  status: 'ACTIVE',
  createTime: '',
  updateTime: ''
})

const supplierFormRules = {
  name: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }],
  contactPerson: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

// 采购订单
const purchaseOrderList = ref<PurchaseOrder[]>([])
const purchaseSearchForm = reactive({
  supplierId: undefined as string | undefined,
  status: '',
  paymentStatus: ''
})
const purchasePagination = reactive({
  current: 1,
  size: 20,
  total: 0
})
const showPurchaseDialog = ref(false)
const purchaseForm = reactive({
  supplierId: '',
  expectedArrivalAt: '',
  paymentMethod: 'TT',
  remark: ''
})

// 绩效评估
const evalSupplierId = ref<string | null>(null)
const evalConclusion = ref<string>('')
const evaluationList = ref<SupplierEvaluation[]>([])
const latestEvaluation = ref<SupplierEvaluation | null>(null)
const evalLoading = ref(false)

// 新增/查看评估的弹窗
const evalDialogVisible = ref(false)
const evalDialogSaving = ref(false)
const evalForm = reactive({
  id: '',
  supplierId: '',
  qualityScore: 5,
  deliveryScore: 5,
  priceScore: 5,
  serviceScore: 5,
  techScore: 5,
  totalScore: 5,
  conclusion: 'GOOD',
  advantages: '',
  improvements: '',
  remark: '',
  evaluatorName: '',
})

// 评估详情弹窗
const evalDetailVisible = ref(false)
const currentEvaluation = ref<SupplierEvaluation | null>(null)

// 采购单详情弹窗
const purchaseDetailVisible = ref(false)
const currentPurchaseOrder = ref<PurchaseOrder | null>(null)
const purchaseOrderItems = ref<PurchaseOrderItem[]>([])

// 采购单下拉用的供应商选项（后端 /purchase/suppliers 返回**全部**供应商，
// 而 supplierList 只是当前分页的数据 —— 用它做下拉会漏掉其它页的供应商）
const purchaseSupplierOptions = ref<Array<{ id: string; name: string; code: string }>>([])

const loadPurchaseSupplierOptions = async () => {
  purchaseSupplierOptions.value = (await supplierApi.getPurchaseSupplierOptions()) || []
}

/** 加载某个供应商的评估记录 + 最新一次评估 */
const loadEvaluations = async () => {
  if (!evalSupplierId.value) {
    evaluationList.value = []
    latestEvaluation.value = null
    return
  }
  evalLoading.value = true
  try {
    const params: { supplierId?: string; conclusion?: string } = { supplierId: evalSupplierId.value }
    if (evalConclusion.value) params.conclusion = evalConclusion.value
    evaluationList.value = (await supplierApi.getEvaluationList(params)) || []

    // 最新一次评估：后端返回**一条记录**（不是聚合统计对象）
    const latest = await supplierApi.getLatestEvaluation(evalSupplierId.value)
    latestEvaluation.value = latest || null
  } catch (error) {
    console.error('加载评估记录失败', error)
    evaluationList.value = []
    latestEvaluation.value = null
  } finally {
    evalLoading.value = false
  }
}

const openEvalDialog = () => {
  if (!evalSupplierId.value) {
    ElMessage.warning('请先选择供应商')
    return
  }
  evalForm.id = ''
  evalForm.supplierId = evalSupplierId.value
  evalForm.qualityScore = 5
  evalForm.deliveryScore = 5
  evalForm.priceScore = 5
  evalForm.serviceScore = 5
  evalForm.techScore = 5
  evalForm.totalScore = 5
  evalForm.conclusion = ''
  evalForm.advantages = ''
  evalForm.improvements = ''
  evalForm.remark = ''
  evalForm.evaluatorName = ''
  evalDialogVisible.value = true
}

/**
 * 综合得分 —— 必须和后端 SupplierEvaluationService.calculateTotalScore 一样，
 * 否则页面显示的分和库里存的对不上。
 *
 * 📌 后端算法：质量30% + 交付25% + 价格20% + 服务15% + 技术10%
 * ⚠️ 还有一点：**综合得分和评估结论都是后端算的**，
 *    前端传的 totalScore / conclusion / evaluateAt 会被后端覆盖。
 */
const syncTotalScore = () => {
  const q = evalForm.qualityScore || 0
  const d = evalForm.deliveryScore || 0
  const p = evalForm.priceScore || 0
  const s = evalForm.serviceScore || 0
  const t = evalForm.techScore || 0
  evalForm.totalScore = Math.round((q * 0.3 + d * 0.25 + p * 0.2 + s * 0.15 + t * 0.1) * 100) / 100
}

const saveEvaluation = async () => {
  evalDialogSaving.value = true
  try {
    // 📌 不传 totalScore / conclusion / evaluateAt ——
    //    这三个都是后端算的（加权平均 + 按分数推导结论 + 服务器当前时间），
    //    传了也会被覆盖，传错格式（如 '2026-11-03 12:00:00'）反而会 400。
    await supplierApi.createEvaluation({
      supplierId: evalForm.supplierId,
      qualityScore: evalForm.qualityScore,
      deliveryScore: evalForm.deliveryScore,
      priceScore: evalForm.priceScore,
      serviceScore: evalForm.serviceScore,
      techScore: evalForm.techScore,
      advantages: evalForm.advantages,
      improvements: evalForm.improvements,
      remark: evalForm.remark,
      evaluatorName: evalForm.evaluatorName,
    } as SupplierEvaluation)
    ElMessage.success('评估已保存')
    evalDialogVisible.value = false
    loadEvaluations()
  } catch (error) {
    console.error('保存评估失败', error)
  } finally {
    evalDialogSaving.value = false
  }
}

const viewEvaluation = async (row: SupplierEvaluation) => {
  // 📌 真去后端拉详情，而不是把列表行对象直接塞进弹窗
  try {
    currentEvaluation.value = await supplierApi.getEvaluationDetail(row.id)
  } catch (error) {
    console.error('加载评估详情失败', error)
    currentEvaluation.value = row
  }
  evalDetailVisible.value = true
}

const removeEvaluation = async (row: SupplierEvaluation) => {
  await ElMessageBox.confirm(`确定删除评估记录 #${row.id} 吗？`, '提示', { type: 'warning' })
  await supplierApi.deleteEvaluation(row.id)
  ElMessage.success('已删除')
  loadEvaluations()
}

/** 采购单详情（含明细） */
const viewPurchaseOrder = async (row: PurchaseOrder) => {
  const detail = await supplierApi.getPurchaseOrderById(row.id)
  currentPurchaseOrder.value = detail
  purchaseOrderItems.value = (await supplierApi.getPurchaseOrderItems(row.id)) || []
  purchaseDetailVisible.value = true
}

/** 采购单状态流转 */
const advancePurchase = async (row: PurchaseOrder, action: 'approve' | 'purchasing' | 'received' | 'cancel') => {
  if (action === 'cancel') {
    const { value } = await ElMessageBox.prompt('请输入取消原因', '取消采购单', {
      inputPlaceholder: '如：供应商缺货',
      inputValidator: v => (v && v.trim() ? true : '取消原因必填'),
    })
    await supplierApi.cancelPurchaseOrder(row.id, value)
  } else if (action === 'approve') {
    await supplierApi.approvePurchaseOrder(row.id)
  } else if (action === 'purchasing') {
    await supplierApi.markPurchaseOrderPurchasing(row.id)
  } else {
    await supplierApi.markPurchaseOrderReceived(row.id)
  }
  ElMessage.success('操作成功')
  loadPurchaseOrders()
}

/** 删除供应商 */
const removeSupplier = async (row: Supplier) => {
  await ElMessageBox.confirm(
    `确定删除供应商「${row.name}」吗？\n（如果该供应商还有采购单，会被后端拒绝）`,
    '提示',
    { type: 'warning' },
  )
  await supplierApi.deleteSupplier(row.id)
  ElMessage.success('已删除')
  loadSuppliers()
}

/** 头部供应商（后端按采购额排序） */
const loadTopSuppliers = async () => {
  const rows = (await supplierApi.getTopSuppliers()) || []
  supplierList.value = rows
  ElMessage.success(`已加载头部供应商（${rows.length} 家）`)
}

/** 在途（待收货）采购订单 */
const loadInTransitOrders = async () => {
  const rows = (await supplierApi.getInTransitOrders()) || []
  purchaseOrderList.value = rows
  purchasePagination.total = rows.length
  ElMessage.success(`在途采购订单 ${rows.length} 条`)
}

/** 采购订单统计 */
const loadPurchaseStats = async () => {
  const stats = await supplierApi.getPurchaseStats()
  ElMessageBox.alert(
    `采购单总数：${stats.total}\n待审批：${stats.pending}\n在途：${stats.inTransit}\n` +
    `已收货：${stats.received}\n订单总额：${stats.totalAmount}\n已付金额：${stats.totalPaid}`,
    '采购订单统计',
  )
}

// 加载供应商列表
const loadSuppliers = async () => {
  loading.value = true
  try {
    const res = await supplierApi.getSupplierList({
      current: pagination.current,
      size: pagination.size,
      keyword: searchForm.keyword || undefined,
      type: searchForm.type || undefined,
      level: searchForm.level || undefined,
      country: searchForm.country || undefined
    })
    const pageData = unwrapPage(res)
    supplierList.value = pageData.records || []
    pagination.total = pageData.total || 0
  } catch (error) {
    console.error('加载供应商失败', error)
  } finally {
    loading.value = false
  }
}

// 加载统计数据
const loadStats = async () => {
  try {
    const res = await supplierApi.getSupplierStats()
    Object.assign(stats, res)
  } catch (error) {
    console.error('加载统计失败', error)
  }
}

// 搜索
const handleSearch = () => {
  pagination.current = 1
  loadSuppliers()
}

// 重置
const handleReset = () => {
  searchForm.keyword = ''
  searchForm.type = ''
  searchForm.level = ''
  searchForm.country = ''
  handleSearch()
}

// 新增供应商
const handleAddSupplier = () => {
  isEditSupplier.value = false
  resetSupplierForm()
  supplierFormVisible.value = true
}

// 编辑供应商
const handleEditSupplier = (supplier: Supplier) => {
  isEditSupplier.value = true
  Object.assign(supplierForm, supplier)
  supplierFormVisible.value = true
}

// 提交供应商表单
const handleSubmitSupplier = async () => {
  try {
    if (isEditSupplier.value) {
      await supplierApi.updateSupplier(supplierForm.id, supplierForm)
      ElMessage.success('更新成功')
    } else {
      await supplierApi.createSupplier(supplierForm)
      ElMessage.success('创建成功')
    }
    supplierFormVisible.value = false
    loadSuppliers()
    loadStats()
  } catch (error) {
    console.error('操作失败', error)
  }
}

// 重置供应商表单
const resetSupplierForm = () => {
  Object.assign(supplierForm, {
    id: '',
    name: '',
    contactPerson: '',
    phone: '',
    email: '',
    type: 'TRADER',
    country: '',
    city: '',
    address: '',
    category: '',
    cooperationStartAt: '',
    totalPurchaseAmount: 0,
    totalOrders: 0,
    rating: 5,
    level: 'B',
    remark: '',
    status: 'ACTIVE'
  })
}

// 查看供应商详情
const handleViewSupplier = async (supplier: Supplier) => {
  // 📌 真去后端拉详情，而不是把列表行对象直接塞进弹窗
  //    （列表是分页查询，返回的字段可能不完整）
  try {
    const detail = await supplierApi.getSupplierById(supplier.id)
    currentSupplier.value = detail
  } catch (error) {
    console.error('加载供应商详情失败', error)
    currentSupplier.value = supplier
  }
  supplierDetailVisible.value = true
}

// 创建采购
const handleCreatePurchase = (supplier: Supplier) => {
  purchaseForm.supplierId = supplier.id
  openPurchaseDialog()
}

/** 打开新建采购单弹窗（先拉供应商下拉数据） */
const openPurchaseDialog = async () => {
  if (!purchaseSupplierOptions.value.length) {
    await loadPurchaseSupplierOptions()
  }
  showPurchaseDialog.value = true
}

// 创建采购单
const handleCreatePurchaseOrder = async () => {
  if (!purchaseForm.supplierId) {
    ElMessage.warning('请选择供应商')
    return
  }
  try {
    await supplierApi.createPurchaseOrder({
      id: '',
      orderNo: '',
      supplierId: purchaseForm.supplierId,
      supplierName: supplierList.value.find(s => s.id === purchaseForm.supplierId)?.name || '',
      status: 'PENDING',
      totalAmount: 0,
      paidAmount: 0,
      currency: 'USD',
      expectedArrivalAt: purchaseForm.expectedArrivalAt,
      actualArrivalAt: '',
      paymentMethod: purchaseForm.paymentMethod,
      paymentStatus: 'UNPAID',
      purchaserId: '1',
      purchaserName: '',
      remark: purchaseForm.remark,
      createTime: ''
    })
    ElMessage.success('采购单创建成功')
    showPurchaseDialog.value = false
    activeTab.value = 'purchase'
    loadPurchaseOrders()
  } catch (error) {
    console.error('创建采购单失败', error)
  }
}

// 加载采购订单
const loadPurchaseOrders = async () => {
  purchaseLoading.value = true
  try {
    const res = await supplierApi.getPurchaseOrderList({
      current: purchasePagination.current,
      size: purchasePagination.size,
      supplierId: purchaseSearchForm.supplierId,
      status: purchaseSearchForm.status || undefined,
      paymentStatus: purchaseSearchForm.paymentStatus || undefined
    })
    const pageData = unwrapPage(res)
    purchaseOrderList.value = pageData.records || []
    purchasePagination.total = pageData.total || 0
  } catch (error) {
    console.error('加载采购订单失败', error)
  } finally {
    purchaseLoading.value = false
  }
}

// 工具函数
const getTypeName = (type: string) => {
  const map: Record<string, string> = { FACTORY: '工厂', TRADER: '贸易商', AGENT: '代理商' }
  return map[type] || type
}

const getLevelType = (level: string) => {
  const map: Record<string, string> = { A: 'danger', B: 'warning', C: 'info' }
  return map[level] || ''
}

const getPurchaseStatusName = (status: string) => {
  const map: Record<string, string> = {
    PENDING: '待审批', APPROVED: '已审批', PURCHASING: '采购中',
    IN_TRANSIT: '运输中', RECEIVED: '已收货', CANCELLED: '已取消'
  }
  return map[status] || status
}

const getPurchaseStatusType = (status: string) => {
  const map: Record<string, string> = {
    PENDING: 'info', APPROVED: 'success', PURCHASING: 'warning',
    IN_TRANSIT: 'primary', RECEIVED: 'success', CANCELLED: 'danger'
  }
  return map[status] || ''
}

const getPaymentStatusName = (status: string) => {
  const map: Record<string, string> = { UNPAID: '未付款', PARTIAL: '部分付款', PAID: '已付款' }
  return map[status] || status
}

const getPaymentStatusType = (status: string) => {
  const map: Record<string, string> = { UNPAID: 'danger', PARTIAL: 'warning', PAID: 'success' }
  return map[status] || ''
}

const formatAmount = (amount: number) => amount?.toFixed(2) || '0.00'
const formatTime = (time?: string) => time ? dayjs(time).format('YYYY-MM-DD') : '-'

onMounted(() => {
  loadSuppliers()
  loadStats()
  loadPurchaseOrders()
  loadPurchaseSupplierOptions()
})
</script>

<style scoped>
.supplier-page {
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

.eval-score {
  text-align: center;
  padding: 20px;
}

.score-label {
  font-size: 14px;
  color: #666;
  margin-bottom: 10px;
}

.score-value {
  font-size: 32px;
  font-weight: bold;
  color: #409eff;
  margin-bottom: 15px;
}
</style>