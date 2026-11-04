<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { delAddress, getPage, saveOrUpdateAddress } from '@/api/user/address'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { useMallThemeStore } from '@/store/mallThemeStore'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'address-list',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '收货地址',
  },
})

interface AddressItem {
  id: string
  recipientName?: string
  telephone?: string
  provinceName?: string
  cityName?: string
  areaName?: string
  detailAddress?: string
  isDefault?: string
}

const pagingRef = ref()
const { t } = useI18n()
const router = useRouter()
const { confirm } = useGlobalMessage()
const { show: showToast } = useGlobalToast()
const mallThemeStore = useMallThemeStore()

// 默认标签的浅色底：从当前商城主题色派生（小程序不支持 color-mix，需预计算）
const softBrand = computed(() => {
  const color = mallThemeStore.primaryColor
  return /^#[\dA-F]{6}$/i.test(color) ? `${color}17` : 'rgba(255, 34, 55, 0.09)'
})

const addressList = ref<AddressItem[]>([])
// 从结算页进入时为选择模式：点整卡回传地址并返回
const isChoosing = ref(false)
const placeChooseId = ref('')
const defaultSavingId = ref('')
const deletingId = ref('')

onLoad((options) => {
  isChoosing.value = String(options?.placeChooseFlag) === 'true'
  placeChooseId.value = options?.placeChooseId ?? ''
})
onShow(() => {
  nextTick(() => {
    pagingRef.value?.reload()
  })
})

async function queryList(pageNo: number, pageSize: number) {
  try {
    const response = await getPage({
      current: pageNo,
      size: pageSize,
    })
    pagingRef.value?.complete(response.records)
  }
  catch {
    // 走 z-paging 的失败态（可点击重试），不要停在加载中
    pagingRef.value?.complete(false)
  }
}

// 直辖市省市同名时去重，与表单页口径一致
function fullAddress(item: AddressItem) {
  const city = item.cityName && item.cityName !== item.provinceName ? item.cityName : ''
  return [item.provinceName, city, item.areaName, item.detailAddress]
    .filter(Boolean)
    .join(' ')
}

function toForm(id = '') {
  router.push({
    name: 'address-form',
    params: {
      id,
    },
  })
}

function handleCardTap(item: AddressItem) {
  if (isChoosing.value) {
    selectAddress(item)
    return
  }
  if (item.id) {
    toForm(item.id)
  }
}

function selectAddress(item: AddressItem) {
  uni.$emit('update:selectedAddress', item)
  // 走 router.back：实例上的 navigation lock 会拦掉首屏无效返回与连点重复返回
  router.back()
}

async function setDefault(item: AddressItem) {
  if (!item.id || item.isDefault === '1' || defaultSavingId.value) {
    return
  }
  const previousDefaults = addressList.value.filter(
    entry => entry.isDefault === '1' && entry.id !== item.id,
  )
  const snapshot = addressList.value.map(entry => ({ id: entry.id, isDefault: entry.isDefault }))

  // 乐观切换：默认地址全局唯一，先把本地标记整体归零再点亮目标
  defaultSavingId.value = item.id
  addressList.value.forEach((entry) => {
    entry.isDefault = entry.id === item.id ? '1' : '0'
  })

  try {
    // 后端服务层会统一清除旧默认；这里一并复位旧默认，兼容尚未升级的后端（幂等）
    await Promise.all([
      ...previousDefaults.map(entry => saveOrUpdateAddress({ ...entry, isDefault: '0' })),
      saveOrUpdateAddress({ ...item, isDefault: '1' }),
    ])
  }
  catch {
    snapshot.forEach(({ id, isDefault }) => {
      const target = addressList.value.find(entry => entry.id === id)
      if (target) {
        target.isDefault = isDefault
      }
    })
    showToast(t('accountSettings.address.setDefaultFailed'))
  }
  finally {
    defaultSavingId.value = ''
  }
}

function del(item: AddressItem) {
  confirm({
    title: t('accountSettings.address.deleteTitle'),
    msg: t('accountSettings.address.deleteConfirm'),
    closeOnClickModal: false,
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel'),
    success: (res) => {
      if (res.action === 'confirm') {
        removeAddress(item)
      }
    },
  })
}

