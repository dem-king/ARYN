<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { getPointsExchanges, getPointsRecordPage, redeemPointsExchange } from '@/api/user/points'
import type { PointsExchangeItem, PointsRecord } from '@/api/user/points'

definePage({
  name: 'member-points-record',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '积分记录',
  },
})

const { t } = useI18n()
const list = ref<PointsRecord[]>([])
const exchanges = ref<PointsExchangeItem[]>([])
const redeeming = ref(false)
const globalLoading = useGlobalLoading()
const userStore = useUserStore()
const pagingRef = ref()

/** 积分总览（余额 + 即将过期汇总）：复用 store 缓存并保持与其他页面同步 */
const pointsOverview = computed(() => userStore.pointsInfo)

/**
 * 30 天内即将过期提醒：后端 UserPointsInfoVO 按剩余可用口径汇总，
 * expiringPoints>0 且有最近到期批次（nearestExpireTime）时才展示。
 */
const expiringNotice = computed(() => {
  const expiring = Number(pointsOverview.value?.expiringPoints)
  const nearest = pointsOverview.value?.nearestExpireTime
  if (!Number.isFinite(expiring) || expiring <= 0 || !nearest)
    return ''
  return t('accountMember.pointsRecord.expiringNotice', {
    points: expiring,
    date: formatDate(nearest),
  })
})

onLoad(() => {
  nextTick(() => {
    pagingRef.value?.reload()
  })
})

/** 兑换列表与明细同页加载：无启用项时整块隐藏 */
async function loadExchanges() {
  try {
    exchanges.value = (await getPointsExchanges()) ?? []
  }
  catch (error) {
    console.error('加载积分兑换失败:', error)
    exchanges.value = []
  }
}

/** 券面额描述：满减/折扣/无门槛，附限兑与已兑次数 */
function exchangeDesc(ex: PointsExchangeItem): string {
  const parts: string[] = []
  if (ex.couponType === '2' && ex.couponDiscount) {
    // 中文按「8.5 折券」表达，英文按「15% OFF coupon」表达，两种语言的口径不同
    parts.push(t('accountMember.pointsRecord.discountCoupon', {
      discount: ex.couponDiscount,
      percentOff: Math.round((10 - Number(ex.couponDiscount)) * 10),
    }))
  }
  else if (ex.couponAmount) {
    parts.push(ex.couponThreshold && ex.couponThreshold > 0
      ? t('accountMember.pointsRecord.thresholdCoupon', {
          threshold: ex.couponThreshold,
          amount: ex.couponAmount,
        })
      : t('accountMember.pointsRecord.noThresholdCoupon', { amount: ex.couponAmount }))
  }
  else {
    parts.push(ex.title)
  }
  if (ex.perUserLimit > 0) {
    parts.push(t('accountMember.pointsRecord.perUserLimit', { limit: ex.perUserLimit }))
  }
  const exchanged = ex.exchangedCount ?? 0
  if (ex.perUserLimit > 0) {
    parts.push(t('accountMember.pointsRecord.exchangedOfLimit', {
      exchanged,
      limit: ex.perUserLimit,
    }))
  }
  else if (exchanged > 0) {
    parts.push(t('accountMember.pointsRecord.exchangedTimes', { times: exchanged }))
  }
  return parts.join(' · ')
}

function onRedeem(ex: PointsExchangeItem) {
  if (!ex.exchangeable || redeeming.value) {
    if (!ex.exchangeable && ex.unexchangeableReason) {
      uni.showToast({ title: ex.unexchangeableReason, icon: 'none' })
    }
    return
  }
  uni.showModal({
    title: t('accountMember.pointsRecord.redeemTitle'),
    content: t('accountMember.pointsRecord.redeemConfirm', {
      points: ex.pointsCost,
      name: ex.couponName || ex.title,
    }),
    success: (res) => {
      if (!res.confirm)
        return
      doRedeem(ex)
    },
  })
}

async function doRedeem(ex: PointsExchangeItem) {
  redeeming.value = true
  globalLoading.loading(t('accountMember.pointsRecord.redeeming'))
  try {
    await redeemPointsExchange(ex.id)
    uni.showToast({ title: t('accountMember.pointsRecord.redeemSuccess'), icon: 'success' })
    // 兑换改变积分余额/兑换次数/明细，三处同步刷新
    await Promise.allSettled([loadExchanges(), userStore.refreshPointsInfo()])
    pagingRef.value?.reload()
  }
  catch (error) {
    console.error('积分兑换失败:', error)
  }
  finally {
    globalLoading.close()
    redeeming.value = false
  }
}

async function queryList(pageNo: number, pageSize: number) {
  globalLoading.loading(t('common.loading'))
  try {
    const response = await getPointsRecordPage({
      current: pageNo,
      size: pageSize,
    })
    pagingRef.value?.complete(response.records)
    // 首页/下拉刷新时同步总览与兑换列表（失败静默不影响明细列表）
    if (pageNo === 1) {
      userStore.refreshPointsInfo().catch(() => {})
      loadExchanges()
    }
  }
  catch (error) {
    // 失败必须告知 z-paging，否则列表永远停在加载态
    console.error('加载积分记录失败:', error)
    pagingRef.value?.complete(false)
  }
  finally {
    globalLoading.close()
  }
}

/** 后端 LocalDateTime 序列化为 yyyy-MM-dd HH:mm:ss，取前 10 位为 YYYY-MM-DD */
function formatDate(dateStr: string): string {
  return dateStr ? dateStr.slice(0, 10) : ''
}

