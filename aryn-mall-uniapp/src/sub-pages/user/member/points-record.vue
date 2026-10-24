<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getPointsRecordPage } from '@/api/user/points'
import type { PointsRecord } from '@/api/user/points'

definePage({
  name: 'member-points-record',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '积分记录',
  },
})

const list = ref<PointsRecord[]>([])
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

async function queryList(pageNo: number, pageSize: number) {
  globalLoading.loading('加载中...')
  try {
    const response = await getPointsRecordPage({
      current: pageNo,
      size: pageSize,
    })
    pagingRef.value?.complete(response.records)
    // 首页/下拉刷新时同步总览（去重 Promise，失败静默不影响明细列表）
    if (pageNo === 1) {
      userStore.refreshPointsInfo().catch(() => {})
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