async function removeAddress(item: AddressItem) {
  if (!item.id || deletingId.value) {
    return
  }
  deletingId.value = item.id
  try {
    await delAddress(item.id)
    const index = addressList.value.findIndex(entry => entry.id === item.id)
    if (index >= 0) {
      addressList.value.splice(index, 1)
    }

    // 删掉默认地址后自动继承：列表还有地址且没有其他默认时，把第一条设为默认
    if (
      item.isDefault === '1'
      && addressList.value.length
      && !addressList.value.some(entry => entry.isDefault === '1')
    ) {
      const next = addressList.value[0]!
      next.isDefault = '1'
      try {
        await saveOrUpdateAddress({ ...next, isDefault: '1' })
      }
      catch {
        next.isDefault = '0'
      }
    }
  }
  catch {
    showToast(t('accountSettings.address.deleteFailed'))
  }
  finally {
    deletingId.value = ''
  }
}
</script>

<template>
  <hr-navbar :title="isChoosing ? t('accountSettings.address.chooseNavTitle') : t('accountSettings.address.navTitle')" />
  <z-paging
    ref="pagingRef"
    v-model="addressList"
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
      <view class="ad-empty">
        <view class="ad-empty__badge">
          <text class="i-carbon:location ad-empty__icon" />
        </view>
        <text class="ad-empty__title">{{ t('accountSettings.address.empty') }}</text>
        <text class="ad-empty__hint">{{ t('accountSettings.address.emptyHint') }}</text>
        <view class="ad-empty__btn" hover-class="ad-empty__btn--pressed" @click="toForm('')">
          {{ t('accountSettings.address.addNew') }}
        </view>
      </view>
    </template>

    <view v-if="addressList.length" class="ad-list">
      <view
        v-for="item in addressList"
        :key="item.id"
        class="ad-card"
        :class="{
          'ad-card--active': isChoosing && placeChooseId === item.id,
          'ad-card--busy': deletingId === item.id,
        }"
        hover-class="ad-card--pressed"
        :hover-stay-time="80"
        @click="handleCardTap(item)"
      >
        <view class="ad-card__main">
          <view class="ad-card__head">
            <text class="ad-card__name">{{ item.recipientName }}</text>
            <text class="ad-card__phone">{{ item.telephone }}</text>
            <view v-if="item.isDefault === '1'" class="ad-card__badge" :style="{ backgroundColor: softBrand }">
              {{ t('accountSettings.address.defaultTag') }}
            </view>
          </view>
          <view class="ad-card__addr">
            {{ fullAddress(item) }}
          </view>
          <view class="ad-card__foot">
            <view
              class="ad-card__default"
              :class="{ 'is-on': item.isDefault === '1', 'is-busy': defaultSavingId === item.id }"
              hover-class="ad-card__default--pressed"
              :hover-stay-time="80"
              @tap.stop="setDefault(item)"
            >
              <text
                class="ad-card__tick"
                :class="item.isDefault === '1' ? 'i-carbon:checkmark-filled' : 'i-carbon:radio-button'"
              />
              <text>
                {{ item.isDefault === '1' ? t('accountSettings.address.alreadyDefault') : t('accountSettings.address.setDefault') }}
              </text>
            </view>
            <view class="ad-card__actions">
              <view class="ad-card__action" hover-class="ad-card__action--pressed" :hover-stay-time="80" @tap.stop="toForm(item.id)">
                <text class="i-carbon:edit ad-card__action-icon" />
                <text>{{ t('accountSettings.address.edit') }}</text>
              </view>
              <view class="ad-card__action" hover-class="ad-card__action--pressed" :hover-stay-time="80" @tap.stop="del(item)">
                <text class="i-carbon:trash-can ad-card__action-icon" />
                <text>{{ t('accountSettings.address.delete') }}</text>
              </view>
            </view>
          </view>
        </view>
        <text
          v-if="isChoosing"
          class="ad-card__check"
          :class="placeChooseId === item.id ? 'i-carbon:checkmark-filled is-checked' : 'i-carbon:radio-button'"
        />
      </view>
    </view>

    <template #bottom>
      <view v-if="addressList.length" class="ad-bottom">
        <view class="ad-bottom__btn" hover-class="ad-bottom__btn--pressed" :hover-stay-time="80" @click="toForm('')">
          <text class="i-carbon:add ad-bottom__icon" />
          <text>{{ t('accountSettings.address.addNew') }}</text>
        </view>
      </view>
    </template>
  </z-paging>
</template>

