<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getPage } from '@/api/promotion/couponUser'
import { couponStatusMeta, daysUntil, formatCouponExpiry } from '@/utils/coupon-display'

definePage({
  name: 'coupon-user',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '我的优惠券',
  },
})
const { t } = useI18n()
const pagingRef = ref()
const state = reactive<{ list: any[], queryParams: any }>({
  list: [],
  queryParams: {
    status: '',
  },
})
/** 状态筛选 Tab：文案随语言切换，索引语义保持不变（0=全部、1=待使用…） */
const tabs = reactive({
  tabCurrent: 0,
})
const statusTabs = computed(() => [
  { name: t('promotion.coupon.user.tabs.all') },
  { name: t('promotion.coupon.user.tabs.available') },
  { name: t('promotion.coupon.user.tabs.used') },
  { name: t('promotion.coupon.user.tabs.expired') },
  { name: t('promotion.coupon.user.tabs.frozen') },
])
const router = useRouter()
const globalLoading = useGlobalLoading()
function changeTab({ index }: any) {
  state.queryParams.status = `${index - 1}`
  pagingRef.value?.reload()
}
function toGoodsList() {
  router.push({
    name: 'goods-list',
  })
}

/** 券本身的有效期优先，缺失时回落到券模板的领取截止时间 */
function expirySource(item: any) {
  return item.validatTime || item.couponInfo?.receiveEndedAt
}

/**
 * 一张券记录的展示口径：状态带文案 + 色调 + 动作文案。
 *
 * 服务端 `status=2` 表示记录已过期，但按「全部/待使用」筛选时仍可能返回
 * status=0 而有效期已过的券（过期靠定时任务回写，存在时间差），
 * 因此以有效期为准判定，避免出现「待使用」却写着「已过期」的矛盾卡片。
 * 判定只看 `daysUntil` 的数值，不比对已翻译的状态文案（切语言后仍成立）。
 */
function cardView(item: any) {
  const source = expirySource(item)
  const expires = formatCouponExpiry(source)
  const days = daysUntil(source)
  const usable = item.status === '0' && !(days !== null && days < 0)
  if (usable)
    return { band: expires, tone: 'active' as const, action: t('promotion.coupon.user.use') }
  if (item.status === '3')
    return { band: couponStatusMeta('3').label, tone: 'frozen' as const, action: '' }
  return { band: expires || couponStatusMeta(item.status).label, tone: 'muted' as const, action: '' }
}

onLoad((options) => {
  // 从「我的」页带 status 进来时直接落到对应 tab（-1 表示全部）
  if (options?.status) {
    tabs.tabCurrent = Number(options.status)
    state.queryParams.status = options.status
  }
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
      status: state.queryParams.status === '-1' ? '' : state.queryParams.status,
    })
    pagingRef.value?.complete(response.records)
  }
  finally {
    globalLoading.close()
  }
}
/** 列表 → 券卡视图模型，避免模板里重复计算 */
const cards = computed(() => state.list.map(item => ({ item, ...cardView(item) })))

function handleAction() {
  toGoodsList()
}
</script>

<template>
  <z-paging ref="pagingRef" v-model="state.list" :auto="false" @query="queryList">
    <template #top>
      <hr-navbar :title="t('promotion.coupon.user.title')" />
      <wd-tabs v-model="tabs.tabCurrent" @change="changeTab">
        <block v-for="(item, index) in statusTabs" :key="index">
          <wd-tab :title="item.name" />
        </block>
      </wd-tabs>
    </template>
    <wd-notice-bar
      v-if="tabs.tabCurrent === 4" :text="t('promotion.coupon.user.frozenNotice')" :scrollable="false"
      prefix="warn-bold"
    />

    <view v-for="(card, index) in cards" :key="index" class="coupon-user__item">
      <coupon-card
        :coupon="card.item.couponInfo || {}"
        :band="card.band"
        :action-text="card.action"
        :tone="card.tone"
        @action="handleAction"
      />
    </view>
  </z-paging>
</template>

<style lang="scss" scoped>
.coupon-user__item {
  padding: 20rpx 24rpx 0;
}
</style>
