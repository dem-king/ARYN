<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getPointsRecordPage } from '@/api/user/points'

definePage({
  name: 'member-points-record',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '积分记录',
  },
})

interface PointsRecord {
  id: string
  changeType: number
  changePoint: number
  balanceAfter: number
  triggerScene: string
  remark: string
  createTime: string
}

const list = ref<PointsRecord[]>([])
const globalLoading = useGlobalLoading()
const pagingRef = ref()

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
      desc: 'create_time',
    })
    pagingRef.value?.complete(response.records)
  }
  finally {
    globalLoading.close()
  }
}

function getChangeTypeText(type: number): string {
  return type === 1 ? '获取' : '消耗'
}

function getChangeTypeClass(type: number): string {
  return type === 1 ? 'text-green-500' : 'text-red-500'
}

function getSceneText(scene: string): string {
  const sceneMap: Record<string, string> = {
    ORDER: '下单',
    SIGN_IN: '签到',
    EXCHANGE: '兑换',
    RECHARGE: '充值',
    REFUND: '退款',
    ADMIN: '后台调整',
  }
  return sceneMap[scene] || scene
}
</script>

<template>
  <z-paging ref="pagingRef" v-model="list" :auto="false" @query="queryList">
    <template #top>
      <hr-navbar title="积分记录" />
    </template>
    <view v-for="(item, index) in list" :key="index" class="m-2 rounded-xl bg-white px-4 py-3">
      <view class="flex items-center justify-between">
        <view class="flex-1">
          <view class="flex items-center">
            <text class="text-14px font-bold">{{ getSceneText(item.triggerScene) }}</text>
            <text class="ml-2 text-12px rounded px-1 py-0.5" :class="item.changeType === 1 ? 'bg-green-50 text-green-500' : 'bg-red-50 text-red-500'">
              {{ getChangeTypeText(item.changeType) }}
            </text>
          </view>
          <view v-if="item.remark" class="mt-1 text-12px text-gray-400">
            {{ item.remark }}
          </view>
          <view class="mt-1 text-11px text-gray-300">
            {{ item.createTime }}
          </view>
        </view>
        <view class="text-right">
          <view class="text-16px font-bold" :class="getChangeTypeClass(item.changeType)">
            {{ item.changeType === 1 ? '+' : '-' }}{{ item.changePoint }}
          </view>
          <view class="mt-1 text-11px text-gray-400">
            余额 {{ item.balanceAfter }}
          </view>
        </view>
      </view>
    </view>
  </z-paging>
</template>