<style lang="scss" scoped>
$ad-brand: var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237));
$ad-ink: var(--app-text);
$ad-sub: var(--app-text-2);
$ad-muted: var(--app-text-3);
$ad-hairline: var(--app-border);

.ad-list {
  padding: 24rpx 24rpx 32rpx;
}

.ad-card {
  display: flex;
  align-items: stretch;
  padding: 28rpx 28rpx 24rpx;
  margin-bottom: 20rpx;
  border: 2rpx solid transparent;
  border-radius: 24rpx;
  background: var(--app-card);
  box-shadow: 0 6rpx 24rpx rgba(17, 24, 39, 0.04);
  box-sizing: border-box;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}

.ad-card--pressed {
  background: var(--app-card-2);
}

.ad-card--active {
  border-color: $ad-brand;
}

.ad-card--busy {
  opacity: 0.6;
}

.ad-card__main {
  flex: 1;
  min-width: 0;
}

.ad-card__head {
  display: flex;
  align-items: baseline;
  min-width: 0;
}

.ad-card__name {
  max-width: 40%;
  overflow: hidden;
  color: $ad-ink;
  font-size: 32rpx;
  font-weight: 600;
  line-height: 44rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ad-card__phone {
  margin-left: 16rpx;
  color: var(--app-text-3);
  font-size: 26rpx;
  line-height: 40rpx;
  font-variant-numeric: tabular-nums;
}

.ad-card__badge {
  flex: none;
  margin-left: 12rpx;
  padding: 2rpx 12rpx;
  border-radius: 8rpx;
  background: var(--wot-color-theme-primary-soft, rgba(255, 34, 55, 0.09));
  color: $ad-brand;
  font-size: 20rpx;
  font-weight: 500;
  line-height: 30rpx;
}

.ad-card__addr {
  display: -webkit-box;
  overflow: hidden;
  margin-top: 12rpx;
  color: $ad-sub;
  font-size: 26rpx;
  line-height: 38rpx;
  word-break: break-all;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.ad-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid $ad-hairline;
}

.ad-card__default {
  display: flex;
  align-items: center;
  min-height: 48rpx;
  padding-right: 16rpx;
  color: var(--app-text-2);
  font-size: 24rpx;
}

.ad-card__default--pressed {
  opacity: 0.7;
}

.ad-card__default.is-on {
  color: $ad-brand;
  font-weight: 500;
}

.ad-card__default.is-busy {
  opacity: 0.6;
}

.ad-card__tick {
  margin-right: 8rpx;
  font-size: 30rpx;
}

.ad-card__actions {
  display: flex;
  align-items: center;
  flex: none;
}

.ad-card__action {
  display: flex;
  align-items: center;
  height: 48rpx;
  margin-left: 12rpx;
  padding: 0 20rpx;
  border-radius: 999rpx;
  background: var(--app-card-2);
  color: var(--app-text-2);
  font-size: 22rpx;
}

.ad-card__action--pressed {
  background: var(--app-card-pressed);
}

.ad-card__action-icon {
  margin-right: 6rpx;
  font-size: 24rpx;
}

.ad-card__check {
  display: flex;
  align-items: center;
  align-self: center;
  flex: none;
  margin-left: 20rpx;
  color: var(--app-text-3);
  font-size: 40rpx;
}

.ad-card__check.is-checked {
  color: $ad-brand;
}

.ad-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 64rpx 80rpx;
}

.ad-empty__badge {
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

.ad-empty__icon {
  font-size: 72rpx;
}

.ad-empty__title {
  margin-top: 32rpx;
  color: $ad-ink;
  font-size: 30rpx;
  font-weight: 600;
}

.ad-empty__hint {
  margin-top: 12rpx;
  color: $ad-muted;
  font-size: 24rpx;
  line-height: 1.6;
  text-align: center;
}

.ad-empty__btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 80rpx;
  margin-top: 40rpx;
  padding: 0 56rpx;
  border-radius: 999rpx;
  background: $ad-brand;
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
}

.ad-empty__btn--pressed {
  opacity: 0.86;
}

.ad-bottom {
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  border-top: 1rpx solid $ad-hairline;
  background: var(--app-card);
}

.ad-bottom__btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  border-radius: 999rpx;
  background: $ad-brand;
  color: #fff;
  font-size: 30rpx;
  font-weight: 600;
}

.ad-bottom__btn--pressed {
  opacity: 0.9;
}

.ad-bottom__icon {
  margin-right: 8rpx;
  font-size: 32rpx;
}
</style>
