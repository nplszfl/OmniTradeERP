<template>
  <div class="social-page">
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="本页数据全部来自后端真实接口（社交电商服务）。接口异常时会直接报错，不会用假数据兜底。"
    />

    <el-tabs v-model="tab" @tab-change="handleTabChange">
      <!-- ==================== TikTok 店铺 ==================== -->
      <el-tab-pane label="TikTok 店铺" name="tiktok">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>TikTok 店铺授权配置</span>
              <div>
                <el-button type="primary" size="small" :icon="Refresh" @click="syncTiktok" :loading="loading.sync">同步</el-button>
                <el-button type="success" size="small" :icon="Plus" @click="openShopDialog('tiktok', null)">新增店铺</el-button>
              </div>
            </div>
          </template>
          <el-table :data="tiktok" v-loading="loading.tiktok" stripe border>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="shopName" label="店铺名称" min-width="160" />
            <el-table-column prop="shopId" label="店铺 ID" min-width="160" />
            <el-table-column prop="status" label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="shopTagType(row.status)" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="tokenExpireTime" label="Token 过期时间" width="180">
              <template #default="{ row }">{{ formatTime(row.tokenExpireTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="viewShop('tiktok', row)">详情</el-button>
                <el-button link type="primary" size="small" @click="openShopDialog('tiktok', row)">编辑</el-button>
                <el-button link type="warning" size="small" @click="refreshShopToken('tiktok', row)">刷新 Token</el-button>
                <el-button link type="danger" size="small" @click="removeShop('tiktok', row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ==================== 抖音店铺 ==================== -->
      <el-tab-pane label="抖音店铺" name="douyin">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>抖音小店授权配置</span>
              <div>
                <el-button type="primary" size="small" :icon="Refresh" @click="syncDouyin" :loading="loading.sync">同步</el-button>
                <el-button type="success" size="small" :icon="Plus" @click="openShopDialog('douyin', null)">新增店铺</el-button>
              </div>
            </div>
          </template>
          <el-table :data="douyin" v-loading="loading.douyin" stripe border>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="shopName" label="店铺名称" min-width="160" />
            <el-table-column prop="shopId" label="店铺 ID" min-width="160" />
            <el-table-column prop="status" label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="shopTagType(row.status)" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="tokenExpireTime" label="Token 过期时间" width="180">
              <template #default="{ row }">{{ formatTime(row.tokenExpireTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="viewShop('douyin', row)">详情</el-button>
                <el-button link type="primary" size="small" @click="openShopDialog('douyin', row)">编辑</el-button>
                <el-button link type="warning" size="small" @click="refreshShopToken('douyin', row)">刷新 Token</el-button>
                <el-button link type="danger" size="small" @click="removeShop('douyin', row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ==================== 直播 ==================== -->
      <el-tab-pane label="直播管理" name="live">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>直播带货</span>
              <div>
                <el-button size="small" :icon="VideoPlay" @click="loadLiveCurrent">只看直播中</el-button>
                <el-button size="small" :icon="Clock" @click="loadLiveRecent">最近直播</el-button>
                <el-button type="success" size="small" :icon="Plus" @click="openLiveDialog(null)">新建直播</el-button>
              </div>
            </div>
          </template>
          <el-form inline class="filter-bar">
            <el-form-item label="平台">
              <el-select v-model="liveFilter.platform" placeholder="全部" clearable style="width: 130px" @change="loadLive">
                <el-option v-for="p in LIVE_PLATFORMS" :key="p" :label="p" :value="p" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="liveFilter.status" placeholder="全部" clearable style="width: 130px" @change="loadLive">
                <el-option v-for="s in LIVE_STATUS" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
            <el-form-item label="时间范围">
              <el-date-picker
                v-model="liveFilter.range"
                type="datetimerange"
                value-format="YYYY-MM-DD HH:mm:ss"
                range-separator="~"
                start-placeholder="开始"
                end-placeholder="结束"
                style="width: 320px"
                @change="loadLive" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="loadLive">查询</el-button>
              <el-button :icon="Refresh" @click="resetLiveFilter">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table :data="lives" v-loading="loading.live" stripe border>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="title" label="直播标题" min-width="200" show-overflow-tooltip />
            <el-table-column prop="platform" label="平台" width="110" />
            <el-table-column prop="status" label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="liveTagType(row.status)" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="viewerCount" label="观看人数" width="110" />
            <el-table-column prop="peakViewers" label="峰值在线" width="100" />
            <el-table-column prop="totalGmv" label="GMV" width="120">
              <template #default="{ row }">{{ money(row.totalGmv) }}</template>
            </el-table-column>
            <el-table-column prop="totalOrders" label="订单数" width="90" />
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="viewLive(row)">详情</el-button>
                <el-button v-if="row.status === 'SCHEDULED'" link type="warning" size="small" @click="startLive(row)">开始</el-button>
                <el-button v-if="row.status === 'LIVE'" link type="success" size="small" @click="endLive(row)">结束</el-button>
                <el-button link type="primary" size="small" @click="openLiveDialog(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="removeLive(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ==================== 达人 ==================== -->
      <el-tab-pane label="达人库" name="influencer">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>达人库</span>
              <div>
                <el-button size="small" :icon="Trophy" @click="loadTopInfluencers">头部达人</el-button>
                <el-button type="success" size="small" :icon="Plus" @click="openInfluencerDialog(null)">新增达人</el-button>
              </div>
            </div>
          </template>
          <el-form inline class="filter-bar">
            <el-form-item label="平台">
              <el-select v-model="influencerFilter.platform" placeholder="全部" clearable style="width: 130px" @change="loadInfluencers">
                <el-option v-for="p in INFLUENCER_PLATFORMS" :key="p" :label="p" :value="p" />
              </el-select>
            </el-form-item>
            <el-form-item label="级别">
              <el-select v-model="influencerFilter.level" placeholder="全部" clearable style="width: 120px" @change="loadInfluencers">
                <el-option v-for="l in INFLUENCER_LEVEL" :key="l" :label="l" :value="l" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="influencerFilter.status" placeholder="全部" clearable style="width: 130px" @change="loadInfluencers">
                <el-option v-for="s in INFLUENCER_STATUS" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
            <el-form-item label="类目">
              <el-input v-model="influencerFilter.category" placeholder="如：美妆" clearable style="width: 130px" @change="loadInfluencers" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="loadInfluencers">查询</el-button>
              <el-button :icon="Refresh" @click="resetInfluencerFilter">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table :data="influencers" v-loading="loading.influencer" stripe border>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="name" label="达人名称" min-width="140" />
            <el-table-column prop="platform" label="平台" width="110" />
            <el-table-column prop="followerCount" label="粉丝数" width="110" />
            <el-table-column prop="level" label="级别" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.level" size="small" :type="row.level === 'MAK' ? 'danger' : (row.level === 'KOL' ? 'warning' : 'info')">{{ row.level }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="categories" label="类目" min-width="140" show-overflow-tooltip />
            <el-table-column prop="cooperationPrice" label="合作报价" width="110">
              <template #default="{ row }">{{ money(row.cooperationPrice) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="viewInfluencer(row)">详情</el-button>
                <el-button link type="primary" size="small" @click="viewInfluencerCooperations(row)">合作</el-button>
                <el-button link type="primary" size="small" @click="openInfluencerDialog(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="removeInfluencer(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ==================== 达人合作 ==================== -->
      <el-tab-pane label="达人合作" name="cooperation">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>达人合作</span>
              <div>
                <el-button size="small" :icon="CircleCheck" @click="loadCoopCompleted">已完成</el-button>
                <el-button size="small" :icon="Bell" @click="loadCoopUpcoming">即将开始</el-button>
                <el-button type="success" size="small" :icon="Plus" @click="openCooperationDialog(null)">新建合作</el-button>
              </div>
            </div>
          </template>
          <el-form inline class="filter-bar">
            <el-form-item label="类型">
              <el-select v-model="coopFilter.type" placeholder="全部" clearable style="width: 150px" @change="loadCooperations">
                <el-option v-for="t in COOP_TYPE" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="coopFilter.status" placeholder="全部" clearable style="width: 150px" @change="loadCooperations">
                <el-option v-for="s in COOP_STATUS" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
            <el-form-item label="计划时间">
              <el-date-picker
                v-model="coopFilter.range"
                type="datetimerange"
                value-format="YYYY-MM-DD HH:mm:ss"
                range-separator="~"
                start-placeholder="开始"
                end-placeholder="结束"
                style="width: 320px"
                @change="loadCooperations" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="loadCooperations">查询</el-button>
              <el-button :icon="Refresh" @click="resetCoopFilter">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table :data="cooperations" v-loading="loading.cooperation" stripe border>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="productName" label="产品" min-width="160" show-overflow-tooltip />
            <el-table-column prop="type" label="合作类型" width="120" />
            <el-table-column prop="status" label="状态" width="130">
              <template #default="{ row }">
                <el-tag size="small" :type="coopTagType(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="agreedPrice" label="约定价格" width="110">
              <template #default="{ row }">{{ money(row.agreedPrice) }}</template>
            </el-table-column>
            <el-table-column prop="actualGmv" label="实际 GMV" width="120">
              <template #default="{ row }">{{ money(row.actualGmv) }}</template>
            </el-table-column>
            <el-table-column prop="actualViews" label="实际播放" width="100" />
            <el-table-column prop="plannedStartDate" label="计划开始" width="160">
              <template #default="{ row }">{{ formatTime(row.plannedStartDate) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="300" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="viewCooperation(row)">详情</el-button>
                <el-button v-if="row.status === 'PENDING'" link type="warning" size="small" @click="coopAction('confirm', row)">确认</el-button>
                <el-button v-if="row.status === 'CONFIRMED'" link type="success" size="small" @click="coopAction('start', row)">开始</el-button>
                <el-button v-if="row.status === 'IN_PROGRESS'" link type="success" size="small" @click="coopAction('complete', row)">完成</el-button>
                <el-button v-if="['PENDING', 'CONFIRMED'].includes(row.status)" link type="info" size="small" @click="coopAction('cancel', row)">取消</el-button>
                <el-button link type="danger" size="small" @click="removeCooperation(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- ==================== 店铺弹窗 ==================== -->
    <el-dialog v-model="shopDialog.visible" :title="shopDialog.row ? '编辑店铺' : '新增店铺'" width="520px">
      <el-form :model="shopDialog.form" label-width="110px">
        <el-form-item label="店铺名称" required>
          <el-input v-model="shopDialog.form.shopName" placeholder="如：OmniTrade 官方店" />
        </el-form-item>
        <el-form-item label="店铺 ID" required>
          <el-input v-model="shopDialog.form.shopId" placeholder="平台内唯一店铺 ID" />
        </el-form-item>
        <el-form-item label="Access Token">
          <el-input v-model="shopDialog.form.accessToken" type="password" show-password placeholder="留空表示不修改" />
        </el-form-item>
        <el-form-item label="Refresh Token">
          <el-input v-model="shopDialog.form.refreshToken" type="password" show-password placeholder="留空表示不修改" />
        </el-form-item>
        <el-form-item label="App Key">
          <el-input v-model="shopDialog.form.appKey" />
        </el-form-item>
        <el-form-item label="状态" required>
          <el-select v-model="shopDialog.form.status" style="width: 100%">
            <el-option v-for="s in SHOP_STATUS" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shopDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="shopDialog.saving" @click="saveShop">保存</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 直播弹窗 ==================== -->
    <el-dialog v-model="liveDialog.visible" :title="liveDialog.row ? '编辑直播' : '新建直播'" width="600px">
      <el-form :model="liveDialog.form" label-width="110px">
        <el-form-item label="直播 ID" required>
          <el-input v-model="liveDialog.form.streamId" placeholder="平台直播流 ID" />
        </el-form-item>
        <el-form-item label="直播标题" required>
          <el-input v-model="liveDialog.form.title" placeholder="如：新品发布专场" />
        </el-form-item>
        <el-form-item label="平台" required>
          <el-select v-model="liveDialog.form.platform" style="width: 100%">
            <el-option v-for="p in LIVE_PLATFORMS" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="主播达人">
          <el-select v-model="liveDialog.form.influencerId" placeholder="选择主播" clearable filterable style="width: 100%">
            <el-option v-for="i in influencers" :key="i.id" :label="`${i.name} (${i.platform})`" :value="i.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" required>
          <el-select v-model="liveDialog.form.status" style="width: 100%">
            <el-option v-for="s in LIVE_STATUS" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划开始">
          <el-date-picker v-model="liveDialog.form.scheduledStartTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="liveDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="liveDialog.saving" @click="saveLive">保存</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 达人弹窗 ==================== -->
    <el-dialog v-model="influencerDialog.visible" :title="influencerDialog.row ? '编辑达人' : '新增达人'" width="600px">
      <el-form :model="influencerDialog.form" label-width="110px">
        <el-form-item label="达人名称" required>
          <el-input v-model="influencerDialog.form.name" />
        </el-form-item>
        <el-form-item label="平台" required>
          <el-select v-model="influencerDialog.form.platform" style="width: 100%">
            <el-option v-for="p in INFLUENCER_PLATFORMS" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="平台 ID">
          <el-input v-model="influencerDialog.form.platformId" />
        </el-form-item>
        <el-form-item label="粉丝数">
          <el-input-number v-model="influencerDialog.form.followerCount" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="influencerDialog.form.level" style="width: 100%">
            <el-option v-for="l in INFLUENCER_LEVEL" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="influencerDialog.form.status" style="width: 100%">
            <el-option v-for="s in INFLUENCER_STATUS" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="类目">
          <el-input v-model="influencerDialog.form.categories" placeholder="逗号分隔，如：美妆,个护" />
        </el-form-item>
        <el-form-item label="联系方式">
          <el-input v-model="influencerDialog.form.contactEmail" placeholder="邮箱" />
        </el-form-item>
        <el-form-item label="合作报价">
          <el-input-number v-model="influencerDialog.form.cooperationPrice" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="influencerDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="influencerDialog.saving" @click="saveInfluencer">保存</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 合作弹窗 ==================== -->
    <el-dialog v-model="coopDialog.visible" :title="coopDialog.row ? '编辑合作' : '新建合作'" width="620px">
      <el-form :model="coopDialog.form" label-width="110px">
        <el-form-item label="合作达人" required>
          <el-select v-model="coopDialog.form.influencerId" placeholder="选择达人" filterable style="width: 100%">
            <el-option v-for="i in influencers" :key="i.id" :label="`${i.name} (${i.platform})`" :value="i.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品 ID" required>
          <el-input v-model="coopDialog.form.productId" />
        </el-form-item>
        <el-form-item label="产品名称" required>
          <el-input v-model="coopDialog.form.productName" />
        </el-form-item>
        <el-form-item label="合作类型" required>
          <el-select v-model="coopDialog.form.type" style="width: 100%">
            <el-option v-for="t in COOP_TYPE" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" required>
          <el-select v-model="coopDialog.form.status" style="width: 100%">
            <el-option v-for="s in COOP_STATUS" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="约定价格">
          <el-input-number v-model="coopDialog.form.agreedPrice" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="佣金率(%)">
          <el-input-number v-model="coopDialog.form.commissionRate" :min="0" :max="100" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计划开始">
          <el-date-picker v-model="coopDialog.form.plannedStartDate" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计划结束">
          <el-date-picker v-model="coopDialog.form.plannedEndDate" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="coopDialog.form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="coopDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="coopDialog.saving" @click="saveCooperation">保存</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 详情弹窗 ==================== -->
    <el-dialog v-model="detail.visible" :title="detail.title" width="640px">
      <el-descriptions :column="2" border v-if="detail.data">
        <el-descriptions-item v-for="(v, k) in detailFlat" :key="k" :label="k">
          {{ v }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Search, Refresh, VideoPlay, Clock, Trophy, CircleCheck, Bell,
} from '@element-plus/icons-vue'
import {
  tiktokShopApi, douyinShopApi, liveStreamApi, influencerApi, cooperationApi,
  type LiveStream, type Influencer, type Cooperation,
} from '@/api/social'

// ⚠️ 平台枚举**各模块不一样**（后端 @Enumerated(EnumType.STRING)）：
//    直播  LiveStream.Platform      = TIKTOK / DOUYIN / TAOBAO_LIVE
//    达人  Influencer.Platform      = TIKTOK / DOUYIN / INSTAGRAM / YOUTUBE
//    写错枚举值 → Hibernate 读的时候抛 "No enum constant" → 整个服务查询报错。
const LIVE_PLATFORMS = ['TIKTOK', 'DOUYIN', 'TAOBAO_LIVE']
const INFLUENCER_PLATFORMS = ['TIKTOK', 'DOUYIN', 'INSTAGRAM', 'YOUTUBE']
const LIVE_STATUS = ['SCHEDULED', 'LIVE', 'ENDED', 'CANCELLED']
const INFLUENCER_LEVEL = ['KOC', 'KOL', 'MAK']
const INFLUENCER_STATUS = ['ACTIVE', 'INACTIVE', 'BLACKLIST']
const COOP_TYPE = ['LIVE', 'SHORT_VIDEO', 'POST', 'STORY', 'COLLABORATION', 'LIVE_STREAM']
const COOP_STATUS = ['DRAFT', 'PENDING', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED']
const SHOP_STATUS = ['ACTIVE', 'INACTIVE', 'EXPIRED', 'ERROR']

const tab = ref('tiktok')

const tiktok = ref<any[]>([])
const douyin = ref<any[]>([])
const lives = ref<LiveStream[]>([])
const influencers = ref<Influencer[]>([])
const cooperations = ref<Cooperation[]>([])

const loading = reactive({
  tiktok: false, douyin: false, live: false, influencer: false, cooperation: false, sync: false,
})

// ---------- 筛选 ----------
const liveFilter = reactive({ platform: '', status: '', range: [] as string[] })
const influencerFilter = reactive({ platform: '', level: '', status: '', category: '' })
const coopFilter = reactive({ type: '', status: '', range: [] as string[] })

// ---------- 弹窗 ----------
const shopDialog = reactive({
  visible: false, saving: false, kind: 'tiktok' as 'tiktok' | 'douyin',
  row: null as any,
  form: { shopName: '', shopId: '', accessToken: '', refreshToken: '', appKey: '', status: 'ACTIVE' },
})
const liveDialog = reactive({
  visible: false, saving: false, row: null as LiveStream | null,
  form: { streamId: '', title: '', platform: 'TIKTOK', influencerId: undefined as number | undefined, status: 'SCHEDULED', scheduledStartTime: '' },
})
const influencerDialog = reactive({
  visible: false, saving: false, row: null as Influencer | null,
  form: { name: '', platform: 'TIKTOK', platformId: '', followerCount: 0, level: 'KOC', status: 'ACTIVE', categories: '', contactEmail: '', cooperationPrice: 0 },
})
const coopDialog = reactive({
  visible: false, saving: false, row: null as Cooperation | null,
  form: {
    influencerId: undefined as number | undefined, productId: '', productName: '',
    type: 'LIVE', status: 'PENDING', agreedPrice: 0, commissionRate: 0,
    plannedStartDate: '', plannedEndDate: '', remark: '',
  },
})
const detail = reactive({ visible: false, title: '', data: null as any })

// 详情弹窗：把对象拍平成 { 字段名: 值 }，el-descriptions 直接渲染
const detailFlat = computed<Record<string, any>>(() => {
  if (!detail.data) return {}
  const out: Record<string, any> = {}
  for (const [k, v] of Object.entries(detail.data)) {
    if (v === null || v === undefined || v === '') continue
    out[k] = typeof v === 'object' ? JSON.stringify(v) : v
  }
  return out
})

// ---------- 工具 ----------
function formatTime(v?: string) {
  return v ? String(v).replace('T', ' ').slice(0, 19) : '-'
}
function money(v?: number) {
  return v === null || v === undefined ? '-' : Number(v).toFixed(2)
}
function shopTagType(s?: string): 'success' | 'info' | 'danger' | 'warning' {
  return s === 'ACTIVE' ? 'success' : s === 'ERROR' ? 'danger' : s === 'EXPIRED' ? 'warning' : 'info'
}
function liveTagType(s?: string): 'success' | 'info' | 'danger' | 'warning' {
  return s === 'LIVE' ? 'danger' : s === 'ENDED' ? 'info' : s === 'CANCELLED' ? 'warning' : 'success'
}
function coopTagType(s?: string): 'success' | 'info' | 'danger' | 'warning' {
  if (s === 'COMPLETED') return 'success'
  if (s === 'CANCELLED') return 'danger'
  if (s === 'IN_PROGRESS') return 'warning'
  return 'info'
}

// ---------- 加载 ----------
async function loadTiktok() {
  loading.tiktok = true
  try {
    tiktok.value = (await tiktokShopApi.list()) || []
  } catch {
    tiktok.value = []
  } finally {
    loading.tiktok = false
  }
}
async function loadDouyin() {
  loading.douyin = true
  try {
    douyin.value = (await douyinShopApi.list()) || []
  } catch {
    douyin.value = []
  } finally {
    loading.douyin = false
  }
}
async function loadLive() {
  loading.live = true
  try {
    let rows: LiveStream[] = []
    // 📌 后端没有「组合筛选」接口，这里按筛选条件挑最贴切的那个调用，
    //    保证筛选走的也是真实后端接口（而不是前端拿全量自己过滤）。
    const range = liveFilter.range
    if (range && range.length === 2) {
      rows = (await liveStreamApi.byDateRange(range[0], range[1])) || []
      if (liveFilter.status) rows = rows.filter(r => r.status === liveFilter.status)
      if (liveFilter.platform) rows = rows.filter(r => r.platform === liveFilter.platform)
    } else if (liveFilter.platform && liveFilter.status) {
      rows = (await liveStreamApi.byPlatform(liveFilter.platform)) || []
      rows = rows.filter(r => r.status === liveFilter.status)
    } else if (liveFilter.status) {
      rows = (await liveStreamApi.byStatus(liveFilter.status)) || []
    } else if (liveFilter.platform) {
      rows = (await liveStreamApi.byPlatform(liveFilter.platform)) || []
    } else {
      rows = (await liveStreamApi.list()) || []
    }
    lives.value = rows
  } catch {
    lives.value = []
  } finally {
    loading.live = false
  }
}
async function loadInfluencers() {
  loading.influencer = true
  try {
    let rows: Influencer[] = []
    if (influencerFilter.category) {
      rows = (await influencerApi.byCategory(influencerFilter.category)) || []
    } else if (influencerFilter.level) {
      rows = (await influencerApi.byLevel(influencerFilter.level)) || []
    } else if (influencerFilter.status) {
      rows = (await influencerApi.byStatus(influencerFilter.status)) || []
    } else if (influencerFilter.platform) {
      rows = (await influencerApi.byPlatform(influencerFilter.platform)) || []
    } else {
      rows = (await influencerApi.list()) || []
    }
    influencers.value = rows
  } catch {
    influencers.value = []
  } finally {
    loading.influencer = false
  }
}
async function loadCooperations() {
  loading.cooperation = true
  try {
    let rows: Cooperation[] = []
    const range = coopFilter.range
    if (range && range.length === 2) {
      rows = (await cooperationApi.byDateRange(range[0], range[1])) || []
      if (coopFilter.status) rows = rows.filter(r => r.status === coopFilter.status)
      if (coopFilter.type) rows = rows.filter(r => r.type === coopFilter.type)
    } else if (coopFilter.status) {
      rows = (await cooperationApi.byStatus(coopFilter.status)) || []
    } else if (coopFilter.type) {
      rows = (await cooperationApi.byType(coopFilter.type)) || []
    } else {
      rows = (await cooperationApi.list()) || []
    }
    cooperations.value = rows
  } catch {
    cooperations.value = []
  } finally {
    loading.cooperation = false
  }
}

function handleTabChange(name: string | number) {
  const key = String(name)
  if (key === 'tiktok') loadTiktok()
  else if (key === 'douyin') loadDouyin()
  else if (key === 'live') loadLive()
  else if (key === 'influencer') loadInfluencers()
  else if (key === 'cooperation') loadCooperations()
}

onMounted(async () => {
  await Promise.all([loadTiktok(), loadInfluencers()])
})

// ---------- 直播快捷 ----------
async function loadLiveCurrent() {
  lives.value = (await liveStreamApi.currentLive()) || []
}
async function loadLiveRecent() {
  lives.value = (await liveStreamApi.recent()) || []
}
function resetLiveFilter() {
  liveFilter.platform = ''
  liveFilter.status = ''
  liveFilter.range = []
  loadLive()
}
function resetInfluencerFilter() {
  influencerFilter.platform = ''
  influencerFilter.level = ''
  influencerFilter.status = ''
  influencerFilter.category = ''
  loadInfluencers()
}
function resetCoopFilter() {
  coopFilter.type = ''
  coopFilter.status = ''
  coopFilter.range = []
  loadCooperations()
}
async function loadTopInfluencers() {
  influencers.value = (await influencerApi.top()) || []
}
async function loadCoopCompleted() {
  cooperations.value = (await cooperationApi.completed()) || []
}
async function loadCoopUpcoming() {
  cooperations.value = (await cooperationApi.upcoming()) || []
}

// ---------- 店铺 ----------
async function viewShop(kind: 'tiktok' | 'douyin', row: any) {
  const data = kind === 'tiktok'
    ? await tiktokShopApi.detail(row.id)
    : await douyinShopApi.detail(row.id)
  detail.title = `店铺详情 #${row.id}`
  detail.data = data
  detail.visible = true
}
function openShopDialog(kind: 'tiktok' | 'douyin', row: any) {
  shopDialog.kind = kind
  shopDialog.row = row
  shopDialog.form = row
    ? { ...row }
    : { shopName: '', shopId: '', accessToken: '', refreshToken: '', appKey: '', status: 'ACTIVE' }
  shopDialog.visible = true
}
async function saveShop() {
  if (!shopDialog.form.shopName || !shopDialog.form.shopId) {
    ElMessage.warning('店铺名称和店铺 ID 必填')
    return
  }
  shopDialog.saving = true
  try {
    // ⚠️ 这里必须写显式分支而不是 `const api = 条件 ? a : b; api.update(...)`：
    //    静态巡检脚本（check-dead-endpoints.py）只能匹配 `apiFn.method(...)` 字面量，
    //    间接调用会被判成「死接口」→ 清单误报。
    if (shopDialog.kind === 'tiktok') {
      if (shopDialog.row) await tiktokShopApi.update(shopDialog.row.id, shopDialog.form)
      else await tiktokShopApi.create(shopDialog.form)
      loadTiktok()
    } else {
      if (shopDialog.row) await douyinShopApi.update(shopDialog.row.id, shopDialog.form)
      else await douyinShopApi.create(shopDialog.form)
      loadDouyin()
    }
    ElMessage.success('保存成功')
    shopDialog.visible = false
  } catch {
    /* 拦截器已提示 */
  } finally {
    shopDialog.saving = false
  }
}
async function refreshShopToken(kind: 'tiktok' | 'douyin', row: any) {
  if (kind === 'tiktok') {
    await tiktokShopApi.refreshToken(row.id)
    loadTiktok()
  } else {
    await douyinShopApi.refreshToken(row.id)
    loadDouyin()
  }
  ElMessage.success('Token 已刷新')
}
async function removeShop(kind: 'tiktok' | 'douyin', row: any) {
  await ElMessageBox.confirm(`确定删除店铺「${row.shopName}」吗？`, '提示', { type: 'warning' })
  if (kind === 'tiktok') {
    await tiktokShopApi.remove(row.id)
    loadTiktok()
  } else {
    await douyinShopApi.remove(row.id)
    loadDouyin()
  }
  ElMessage.success('已删除')
}
async function syncTiktok() {
  loading.sync = true
  try {
    await tiktokShopApi.sync()
    ElMessage.success('同步任务已提交')
    loadTiktok()
  } finally {
    loading.sync = false
  }
}
async function syncDouyin() {
  loading.sync = true
  try {
    await douyinShopApi.sync()
    ElMessage.success('同步任务已提交')
    loadDouyin()
  } finally {
    loading.sync = false
  }
}

// ---------- 直播 ----------
async function viewLive(row: LiveStream) {
  const data = await liveStreamApi.detail(row.id)
  detail.title = `直播详情 #${row.id}`
  detail.data = data
  detail.visible = true
}
function openLiveDialog(row: LiveStream | null) {
  liveDialog.row = row
  liveDialog.form = row
    ? {
        streamId: row.streamId, title: row.title, platform: row.platform,
        influencerId: row.influencerId, status: row.status,
        scheduledStartTime: row.scheduledStartTime ? formatTime(row.scheduledStartTime) : '',
      }
    : { streamId: '', title: '', platform: 'TIKTOK', influencerId: undefined, status: 'SCHEDULED', scheduledStartTime: '' }
  liveDialog.visible = true
}
async function saveLive() {
  if (!liveDialog.form.streamId || !liveDialog.form.title || !liveDialog.form.platform) {
    ElMessage.warning('直播 ID、标题、平台必填')
    return
  }
  liveDialog.saving = true
  try {
    if (liveDialog.row) await liveStreamApi.update(liveDialog.row.id, liveDialog.form)
    else await liveStreamApi.create(liveDialog.form)
    ElMessage.success('保存成功')
    liveDialog.visible = false
    loadLive()
  } catch {
    /* 拦截器已提示 */
  } finally {
    liveDialog.saving = false
  }
}
async function startLive(row: LiveStream) {
  await liveStreamApi.start(row.id)
  ElMessage.success('直播已开始')
  loadLive()
}
async function endLive(row: LiveStream) {
  await liveStreamApi.end(row.id)
  ElMessage.success('直播已结束')
  loadLive()
}
async function removeLive(row: LiveStream) {
  await ElMessageBox.confirm(`确定删除直播「${row.title}」吗？`, '提示', { type: 'warning' })
  await liveStreamApi.remove(row.id)
  ElMessage.success('已删除')
  loadLive()
}

// ---------- 达人 ----------
async function viewInfluencer(row: Influencer) {
  const data = await influencerApi.detail(row.id)
  detail.title = `达人详情 #${row.id}`
  detail.data = data
  detail.visible = true
}
async function viewInfluencerCooperations(row: Influencer) {
  cooperations.value = (await cooperationApi.byInfluencer(row.id)) || []
  detail.title = `「${row.name}」的合作记录`
  detail.data = { 合作数: cooperations.value.length, 记录: cooperations.value }
  detail.visible = true
  tab.value = 'cooperation'
}
function openInfluencerDialog(row: Influencer | null) {
  influencerDialog.row = row
  influencerDialog.form = row
    ? {
        name: row.name, platform: row.platform, platformId: row.platformId || '',
        followerCount: row.followerCount || 0, level: row.level || 'KOC',
        status: row.status || 'ACTIVE', categories: row.categories || '',
        contactEmail: row.contactEmail || '', cooperationPrice: Number(row.cooperationPrice || 0),
      }
    : { name: '', platform: 'TIKTOK', platformId: '', followerCount: 0, level: 'KOC', status: 'ACTIVE', categories: '', contactEmail: '', cooperationPrice: 0 }
  influencerDialog.visible = true
}
async function saveInfluencer() {
  if (!influencerDialog.form.name || !influencerDialog.form.platform) {
    ElMessage.warning('达人名称和平台必填')
    return
  }
  influencerDialog.saving = true
  try {
    if (influencerDialog.row) await influencerApi.update(influencerDialog.row.id, influencerDialog.form)
    else await influencerApi.create(influencerDialog.form)
    ElMessage.success('保存成功')
    influencerDialog.visible = false
    loadInfluencers()
  } catch {
    /* 拦截器已提示 */
  } finally {
    influencerDialog.saving = false
  }
}
async function removeInfluencer(row: Influencer) {
  await ElMessageBox.confirm(`确定删除达人「${row.name}」吗？`, '提示', { type: 'warning' })
  await influencerApi.remove(row.id)
  ElMessage.success('已删除')
  loadInfluencers()
}

// ---------- 合作 ----------
async function viewCooperation(row: Cooperation) {
  const data = await cooperationApi.detail(row.id)
  detail.title = `合作详情 #${row.id}`
  detail.data = data
  detail.visible = true
}
function openCooperationDialog(row: Cooperation | null) {
  coopDialog.row = row
  coopDialog.form = row
    ? {
        influencerId: row.influencerId, productId: row.productId, productName: row.productName,
        type: row.type, status: row.status, agreedPrice: Number(row.agreedPrice || 0),
        commissionRate: Number(row.commissionRate || 0),
        plannedStartDate: row.plannedStartDate ? formatTime(row.plannedStartDate) : '',
        plannedEndDate: row.plannedEndDate ? formatTime(row.plannedEndDate) : '',
        remark: row.remark || '',
      }
    : {
        influencerId: undefined, productId: '', productName: '', type: 'LIVE', status: 'PENDING',
        agreedPrice: 0, commissionRate: 0, plannedStartDate: '', plannedEndDate: '', remark: '',
      }
  coopDialog.visible = true
}
async function saveCooperation() {
  if (!coopDialog.form.influencerId || !coopDialog.form.productId || !coopDialog.form.productName) {
    ElMessage.warning('合作达人、产品 ID、产品名称必填')
    return
  }
  coopDialog.saving = true
  try {
    if (coopDialog.row) await cooperationApi.update(coopDialog.row.id, coopDialog.form)
    else await cooperationApi.create(coopDialog.form)
    ElMessage.success('保存成功')
    coopDialog.visible = false
    loadCooperations()
  } catch {
    /* 拦截器已提示 */
  } finally {
    coopDialog.saving = false
  }
}
async function coopAction(action: 'confirm' | 'start' | 'complete' | 'cancel', row: Cooperation) {
  if (action === 'complete') {
    // complete 需要带实际数据（实际播放/实际 GMV），这里用一个简化表单
    const { value } = await ElMessageBox.prompt(
      '请输入实际 GMV（可留空）',
      '完成合作',
      { inputPlaceholder: '实际 GMV 金额', inputPattern: /^[\d.]*$/, inputErrorMessage: '请输入数字' },
    )
    await cooperationApi.complete(row.id, { actualGmv: value ? Number(value) : undefined, actualViews: row.actualViews })
  } else if (action === 'cancel') {
    await ElMessageBox.confirm('确定取消该合作吗？', '提示', { type: 'warning' })
    await cooperationApi.cancel(row.id)
  } else {
    await cooperationApi[action](row.id)
  }
  ElMessage.success('操作成功')
  loadCooperations()
}
async function removeCooperation(row: Cooperation) {
  await ElMessageBox.confirm(`确定删除合作「${row.productName}」吗？`, '提示', { type: 'warning' })
  await cooperationApi.remove(row.id)
  ElMessage.success('已删除')
  loadCooperations()
}
</script>

<style scoped>
.social-page { padding: 16px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.filter-bar { margin-bottom: 8px; }
</style>
