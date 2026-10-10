<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getBalanceRecordPage } from '@/sub-pages/api/user/balance'

definePage({
  name: 'member-balance-record',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '余额明细',
  },
})

interface BalanceRecord {
  id: string
  // 后端 AppBalanceRecordVO.changeType 是字符串：1-充值；2-消费；3-调整
  changeType: string
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
    })
    pagingRef.value?.complete(response.records)
  }
  catch (error) {
    // 失败必须告知 z-paging，否则列表永远停在加载态
    console.error('加载余额记录失败:', error)
    pagingRef.value?.complete(false)
  }
  finally {
    globalLoading.close()
  }
}

function getChangeTypeText(type: string): string {
  return type === '1' ? '充值' : type === '2' ? '消费' : '调整'
}

function getChangeTypeClass(type: string): string {
  return type === '2' ? 'text-red-500' : 'text-green-500'
}

// 金额符号由变动类型决定，调整单（3）按其正负号展示
function formatChangeAmount(item: BalanceRecord): string {
  const amount = Number(item.changeAmount ?? 0)
  const sign = item.changeType === '2' || amount < 0 ? '-' : '+'
  return `${sign}${Math.abs(amount)}`
}

function getSceneText(scene: string): string {
  const sceneMap: Record<string, string> = {
    RECHARGE: '充值',
    CONSUME: '消费',
    REFUND: '退款',
    ADMIN: '后台调整',
    ADMIN_ADJUST: '后台调整',
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
        </view>
        <view class="text-right">
          <view class="text-16px font-bold" :class="getChangeTypeClass(item.changeType)">
            {{ formatChangeAmount(item) }}
          </view>
          <view class="mt-1 text-11px text-gray-400">
            余额 {{ item.balanceAfter }}
          </view>
        </view>
      </view>
    </view>
  </z-paging>
</template>