/** 单条记录到期提示：仅获取类流水下发 expireTime，null=永久不展示 */
function expireNotice(item: PointsRecord): string {
  return item.expireTime
    ? t('accountMember.pointsRecord.expireAt', { date: formatDate(item.expireTime) })
    : ''
}

function getChangeTypeText(type: string): string {
  return type === '1'
    ? t('accountMember.pointsRecord.earned')
    : t('accountMember.pointsRecord.spent')
}

function getChangeTypeClass(type: string): string {
  return type === '1' ? 'text-green-500' : 'text-red-500'
}

const sceneKeyMap: Record<string, string> = {
  ORDER: 'sceneOrder',
  ORDER_COMPLETE: 'sceneOrderComplete',
  SIGN_IN: 'sceneSignIn',
  EXCHANGE: 'sceneExchange',
  RECHARGE: 'sceneRecharge',
  REFUND: 'sceneRefund',
  ADMIN: 'sceneAdmin',
  ADMIN_ADJUST: 'sceneAdmin',
  REGISTER: 'sceneRegister',
  ORDER_DEDUCT: 'sceneOrderDeduct',
  POINTS_EXPIRE: 'scenePointsExpire',
  POINTS_EXCHANGE: 'scenePointsExchange',
  ORDER_REFUND: 'sceneOrderRefund',
}

function getSceneText(scene: string): string {
  const key = sceneKeyMap[scene]
  return key ? t(`accountMember.pointsRecord.${key}`) : scene
}
</script>

<template>
  <z-paging ref="pagingRef" v-model="list" :auto="false" @query="queryList">
    <template #top>
      <hr-navbar :title="t('accountMember.pointsRecord.title')" />
    </template>
    <!-- 积分总览：余额 + 30 天内即将过期提醒（增强信息，缺失时不渲染） -->
    <view v-if="pointsOverview" class="m-2 rounded-xl bg-white px-4 py-3">
      <view class="flex items-center justify-between">
        <text class="text-14px font-bold">{{ t('accountMember.pointsRecord.currentPoints') }}</text>
        <text class="text-20px font-bold">{{ pointsOverview.point }}</text>
      </view>
      <view v-if="expiringNotice" class="mt-1 text-12px text-orange-500">
        {{ expiringNotice }}
      </view>
    </view>
    <!-- 积分兑换：无启用项整块隐藏；不可兑置灰并提示原因 -->
    <view v-if="exchanges.length" class="m-2 rounded-xl bg-white px-4 py-3">
      <view class="flex items-center justify-between">
        <text class="text-14px font-bold">{{ t('accountMember.pointsRecord.exchange') }}</text>
        <text class="text-11px text-gray-400">{{ t('accountMember.pointsRecord.exchangeHint') }}</text>
      </view>
      <view
        v-for="ex in exchanges" :key="ex.id"
        class="mt-2 flex items-center justify-between rounded-lg bg-gray-50 px-3 py-2"
      >
        <view class="flex-1">
          <view class="text-13px font-bold">
            {{ ex.couponName || ex.title }}
          </view>
          <view class="mt-1 text-11px text-gray-400">
            {{ exchangeDesc(ex) }}
          </view>
        </view>
        <view class="ml-2 flex flex-col items-end">
          <text class="text-14px font-bold text-orange-500">{{ t('accountMember.pointsRecord.pointsCost', { points: ex.pointsCost }) }}</text>
          <view
            class="mt-1 rounded-full px-3 py-1 text-12px"
            :class="ex.exchangeable ? 'bg-orange-500 text-white' : 'bg-gray-200 text-gray-400'"
            @tap="onRedeem(ex)"
          >
            {{ ex.exchangeable ? t('accountMember.pointsRecord.redeem') : (ex.unexchangeableReason || t('accountMember.pointsRecord.unavailable')) }}
          </view>
        </view>
      </view>
    </view>
    <view v-for="(item, index) in list" :key="index" class="m-2 rounded-xl bg-white px-4 py-3">
      <view class="flex items-center justify-between">
        <view class="flex-1">
          <view class="flex items-center">
            <text class="text-14px font-bold">{{ getSceneText(item.triggerScene) }}</text>
            <text class="ml-2 text-12px rounded px-1 py-0.5" :class="item.changeType === '1' ? 'bg-green-50 text-green-500' : 'bg-red-50 text-red-500'">
              {{ getChangeTypeText(item.changeType) }}
            </text>
          </view>
          <view v-if="item.remark" class="mt-1 text-12px text-gray-400">
            {{ item.remark }}
          </view>
          <view class="mt-1 text-11px text-gray-300">
            {{ item.createTime }}
          </view>
          <!-- 该批次积分到期提示：永久积分（null）不展示 -->
          <view v-if="expireNotice(item)" class="mt-1 text-11px text-orange-500">
            {{ expireNotice(item) }}
          </view>
        </view>
        <view class="text-right">
          <view class="text-16px font-bold" :class="getChangeTypeClass(item.changeType)">
            {{ item.changeType === '1' ? '+' : '-' }}{{ item.changePoint }}
          </view>
          <view class="mt-1 text-11px text-gray-400">
            {{ t('accountMember.pointsRecord.balanceAfter', { balance: item.balanceAfter }) }}
          </view>
        </view>
      </view>
    </view>
  </z-paging>
</template>
