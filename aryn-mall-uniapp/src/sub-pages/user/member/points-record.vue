<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getPointsExchanges, getPointsRecordPage, redeemPointsExchange } from '@/api/user/points'
import type { PointsExchangeItem, PointsRecord } from '@/api/user/points'

definePage({
  name: 'member-points-record',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '积分记录',
  },
})

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
  return `${expiring} 积分将于 ${formatDate(nearest)} 到期，请注意使用`
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
    parts.push(`${ex.couponDiscount} 折券`)
  }
  else if (ex.couponAmount) {
    parts.push(ex.couponThreshold && ex.couponThreshold > 0
      ? `满 ${ex.couponThreshold} 减 ${ex.couponAmount} 元`
      : `${ex.couponAmount} 元无门槛券`)
  }
  else {
    parts.push(ex.title)
  }
  if (ex.perUserLimit > 0) {
    parts.push(`每人限兑 ${ex.perUserLimit} 次`)
  }
  const exchanged = ex.exchangedCount ?? 0
  if (ex.perUserLimit > 0) {
    parts.push(`已兑 ${exchanged}/${ex.perUserLimit}`)
  }
  else if (exchanged > 0) {
    parts.push(`已兑 ${exchanged} 次`)
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
    title: '积分兑换',
    content: `确认用 ${ex.pointsCost} 积分兑换「${ex.couponName || ex.title}」？兑换后发放至「我的优惠券」。`,
    success: (res) => {
      if (!res.confirm)
        return
      doRedeem(ex)
    },
  })
}

async function doRedeem(ex: PointsExchangeItem) {
  redeeming.value = true
  globalLoading.loading('兑换中...')
  try {
    await redeemPointsExchange(ex.id)
    uni.showToast({ title: '兑换成功', icon: 'success' })
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
  globalLoading.loading('加载中...')
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
  return item.expireTime ? `将于 ${formatDate(item.expireTime)} 到期` : ''
}

function getChangeTypeText(type: string): string {
  return type === '1' ? '获取' : '消耗'
}

function getChangeTypeClass(type: string): string {
  return type === '1' ? 'text-green-500' : 'text-red-500'
}

function getSceneText(scene: string): string {
  const sceneMap: Record<string, string> = {
    ORDER: '下单',
    ORDER_COMPLETE: '订单完成',
    SIGN_IN: '签到',
    EXCHANGE: '兑换',
    RECHARGE: '充值',
    REFUND: '退款',
    ADMIN: '后台调整',
    ADMIN_ADJUST: '后台调整',
    REGISTER: '注册奖励',
    ORDER_DEDUCT: '积分抵现',
    POINTS_EXPIRE: '积分过期',
    POINTS_EXCHANGE: '积分兑换',
    ORDER_REFUND: '退款返还',
  }
  return sceneMap[scene] || scene
}
</script>

<template>
  <z-paging ref="pagingRef" v-model="list" :auto="false" @query="queryList">
    <template #top>
      <hr-navbar title="积分记录" />
    </template>
    <!-- 积分总览：余额 + 30 天内即将过期提醒（增强信息，缺失时不渲染） -->
    <view v-if="pointsOverview" class="m-2 rounded-xl bg-white px-4 py-3">
      <view class="flex items-center justify-between">
        <text class="text-14px font-bold">当前积分</text>
        <text class="text-20px font-bold">{{ pointsOverview.point }}</text>
      </view>
      <view v-if="expiringNotice" class="mt-1 text-12px text-orange-500">
        {{ expiringNotice }}
      </view>
    </view>
    <!-- 积分兑换：无启用项整块隐藏；不可兑置灰并提示原因 -->
    <view v-if="exchanges.length" class="m-2 rounded-xl bg-white px-4 py-3">
      <view class="flex items-center justify-between">
        <text class="text-14px font-bold">积分兑换</text>
        <text class="text-11px text-gray-400">兑换后发放至「我的优惠券」</text>
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
          <text class="text-14px font-bold text-orange-500">{{ ex.pointsCost }} 积分</text>
          <view
            class="mt-1 rounded-full px-3 py-1 text-12px"
            :class="ex.exchangeable ? 'bg-orange-500 text-white' : 'bg-gray-200 text-gray-400'"
            @tap="onRedeem(ex)"
          >
            {{ ex.exchangeable ? '兑换' : (ex.unexchangeableReason || '不可兑换') }}
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
            余额 {{ item.balanceAfter }}
          </view>
        </view>
      </view>
    </view>
  </z-paging>
</template>
