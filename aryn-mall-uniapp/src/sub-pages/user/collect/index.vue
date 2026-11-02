<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { deleteObj, getPage } from '@/api/product/collect'
import { shouldShowOriginalPrice, splitPrice } from '@/components/diy/price-display'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { formatShortDateTime } from '@/utils/history-display'
import { stockHintText } from '@/utils/stock-display'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'user-collect',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '我的收藏',
  },
})

interface CollectSpu {
  spuUrls?: string[]
  name?: string
  salesPrice?: number | string
  originalPrice?: number | string
  stock?: number | string
}

interface CollectItem {
  id: string
  spuId?: string
  goodsSpu?: CollectSpu | null
  salesPrice?: number | string
  createTime?: string
}

interface CollectCardView {
  item: CollectItem
  pic: string
  name: string
  available: boolean
  price: ReturnType<typeof splitPrice>
  originalPrice: number | null
  showOriginal: boolean
  soldOut: boolean
  stockTag: string
  timeText: string
}

const { t } = useI18n()
const list = ref<CollectItem[]>([])
const router = useRouter()
const globalLoading = useGlobalLoading()
const userStore = useUserStore()
const { show: showToast } = useGlobalToast()
const pagingRef = ref()
const removingId = ref('')

onLoad(async () => {
  nextTick(() => {
    pagingRef.value?.reload()
  })
})

async function queryList(pageNo: number, pageSize: number) {
  globalLoading.loading(t('common.loading'))
  try {
    const response = await getPage({
      current: pageNo,
      size: pageSize,
      desc: 'create_time',
    })
    pagingRef.value?.complete(response.records)
  }
  finally {
    globalLoading.close()
  }
}

const cards = computed<CollectCardView[]>(() =>
  list.value.map((item) => {
    const spu = item.goodsSpu
    const available = Boolean(spu?.name)
    const stock = Number(spu?.stock)
    const soldOut = available && Number.isFinite(stock) && stock <= 0
    const originalPrice = Number(spu?.originalPrice)
    const savedTime = formatShortDateTime(item.createTime) || item.createTime || ''
    return {
      item,
      pic: spu?.spuUrls?.[0] || '',
      name: available ? String(spu?.name) : t('accountSettings.collect.unavailable'),
      available,
      price: available ? splitPrice(spu?.salesPrice) : null,
      originalPrice: Number.isFinite(originalPrice) ? originalPrice : null,
      showOriginal: available && shouldShowOriginalPrice(spu?.salesPrice, spu?.originalPrice),
      soldOut,
      stockTag: available && !soldOut ? stockHintText(spu?.stock) : '',
      timeText: savedTime
        ? t('accountSettings.collect.collectedAt', { time: savedTime })
        : '',
    }
  }),
)

function openItem(card: CollectCardView) {
  if (!card.available || !card.item.spuId)
    return
  router.push({
    name: 'goods-detail',
    params: {
      id: card.item.spuId,
    },
  })
}

async function removeCollect(card: CollectCardView) {
  if (!card.item.id || removingId.value)
    return
  removingId.value = card.item.id
  try {
    await deleteObj(card.item.id)
    const index = list.value.findIndex(entry => entry.id === card.item.id)
    if (index >= 0)
      list.value.splice(index, 1)
    userStore.decrementCollectCount(1)
    showToast(t('accountSettings.collect.removed'))
  }
  catch (error) {
    console.error('取消收藏失败:', error)
  }
  finally {
    removingId.value = ''
  }
}

function toGoodsList() {
  router.push({ name: 'goods-list' })
}
</script>

<template>
  <!-- eslint-disable vue/singleline-html-element-content-newline -->
  <hr-navbar :title="t('accountSettings.collect.navTitle')" />
  <z-paging
    ref="pagingRef"
    v-model="list"
    :auto="false"
    :refresher-default-text="t('catalog.refresherPull')"
    :refresher-pulling-text="t('catalog.refresherRelease')"
    :refresher-refreshing-text="t('catalog.refresherRefreshing')"
    :refresher-complete-text="t('catalog.refresherDone')"
    :loading-more-default-text="t('catalog.loadMore')"
    :loading-more-loading-text="t('common.loading')"
    :loading-more-fail-text="t('catalog.loadMoreFailed')"
    :loading-more-no-more-text="t('catalog.noMore')"
    @query="queryList"
    @scroll="handlePageScroll"
  >
    <!-- 顶栏覆盖层等高占位：列表整屏滚动，首屏内容从顶栏下沿开始 -->
    <hr-navbar-space />

    <template #empty>
      <view class="cl-empty">
        <view class="cl-empty__badge">
          <text class="i-carbon:star cl-empty__icon" />
        </view>
        <text class="cl-empty__title">{{ t('accountSettings.collect.empty') }}</text>
        <text class="cl-empty__hint">{{ t('accountSettings.collect.emptyHint') }}</text>
        <view class="cl-empty__btn" hover-class="cl-empty__btn--pressed" @click="toGoodsList">
          {{ t('accountSettings.collect.goShopping') }}
        </view>
      </view>
    </template>

    <view v-if="cards.length" class="cl-list">
      <view
        v-for="card in cards"
        :key="card.item.id"
        class="cl-card"
        :hover-class="card.available ? 'cl-card--pressed' : 'none'"
        :hover-stay-time="60"
        @click="openItem(card)"
      >
        <view class="cl-card__pic-wrap">
          <image v-if="card.pic" class="cl-card__pic" :src="card.pic" mode="aspectFill" />
          <view v-else class="cl-card__pic cl-card__pic--empty">
            <text class="i-carbon:image cl-card__pic-icon" />
          </view>
          <view v-if="card.soldOut" class="cl-card__mask">{{ t('labels.soldOut') }}</view>
        </view>
        <view class="cl-card__body">
          <text class="cl-card__name" :class="{ 'cl-card__name--unavailable': !card.available }">{{ card.name }}</text>
          <view v-if="card.price" class="cl-card__price">
            <text class="cl-card__currency">¥</text>
            <text class="cl-card__amount">{{ card.price.integer }}</text>
            <text class="cl-card__decimal">.{{ card.price.decimal }}</text>
            <text v-if="card.showOriginal" class="cl-card__original">¥{{ card.originalPrice }}</text>
            <text v-if="card.stockTag" class="cl-card__stock">{{ card.stockTag }}</text>
          </view>
          <view class="cl-card__foot">
            <view v-if="card.timeText" class="cl-card__time">
              <text class="i-carbon:time cl-card__time-icon" />
              <text class="cl-card__time-text">{{ card.timeText }}</text>
            </view>
            <view
              class="cl-card__remove"
              :class="{ 'cl-card__remove--busy': removingId === card.item.id }"
              hover-class="cl-card__remove--pressed"
              @click.stop="removeCollect(card)"
            >
              {{ removingId === card.item.id ? t('accountSettings.collect.removing') : t('accountSettings.collect.remove') }}
            </view>
          </view>
        </view>
      </view>
    </view>
  </z-paging>
