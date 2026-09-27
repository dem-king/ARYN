<script setup lang="ts">
import type { CouponCardData } from '@/components/coupon-card/index.vue'
import { onLoad } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { getPage } from '@/api/promotion/couponInfo'
import { addObj } from '@/api/promotion/couponUser'
import { formatCouponDeadline } from '@/utils/coupon-display'

definePage({
  name: 'coupon-list',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '领券中心',
  },
})
const pagingRef = ref()
const state = reactive<{ list: any[], hotCouponList: any[] }>({
  list: [],
  hotCouponList: [],
})
const globalLoading = useGlobalLoading()
const { show } = useGlobalToast()
onLoad(async () => {
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
    })
    pagingRef.value?.complete(response.records)
  }
  finally {
    globalLoading.close()
  }
}
/** 是否已达领取上限：服务端按用户返回已领取次数 */
function isReceived(item: any) {
  return Number(item.userReceiveCount) > 0
}
/**
 * 已领取的券按钮是「去使用」，仍然可点（原来就是这样跳商品列表）。
 * 注意不能把它置灰：置灰的「去使用」等于告诉用户领了也用不了。
 */
function actionText(item: any) {
  return isReceived(item) ? '去使用' : '立即领取'
}
/** 领券截止时间当作状态带文案；没有截止时间则整条不显示 */
function bandText(item: any) {
  return formatCouponDeadline(item.receiveEndedAt)
}

const router = useRouter()

/** 未领取则领取，已领取则去挑商品 */
async function handleAction(coupon: CouponCardData) {
  const item = state.list.find(entry => entry.id === coupon.id)
  if (item && isReceived(item)) {
    router.push({ name: 'goods-list' })
    return
  }
  try {
    await addObj({ couponId: coupon.id })
    show('领取成功')
    // 本地累加已领次数，卡片即时切到「去使用」，不必重拉列表
    if (item)
      item.userReceiveCount = Number(item.userReceiveCount || 0) + 1
  }
  catch (err: any) {
    // 服务端失败原因已由请求层统一提示；这里兜底补一条，避免静默失败
    show(err?.message || '领取失败')
  }
}
</script>

<template>
  <z-paging ref="pagingRef" v-model="state.list" :auto="false" @query="queryList">
    <template #top>
      <hr-navbar title="领券中心" />
    </template>
    <view v-for="item in state.list" :key="item.id" class="coupon-list__item">
      <coupon-card
        :coupon="item"
        :band="bandText(item)"
        :action-text="actionText(item)"
        @action="handleAction"
      />
    </view>
  </z-paging>
</template>

<style lang="scss" scoped>
.coupon-list__item {
  padding: 20rpx 24rpx 0;
}
</style>
