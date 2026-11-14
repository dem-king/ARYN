<script setup lang="ts">
/**
 * 凑单免运费页（结算页「满 X 元免运费，还差 Y 元」点击进入）。
 *
 * 职责边界：
 *   · 只做「找可凑单商品 + 加购 + 回结算页」，不在本页算任何金额；
 *   · 门槛与差额由结算页（服务端下发）通过 URL 带入，仅作文案展示；
 *   · 加购后把「已加购 N 件」告诉用户，真实运费必须回结算页由服务端重算后展示，
 *     避免前端按商品售价自行累计造成第二套口径。
 *
 * 商品池：只拉 `freight_type='2'`（随订单规则）的在售商品，按售价升序，
 * 因为只有这类商品会抬高满额包邮的门槛基数；包邮/固定运费商品加进来不改变差额。
 */
import { onLoad, onReachBottom, onUnload } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { getPage } from '@/api/product/spu'
import PriceText from '@/components/price-text/index.vue'
import QuickCartButton from '@/components/quick-cart-button/index.vue'
import { useGoodsStore } from '@/store/goodsStore'

definePage({
  name: 'order-fill-gap',
  style: {
    navigationBarTitleText: '凑单免运费',
  },
})

const PAGE_SIZE = 20
/** 加购后回到结算页时通知其重新结算（事件名与结算页监听保持一致） */
const REFRESH_EVENT = 'order-confirm:refresh'

const { t } = useI18n()
const router = useRouter()
const goodsStore = useGoodsStore()

const state = reactive({
  threshold: 0,
  gap: 0,
})

const goodsList = ref<any[]>([])
const loading = ref(false)
const finished = ref(false)
const loadFailed = ref(false)
const current = ref(1)
const addedCount = ref(0)

/** 金额展示：去掉无意义的末尾 0（只用于文案，不参与任何计算） */
function formatAmount(value: unknown) {
  const amount = Number(value)
  if (!Number.isFinite(amount) || amount <= 0)
    return ''
  return String(Number(amount.toFixed(2)))
}

const heroText = computed(() => {
  if (state.gap > 0 && formatAmount(state.gap)) {
    return t('checkout.fillGap.gapHint', { amount: formatAmount(state.gap) })
  }
  const threshold = formatAmount(state.threshold)
  return threshold
    ? t('checkout.fillGap.thresholdHint', { amount: threshold })
    : t('checkout.fillGap.title')
})

async function loadPage(reset = false) {
  if (loading.value)
    return
  if (!reset && finished.value)
    return
  if (reset) {
    current.value = 1
    finished.value = false
    goodsList.value = []
  }
  loading.value = true
  try {
    const response = await getPage({
      current: current.value,
      size: PAGE_SIZE,
      // 只拉参与满额包邮门槛的商品，售价升序让「最便宜的先看到」
      freightType: '2',
      asc: 'sales_price',
    })
    const records = response?.records ?? []
    goodsList.value = reset ? records : [...goodsList.value, ...records]
    finished.value = records.length < PAGE_SIZE
    current.value += 1
    loadFailed.value = false
  }
  catch (e) {
    // 拦截器已提示错误；保留已加载数据，允许用户上拉重试
    console.warn('[order-fill-gap] queryList failed', e)
    loadFailed.value = true
    finished.value = true
  }
  finally {
    loading.value = false
  }
}

onLoad((options) => {
  state.threshold = Number(options?.threshold ?? 0) || 0
  state.gap = Number(options?.gap ?? 0) || 0
  void loadPage(true)
})

onReachBottom(() => {
  void loadPage()
})

function toDetail(id: string) {
  if (!id)
    return
  router.push({ name: 'goods-detail', params: { id } })
}

