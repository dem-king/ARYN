<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'
import { getPage } from '@/api/promotion/couponUser'
import { couponStatusMeta, formatCouponExpiry } from '@/utils/coupon-display'

definePage({
  name: 'coupon-user',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '我的优惠券',
  },
})
const pagingRef = ref()
const state = reactive<{ list: any[], queryParams: any }>({
  list: [],
  queryParams: {
    status: '',
  },
})
const tabs = reactive({
  statusList: [
    {
      name: '全部',
    },
    {
      name: '待使用',
    },
    {
      name: '已使用',
    },
    {
      name: '已过期',
    },
    {
      name: '冻结中',
    },
  ],

  tabCurrent: 0,
})
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
 */
function cardView(item: any) {
  const expires = formatCouponExpiry(expirySource(item))
  const usable = item.status === '0' && expires !== '已过期'
  if (usable)
    return { band: expires, tone: 'active' as const, action: '去使用' }
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
  globalLoading.loading('加载中...')
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
      <hr-navbar title="我的优惠券" />
      <wd-tabs v-model="tabs.tabCurrent" @change="changeTab">
        <block v-for="(item, index) in tabs.statusList" :key="index">
          <wd-tab :title="item.name" />
        </block>
      </wd-tabs>
    </template>
    <wd-notice-bar
      v-if="tabs.tabCurrent === 4" text="使用优惠券的订单未支付，优惠券会被冻结哦" :scrollable="false"
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
