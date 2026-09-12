<script setup lang="ts">
/**
 * 首页船舶工作台
 *
 * 展示当前船舶、下一靠港与配送时间窗，并提供船供采购入口。
 * 登录用户加载本人作为成员的在营船舶；未登录或无成员关系时隐藏。
 */
import { onMounted, ref } from 'vue'

import { getMyVessels, getVesselContext } from '@/api/vessel'
import { useAuthStore } from '@/store/authStore'
import { useShipContextStore } from '@/store/shipContextStore'

const authStore = useAuthStore()
const shipContextStore = useShipContextStore()
const visible = ref(false)

async function loadWorkbench() {
  if (!authStore.isLoggedIn) {
    visible.value = false
    return
  }
  try {
    const vessels = await getMyVessels()
    if (!vessels || vessels.length === 0) {
      visible.value = false
      return
    }
    visible.value = true
    if (!shipContextStore.hasVesselContext) {
      shipContextStore.setVesselContext({
        vesselId: vessels[0].id,
        vesselName: vessels[0].vesselName,
      })
      const context = await getVesselContext(vessels[0].id)
      if (context?.vesselCallId) {
        shipContextStore.setVesselCall({
          berth: context.berth,
          deliveryWindowEnd: context.deliveryWindowEnd,
          deliveryWindowStart: context.deliveryWindowStart,
          id: context.vesselCallId,
          portCode: context.portCode,
          portName: context.portName,
        })
      }
    }
  } catch {
    visible.value = false
  }
}

function goShipSupply() {
  shipContextStore.setPurchaseScene('2')
  uni.navigateTo({ url: '/sub-pages/product/ship-supply?scene=2' })
}

function goPersonal() {
  shipContextStore.setPurchaseScene('1')
  uni.switchTab({ url: '/pages/product/index' })
}

function goFrequent() {
  uni.navigateTo({ url: '/sub-pages/product/frequent' })
}

onMounted(loadWorkbench)
</script>

<template>
  <view v-if="visible" class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-30rpx">
    <view class="flex items-center justify-between">
      <view class="text-30rpx font-bold">
        {{ shipContextStore.vesselName || '我的船舶' }}
      </view>
      <view
        v-if="shipContextStore.hasVesselContext"
        class="rounded-full bg-green-100 px-16rpx py-4rpx text-22rpx text-green-600"
      >
        已选靠港
      </view>
    </view>
    <view class="mt-10rpx text-24rpx text-gray-500">
      <template v-if="shipContextStore.hasVesselContext">
        下一靠港：{{ shipContextStore.portName }}
        {{ shipContextStore.berth }}
        <text v-if="shipContextStore.deliveryWindowStart">
          （{{ shipContextStore.deliveryWindowStart }} 起）
        </text>
      </template>
      <template v-else>
        尚未选择靠港计划，结算前请完成选择
      </template>
    </view>
    <view class="mt-24rpx flex gap-20rpx">
      <button
        class="!m-0 flex-1 rounded-40rpx bg-blue-500 text-26rpx text-white"
        @tap="goShipSupply"
      >
        船供采购
      </button>
      <button
        class="!m-0 flex-1 rounded-40rpx bg-gray-100 text-26rpx text-gray-700"
        @tap="goPersonal"
      >
        个人购买
      </button>
      <button
        class="!m-0 flex-1 rounded-40rpx bg-amber-100 text-26rpx text-amber-700"
        @tap="goFrequent"
      >
        常购
      </button>
    </view>
  </view>
</template>
