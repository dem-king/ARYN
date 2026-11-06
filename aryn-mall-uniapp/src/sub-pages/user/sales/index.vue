<script setup lang="ts">
import type {
  SalesCustomer,
  SalesOrderRecord,
  SalesWorkbench,
} from '@/api/sales/entity'
import { reactive, ref } from 'vue'
import { onPageScroll, onShow } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import {
  getSalesCustomerPage,
  getSalesOrderPage,
  getSalesQrcode,
  getSalesWorkbench,
} from '@/api/sales/entity'
import {
  formatMoney,
  getSalesBindSourceText,
  getSalesOrderStatusClass,
  getSalesOrderStatusText,
} from './presentation'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { navigateToUrl } from '@/utils/tab-bar'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'sales-workbench',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '销售工作台',
  },
})

const { t } = useI18n()
const router = useRouter()
const globalLoading = useGlobalLoading()

/**
 * 销售工作台只读：展示推广码、我的客户、业绩明细、本月预估提成。
 *
 * - 提成随基本工资发放，页面内**不提供任何提现入口**；
 * - `isSales` 为 false 时只展示「非销售账号」提示，客户/业绩接口不发起；
 * - 汇总与推广码都来自服务端，前端不做金额推算（避免与后台台账口径不一致）。
 */
const workbench = ref<SalesWorkbench | null>(null)
/** null=加载中（不渲染内容，避免「非销售」提示先闪再切走）；false=非销售；true=在职销售 */
const isSales = ref<boolean | null>(null)
/** 工作台汇总请求失败（区别于「非销售」：失败给重试，不冒充身份判定） */
const loadFailed = ref(false)

const qrcodeUrl = ref('')
const qrcodeLoading = ref(false)
const qrcodeFailed = ref(false)

const customers = ref<SalesCustomer[]>([])
const customerPage = reactive({
  current: 0,
  loading: false,
  size: 10,
  total: 0,
})
const orders = ref<SalesOrderRecord[]>([])
const orderPage = reactive({
  current: 0,
  loading: false,
  size: 10,
  total: 0,
})

async function loadWorkbench() {
  try {
    const data = await getSalesWorkbench()
    workbench.value = data ?? null
    isSales.value = Boolean(data?.isSales)
    loadFailed.value = false
    qrcodeUrl.value = data?.qrcodeUrl || ''
    if (isSales.value && !qrcodeUrl.value) {
      void loadQrcode()
    }
  } catch (error) {
    console.warn('[sales-workbench] 工作台汇总加载失败', error)
    loadFailed.value = true
    isSales.value = null
  }
}

/** 推广码兜底：工作台汇总未带 qrcodeUrl 时单独取（user 域接口，Redis 缓存 7 天） */
async function loadQrcode() {
  if (qrcodeLoading.value) return
  qrcodeLoading.value = true
  qrcodeFailed.value = false
  try {
    const data = await getSalesQrcode()
    qrcodeUrl.value = data?.qrcodeUrl || ''
    qrcodeFailed.value = !qrcodeUrl.value
  } catch (error) {
    console.warn('[sales-workbench] 推广码获取失败', error)
    qrcodeFailed.value = true
  } finally {
    qrcodeLoading.value = false
  }
}

async function loadCustomers(reset = false) {
  if (customerPage.loading) return
  const current = reset ? 1 : customerPage.current + 1
  customerPage.loading = true
  try {
    const response = await getSalesCustomerPage({
      current,
      size: customerPage.size,
    })
    const records = response.records || []
    customers.value = reset ? records : [...customers.value, ...records]
    customerPage.current = response.current || current
    customerPage.total = response.total || 0
  } catch (error) {
    console.warn('[sales-workbench] 客户列表加载失败', error)
    if (reset) customers.value = []
  } finally {
    customerPage.loading = false
  }
}

async function loadOrders(reset = false) {
  if (orderPage.loading) return
  const current = reset ? 1 : orderPage.current + 1
  orderPage.loading = true
  try {
    const response = await getSalesOrderPage({
      current,
      size: orderPage.size,
    })
    const records = response.records || []
    orders.value = reset ? records : [...orders.value, ...records]
    orderPage.current = response.current || current
    orderPage.total = response.total || 0
  } catch (error) {
    console.warn('[sales-workbench] 业绩明细加载失败', error)
    if (reset) orders.value = []
  } finally {
    orderPage.loading = false
  }
}

async function reloadList() {
  if (!isSales.value) return
  await Promise.all([loadCustomers(true), loadOrders(true)])
}