</template>

<style lang="scss" scoped>
$cl-brand: var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237));
$cl-price: #fa2c19;
$cl-ink: var(--app-text);
$cl-muted: var(--app-text-3);

.cl-list {
  padding: 20rpx 24rpx calc(32rpx + env(safe-area-inset-bottom));
}

.cl-card {
  display: flex;
  padding: 24rpx;
  margin-bottom: 20rpx;
  background: var(--app-card);
  border-radius: 24rpx;
  box-shadow: 0 6rpx 24rpx rgba(17, 24, 39, 0.04);
  transition: background-color 0.15s ease;
}

.cl-card--pressed {
  background: var(--app-card-2);
}

.cl-card__pic-wrap {
  position: relative;
  width: 168rpx;
  height: 168rpx;
  flex: none;
}

.cl-card__pic {
  display: block;
  width: 168rpx;
  height: 168rpx;
  border-radius: 16rpx;
  background: var(--app-card-2);
}

.cl-card__pic--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--app-text-3);
}

.cl-card__pic-icon {
  font-size: 48rpx;
}

.cl-card__mask {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16rpx;
  background: rgba(17, 24, 39, 0.44);
  color: #fff;
  font-size: 22rpx;
  font-weight: 500;
}

.cl-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  margin-left: 20rpx;
}

.cl-card__name {
  display: -webkit-box;
  overflow: hidden;
  color: $cl-ink;
  font-size: 28rpx;
  font-weight: 500;
  line-height: 38rpx;
  word-break: break-all;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.cl-card__name--unavailable {
  color: $cl-muted;
}

.cl-card__price {
  display: flex;
  align-items: baseline;
  margin-top: 10rpx;
}

.cl-card__currency {
  color: $cl-price;
  font-size: 22rpx;
  font-weight: 600;
}

.cl-card__amount {
  color: $cl-price;
  font-size: 34rpx;
  font-weight: 700;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.cl-card__decimal {
  color: $cl-price;
  font-size: 24rpx;
  font-weight: 600;
}

.cl-card__original {
  margin-left: 12rpx;
  color: var(--app-text-3);
  font-size: 22rpx;
  text-decoration: line-through;
}

.cl-card__stock {
  margin-left: auto;
  padding: 2rpx 12rpx;
  border-radius: 8rpx;
  background: #fff7e8;
  color: #b45309;
  font-size: 20rpx;
  line-height: 30rpx;
}

.cl-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 12rpx;
}

.cl-card__time {
  display: flex;
  align-items: center;
  min-width: 0;
  color: $cl-muted;
}

.cl-card__time-icon {
  flex: none;
  font-size: 22rpx;
}

.cl-card__time-text {
  overflow: hidden;
  margin-left: 6rpx;
  color: $cl-muted;
  font-size: 22rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cl-card__remove {
  flex: none;
  margin-left: 16rpx;
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  background: var(--app-card-2);
  color: var(--app-text-2);
  font-size: 22rpx;
  line-height: 32rpx;
}

.cl-card__remove--pressed {
  background: var(--app-card-pressed);
}

.cl-card__remove--busy {
  opacity: 0.6;
}

.cl-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 64rpx 80rpx;
}

.cl-empty__badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 176rpx;
  height: 176rpx;
  border-radius: 50%;
  background: var(--app-card);
  box-shadow: 0 8rpx 24rpx rgba(17, 24, 39, 0.05);
  color: var(--app-text-3);
}

.cl-empty__icon {
  font-size: 72rpx;
}

.cl-empty__title {
  margin-top: 32rpx;
  color: $cl-ink;
  font-size: 30rpx;
  font-weight: 600;
}

.cl-empty__hint {
  margin-top: 12rpx;
  color: $cl-muted;
  font-size: 24rpx;
  line-height: 1.6;
  text-align: center;
}

.cl-empty__btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 80rpx;
  margin-top: 40rpx;
  padding: 0 56rpx;
  border-radius: 999rpx;
  background: $cl-brand;
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
}

.cl-empty__btn--pressed {
  opacity: 0.86;
}
</style>