function onAdded(spuId: string, detail?: { skuId?: string; quantity?: number; info?: any }) {
  const quantity = Number(detail?.quantity)
  // 件数按实际加购数量累计（多规格弹层可能一次加多件，按操作次数会少报）
  addedCount.value += Number.isFinite(quantity) && quantity > 0 ? quantity : 1
  const skuId = String(detail?.skuId || '')
  if (!skuId || !Number.isFinite(quantity) || quantity <= 0) {
    // 多规格弹层回传缺失时只计数，结算页仍可从购物车补齐（不阻断加购结果）
    return
  }
  const info = detail?.info || {}
  // 回写结算快照：结算页进入时把购物车勾选项快照到 goodsStore，
  // 只加购不同步这份快照，回结算页重算时新商品不会进入本单
  goodsStore.setGoodsList([
    ...goodsStore.createGoodsList,
    {
      skuId,
      spuId,
      quantity,
      spuName: info.name || '',
      picUrl: info.picUrl || '',
      specsInfo: info.specsInfo || '',
    },
  ])
}

/** 回结算页：返回后由结算页重新请求结算，运费与差额以服务端结果为准 */
function backToCheckout() {
  uni.navigateBack({
    delta: 1,
    fail: () => {
      // 页面栈异常（如从分享链接直达）时退到购物车，仍能继续下单
      uni.switchTab({ url: '/pages/user/shopping-cart/index' })
    },
  })
}

onUnload(() => {
  // 加购过商品才通知重算，避免纯浏览返回也触发一次多余结算请求
  if (addedCount.value > 0) {
    uni.$emit(REFRESH_EVENT)
  }
})
</script>

<template>
  <view class="fill-gap-page">
    <view class="gap-hero">
      <view class="gap-hero__icon">
        <wd-icon name="delivery" size="48rpx" color="#ffffff" />
      </view>
      <view class="gap-hero__body">
        <text class="gap-hero__title">
          {{ t('checkout.fillGap.title') }}
        </text>
        <text class="gap-hero__desc">
          {{ heroText }}
        </text>
      </view>
    </view>

    <view class="goods-head">
      <text class="goods-head__title">
        {{ t('checkout.fillGap.recommend') }}
      </text>
      <text class="goods-head__tip">
        {{ t('checkout.fillGap.recommendTip') }}
      </text>
    </view>

    <view class="goods-list">
      <view
        v-for="item in goodsList"
        :key="item.id"
        class="goods-row"
        hover-class="goods-row--pressed"
        :hover-stay-time="80"
        @tap="toDetail(item.id)"
      >
        <image class="goods-row__pic" :src="item.spuUrls?.[0]" mode="aspectFill" lazy-load />
        <view class="goods-row__info">
          <text class="goods-row__name">
            {{ item.name }}
          </text>
          <text v-if="item.subTitle" class="goods-row__sub">
            {{ item.subTitle }}
          </text>
          <view class="goods-row__price">
            <price-text :value="item.salesPrice" format="trim" />
          </view>
        </view>
        <quick-cart-button :spu-id="item.id" @added="onAdded">
          <view class="add-btn">
            {{ t('checkout.fillGap.add') }}
          </view>
        </quick-cart-button>
      </view>

      <view v-if="loading" class="state-text">
        {{ t('common.loading') }}
      </view>
      <view v-else-if="loadFailed" class="state-text" @tap="loadPage(true)">
        {{ t('checkout.fillGap.loadFailed') }}
      </view>
      <view v-else-if="!goodsList.length" class="empty">
        <text class="empty__title">
          {{ t('checkout.fillGap.empty') }}
        </text>
        <text class="empty__tip">
          {{ t('checkout.fillGap.emptyTip') }}
        </text>
      </view>
      <view v-else-if="finished" class="state-text state-text--muted">
        {{ t('catalog.noMore') }}
      </view>
    </view>

    <text class="footnote">
      {{ t('checkout.fillGap.footnote') }}
    </text>

    <view class="footer-safe" />
    <view class="footer">
      <view class="footer__info">
        <text v-if="addedCount > 0" class="footer__added">
          {{ t('checkout.fillGap.added', { count: addedCount }) }}
        </text>
        <text v-else class="footer__hint">
          {{ t('checkout.fillGap.footerHint') }}
        </text>
      </view>
      <view class="footer__btn" hover-class="footer__btn--pressed" @tap="backToCheckout">
        {{ t('checkout.fillGap.back') }}
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.fill-gap-page {
  min-height: 100vh;
  padding: 24rpx 24rpx 0;
  background-color: #f5f6f8;
  box-sizing: border-box;
}

