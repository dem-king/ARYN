<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getBalanceRecordPage } from '@/api/user/balance'

definePage({
  name: 'member-balance-record',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '余额明细',
  },
})

interface BalanceRecord {
  id: string
  changeType: number
  changeAmount: number
  balanceAfter: number
  triggerScene: string
  remark: string
  createTime: string
}

const list = ref<BalanceRecord[]>([])
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
    const response = await getBalanceRecordPage({
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
  return type === 1 ? '充值' : type === 2 ? '消费' : '调整'
}

function getChangeTypeClass(type: number): string {
  return type === 1 ? 'text-green-500' : 'text-red-500'
}

function getSceneText(scene: string): string {
  const sceneMap: Record<string, string> = {
    RECHARGE: '充值',
    CONSUME: '消费',
    REFUND: '退款',
    ADMIN: '后台调整',
    GIFT: '赠送',
  }
  return sceneMap[scene] || scene
}
</script>

<template>
  <z-paging ref="pagingRef" v-model="list" :auto="false" @query="queryList">
    <template #top>
      <hr-navbar title="余额明细" />
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
            {{ item.changeType === 1 ? '+' : '-' }}{{ item.changeAmount }}
          </view>
          <view class="mt-1 text-11px text-gray-400">
            余额 {{ item.balanceAfter }}
          </view>
        </view>
      </view>
    </view>
  </z-paging>
</template>