async function reloadAll() {
  globalLoading.loading(t('common.loading'))
  try {
    await loadWorkbench()
    await reloadList()
  } finally {
    globalLoading.close()
  }
}

/** data URI → base64；非 data URI（对象存储 URL）返回空串 */
function extractDataUriBase64(url: string) {
  const match = /^data:image\/[\w+.-]+;base64,(.+)$/i.exec(url)
  return match?.[1] ?? ''
}

function saveFailed() {
  uni.showToast({
    title: t('sales.workbench.saveFailed'),
    icon: 'none',
  })
}

/** 写入临时文件后保存到相册（data URI 不能直接交给 saveImageToPhotosAlbum） */
function saveToAlbum(filePath: string) {
  uni.saveImageToPhotosAlbum({
    filePath,
    success: () => {
      uni.showToast({
        title: t('sales.workbench.saveSuccess'),
        icon: 'success',
      })
    },
    fail: saveFailed,
  })
}

/**
 * 保存推广码到相册。
 *
 * - data URI（data:image/png;base64,...）：先用文件系统写临时文件，再保存；
 * - 对象存储 URL：先 downloadFile 拿临时路径；
 * - H5 / App 端不支持相册 API：只提示长按保存。
 */
function saveQrcode() {
  const url = qrcodeUrl.value
  if (!url) {
    saveFailed()
    return
  }
  // #ifdef MP-WEIXIN
  const base64 = extractDataUriBase64(url)
  if (base64) {
    const filePath = `${wx.env.USER_DATA_PATH}/sales-qrcode-${Date.now()}.png`
    uni.getFileSystemManager().writeFile({
      filePath,
      data: base64,
      encoding: 'base64',
      success: () => saveToAlbum(filePath),
      fail: saveFailed,
    })
    return
  }
  uni.downloadFile({
    url,
    success: (res) => {
      if (res.statusCode === 200 && res.tempFilePath) {
        saveToAlbum(res.tempFilePath)
      }
      else {
        saveFailed()
      }
    },
    fail: saveFailed,
  })
  // #endif
  // #ifndef MP-WEIXIN
  saveFailed()
  // #endif
}

function retryQrcode() {
  void loadQrcode()
}

function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    router.back()
    return
  }
  navigateToUrl('/pages/user/user-center/index')
}

onShow(() => {
  globalLoading.loading(t('common.loading'))
  Promise.resolve()
    .then(async () => {
      await loadWorkbench()
      await reloadList()
    })
    .finally(() => {
      globalLoading.close()
    })
})

// 顶栏磨砂玻璃：字面量注册滚动（小程序按页面源码生成滚动位标记，包进 composable 会静默失效）
onPageScroll(handlePageScroll)
</script>