.gap-hero {
  display: flex;
  gap: 20rpx;
  align-items: center;
  padding: 28rpx 28rpx;
  background: linear-gradient(135deg, #ff7a45 0%, #ff4d4f 100%);
  border-radius: 24rpx;
  box-shadow: 0 8rpx 20rpx rgba(255, 77, 79, 0.18);
}

.gap-hero__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  background-color: rgba(255, 255, 255, 0.18);
  border-radius: 50%;
  flex-shrink: 0;
}

.gap-hero__body {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  min-width: 0;
}

.gap-hero__title {
  font-size: 30rpx;
  font-weight: 600;
  color: #ffffff;
}

.gap-hero__desc {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.92);
}

.goods-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 28rpx 4rpx 16rpx;
}

.goods-head__title {
  font-size: 28rpx;
  font-weight: 600;
  color: #1d2129;
}

.goods-head__tip {
  font-size: 22rpx;
  color: #86909c;
}

.goods-list {
  overflow: hidden;
  background-color: #ffffff;
  border-radius: 24rpx;
}

.goods-row {
  display: flex;
  gap: 20rpx;
  align-items: center;
  padding: 20rpx 24rpx;
  border-bottom: 1rpx solid #f2f3f5;
}

.goods-row--pressed {
  background-color: #fafafa;
}

.goods-row__pic {
  width: 140rpx;
  height: 140rpx;
  background-color: #f7f8fa;
  border-radius: 16rpx;
  flex-shrink: 0;
}

.goods-row__info {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6rpx;
  min-width: 0;
}

.goods-row__name {
  overflow: hidden;
  font-size: 28rpx;
  font-weight: 500;
  color: #1d2129;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods-row__sub {
  overflow: hidden;
  font-size: 22rpx;
  color: #86909c;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods-row__price {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-top: 4rpx;
}

.add-btn {
  padding: 10rpx 26rpx;
  font-size: 24rpx;
  font-weight: 500;
  color: #ffffff;
  background-color: var(--ui-color-primary, #ff6b00);
  border-radius: 999rpx;
}

.state-text {
  padding: 32rpx;
  font-size: 24rpx;
  color: #86909c;
  text-align: center;
}

.state-text--muted {
  padding: 24rpx 32rpx 32rpx;
}

.empty {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  align-items: center;
  padding: 80rpx 48rpx;
  text-align: center;
}

.empty__title {
  font-size: 28rpx;
  color: #4e5969;
}

.empty__tip {
  font-size: 22rpx;
  line-height: 1.6;
  color: #86909c;
}

.footnote {
  display: block;
  padding: 20rpx 8rpx 0;
  font-size: 22rpx;
  line-height: 1.6;
  color: #86909c;
}

.footer-safe {
  height: calc(140rpx + constant(safe-area-inset-bottom));
  height: calc(140rpx + env(safe-area-inset-bottom));
}

.footer {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 10;
  display: flex;
  gap: 20rpx;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + constant(safe-area-inset-bottom));
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background-color: #ffffff;
  box-shadow: 0 -4rpx 16rpx rgba(29, 33, 41, 0.06);
}

.footer__info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.footer__added {
  font-size: 26rpx;
  font-weight: 500;
  color: #1d2129;
}

.footer__hint {
  font-size: 24rpx;
  color: #86909c;
}

.footer__btn {
  padding: 18rpx 40rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #ffffff;
  background-color: var(--ui-color-primary, #ff6b00);
  border-radius: 999rpx;
  flex-shrink: 0;
}

.footer__btn--pressed {
  opacity: 0.86;
}
</style>