<template>
  <view>
    <hr-navbar :title="t('sales.workbench.navTitle')" />

    <view class="sales-page">
      <!-- 汇总加载失败：给重试，不冒充「非销售」身份判定 -->
      <view v-if="loadFailed" class="state-card">
        <text class="i-carbon:warning-alt state-icon" />
        <view class="state-title">
          {{ t('common.loadFailed') }}
        </view>
        <button class="state-btn" @click="reloadAll">
          {{ t('common.retry') }}
        </button>
      </view>

      <view v-else-if="isSales === false" class="state-card">
        <text class="i-carbon:user-x state-icon" />
        <view class="state-title">
          {{ t('sales.workbench.nonSalesTitle') }}
        </view>
        <view class="state-desc">
          {{ t('sales.workbench.nonSalesHint') }}
        </view>
        <button class="state-btn" @click="goBack">
          {{ t('sales.workbench.nonSalesBack') }}
        </button>
      </view>

      <template v-else-if="isSales">
        <view class="summary-card">
          <view class="summary-label">
            {{ t('sales.workbench.monthCommission') }}
          </view>
          <view class="summary-main">
            ¥ {{ formatMoney(workbench?.monthCommission) }}
          </view>
          <view class="summary-grid">
            <view class="summary-item">
              <view class="summary-item__label">
                {{ t('sales.workbench.monthTradeAmount') }}
              </view>
              <view class="summary-item__value">
                {{ formatMoney(workbench?.monthTradeAmount) }}
              </view>
            </view>
            <view class="summary-item">
              <view class="summary-item__label">
                {{ t('sales.workbench.totalCommission') }}
              </view>
              <view class="summary-item__value">
                {{ formatMoney(workbench?.totalCommission) }}
              </view>
            </view>
            <view class="summary-item">
              <view class="summary-item__label">
                {{ t('sales.workbench.monthOrderCount') }}
              </view>
              <view class="summary-item__value">
                {{ workbench?.monthOrderCount ?? 0 }}
              </view>
            </view>
            <view class="summary-item">
              <view class="summary-item__label">
                {{ t('sales.workbench.customerCount') }}
              </view>
              <view class="summary-item__value">
                {{ workbench?.customerCount ?? 0 }}
              </view>
            </view>
          </view>
        </view>

        <!-- 推广码 -->
        <view class="card">
          <view class="title">
            {{ t('sales.workbench.qrcodeTitle') }}
          </view>
          <view class="qrcode-wrap">
            <image
              v-if="qrcodeUrl"
              class="qrcode-image"
              :src="qrcodeUrl"
              mode="aspectFit"
              @longpress="saveQrcode"
            />
            <view v-else class="qrcode-placeholder">
              <text class="i-carbon:qrcode qrcode-placeholder__icon" />
              <view class="qrcode-placeholder__text">
                {{ qrcodeLoading ? t('sales.workbench.qrcodeLoading') : t('sales.workbench.qrcodeUnavailable') }}
              </view>
              <view v-if="qrcodeFailed && !qrcodeLoading" class="qrcode-retry" @click="retryQrcode">
                {{ t('sales.workbench.qrcodeRetry') }}
              </view>
            </view>
          </view>
          <view class="qrcode-hint">
            {{ t('sales.workbench.qrcodeHint') }}
          </view>
          <button class="primary-btn" :disabled="!qrcodeUrl" @click="saveQrcode">
            {{ t('sales.workbench.saveToAlbum') }}
          </button>
        </view>

        <!-- 我的客户 -->
        <view class="card">
          <view class="title">
            {{ t('sales.workbench.customersTitle') }}
          </view>
          <view v-if="customers.length === 0" class="empty">
            {{ t('sales.workbench.customersEmpty') }}
          </view>
          <view
            v-for="item in customers"
            v-else
            :key="item.customerUserId"
            class="customer-item"
          >
            <image
              v-if="item.avatar"
              class="customer-avatar"
              :src="item.avatar"
              mode="aspectFill"
            />
            <view v-else class="customer-avatar customer-avatar--fallback">
              <text class="i-carbon:user-avatar customer-avatar__icon" />
            </view>
            <view class="customer-body">
              <view class="customer-name">
                {{ item.nickname || '-' }}
              </view>
              <view class="customer-meta">
                {{ t('sales.workbench.bindSourceLabel', { source: getSalesBindSourceText(item.bindSource) }) }}
                <text v-if="item.bindTime">
                  · {{ t('sales.workbench.bindTimeLabel', { time: item.bindTime }) }}
                </text>
              </view>
            </view>
          </view>
          <button
            v-if="customers.length < customerPage.total"
            class="load-more-btn"
            :disabled="customerPage.loading"
            @click="loadCustomers()"
          >
            {{ customerPage.loading ? t('common.loading') : t('sales.workbench.loadMore') }}
          </button>
        </view>

        <!-- 业绩明细 -->
        <view class="card">
          <view class="title">
            {{ t('sales.workbench.ordersTitle') }}
          </view>
          <view v-if="orders.length === 0" class="empty">
            {{ t('sales.workbench.ordersEmpty') }}
          </view>
          <view
            v-for="item in orders"
            v-else
            :key="item.id"
            class="record-item"
          >
            <view class="record-main">
              <view class="record-order">
                {{ item.orderNo || item.id }}
              </view>
              <view class="record-time">
                {{ t('sales.workbench.orderPayTime', { time: item.payTime || '-' }) }}
              </view>
              <view class="record-time">
                {{ t('sales.workbench.orderCommissionBase', { amount: formatMoney(item.commissionBase) }) }}
              </view>
            </view>
            <view class="record-right">
              <view class="record-amount">
                +{{ formatMoney(item.commissionAmount) }}
              </view>
              <view
                class="record-status"
                :class="getSalesOrderStatusClass(item.status)"
              >
                {{ getSalesOrderStatusText(item.status) }}
              </view>
            </view>
          </view>
          <button
            v-if="orders.length < orderPage.total"
            class="load-more-btn"
            :disabled="orderPage.loading"
            @click="loadOrders()"
          >
            {{ orderPage.loading ? t('common.loading') : t('sales.workbench.loadMore') }}
          </button>
        </view>
      </template>

      <view class="page-bottom-space" />
    </view>
  </view>
</template>

<style scoped lang="scss">
.sales-page {
  margin: 24rpx;
}

/* ===== 状态卡（非销售 / 加载失败） ===== */

.state-card {
  margin-top: 80rpx;
  border-radius: 16rpx;
  background: var(--app-card);
  padding: 80rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.state-icon {
  font-size: 96rpx;
  color: var(--app-text-3);
}

.state-title {
  margin-top: 24rpx;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--app-text);
}

.state-desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: var(--app-text-3);
  text-align: center;
}

.state-btn {
  margin-top: 32rpx;
  min-width: 240rpx;
  height: 72rpx;
  line-height: 72rpx;
  border-radius: 999rpx;
  font-size: 26rpx;
  color: var(--app-text-2);
  background: var(--app-card-2);
}

/* ===== 汇总卡片 ===== */

.summary-card {
  border-radius: 20rpx;
  padding: 32rpx 28rpx;
  background: linear-gradient(135deg, #ff5f56, #ff2d55);
  color: #fff;
}

.summary-label {
  font-size: 24rpx;
  opacity: 0.85;
}

.summary-main {
  margin-top: 12rpx;
  font-size: 56rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
}

.summary-grid {
  margin-top: 28rpx;
  padding-top: 24rpx;
  border-top: 1px solid rgba(255, 255, 255, 0.28);
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20rpx;
}

.summary-item__label {
  font-size: 22rpx;
  opacity: 0.8;
}

.summary-item__value {
  margin-top: 6rpx;
  font-size: 32rpx;
  font-weight: 600;
}

.card {
  margin-top: 20rpx;
  border-radius: 16rpx;
  background: var(--app-card);
  padding: 28rpx 24rpx;
}

.title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--app-text);
}

/* ===== 推广码 ===== */

.qrcode-wrap {
  margin-top: 24rpx;
  display: flex;
  justify-content: center;
}

.qrcode-image {
  width: 360rpx;
  height: 360rpx;
  border-radius: 12rpx;
  background: #fff;
}

.qrcode-placeholder {
  width: 360rpx;
  height: 360rpx;
  border-radius: 12rpx;
  background: var(--app-card-2);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.qrcode-placeholder__icon {
  font-size: 72rpx;
  color: var(--app-text-3);
}

.qrcode-placeholder__text {
  margin-top: 16rpx;
  font-size: 24rpx;
  color: var(--app-text-3);
}

.qrcode-retry {
  margin-top: 16rpx;
  font-size: 24rpx;
  color: #ff5f56;
}

.qrcode-hint {
  margin-top: 16rpx;
  text-align: center;
  font-size: 22rpx;
  color: var(--app-text-3);
}

.primary-btn {
  margin-top: 24rpx;
  width: 100%;
  height: 78rpx;
  line-height: 78rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
  color: #fff;
  background: linear-gradient(135deg, #ff5f56, #ff2d55);
}

.primary-btn[disabled] {
  opacity: 0.5;
}

/* ===== 我的客户 ===== */

.customer-item {
  padding: 20rpx 0;
  border-bottom: 1px solid var(--app-border);
  display: flex;
  align-items: center;
}

.customer-item:last-child {
  border-bottom: 0;
}

.customer-avatar {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  margin-right: 20rpx;
  flex: none;
  background: var(--app-card-2);
}

.customer-avatar--fallback {
  display: flex;
  align-items: center;
  justify-content: center;
}

.customer-avatar__icon {
  font-size: 40rpx;
  color: var(--app-text-3);
}

.customer-body {
  flex: 1;
  min-width: 0;
}

.customer-name {
  font-size: 28rpx;
  color: var(--app-text);
}

.customer-meta {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--app-text-3);
}

/* ===== 业绩明细 ===== */

.record-item {
  padding: 20rpx 0;
  border-bottom: 1px solid var(--app-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.record-item:last-child {
  border-bottom: 0;
}

.record-main {
  flex: 1;
  min-width: 0;
}

.record-order {
  font-size: 26rpx;
  color: var(--app-text);
}

.record-time {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--app-text-3);
}

.record-right {
  margin-left: 16rpx;
  text-align: right;
}

.record-amount {
  font-size: 28rpx;
  font-weight: 600;
  color: #67c23a;
}

.record-status {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: var(--app-text-3);
}

.status-pending {
  color: #e6a23c;
}

.status-success {
  color: #67c23a;
}

.status-rejected {
  color: #f56c6c;
}

.empty {
  padding: 24rpx 0 8rpx;
  text-align: center;
  font-size: 26rpx;
  color: var(--app-text-3);
}

.load-more-btn {
  width: 100%;
  height: 72rpx;
  margin-top: 12rpx;
  border-radius: 8rpx;
  background: var(--app-card-2);
  color: var(--app-text-2);
  font-size: 26rpx;
  line-height: 72rpx;
}

.page-bottom-space {
  height: 40rpx;
}
</style>
