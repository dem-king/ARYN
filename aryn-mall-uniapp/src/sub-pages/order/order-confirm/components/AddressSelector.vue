<script setup lang="ts">
interface Address {
  id: string
  detailAddress: string
  recipientName: string
  telephone: string
  provinceName: string
  cityName: string
  areaName: string
}

interface Props {
  selectedAddress: Address | null
}

defineProps<Props>()

const emit = defineEmits<{
  toAddress: []
}>()

function toAddress() {
  emit('toAddress')
}
</script>

<template>
  <view class="mb-2 rounded-b-xl bg-white p-2">
    <view class="flex items-center justify-between" @click="toAddress">
      <view class="flex items-center">
        <wd-icon name="location" size="22px" />
        <view v-if="selectedAddress" class="pl-10px">
          <view>
            <text class="text-14px">
              {{ selectedAddress.recipientName }}
            </text>
            <text class="pl-10px text-14px">
              {{ selectedAddress.telephone }}
            </text>
          </view>
          <view class="py-8px text-13px">
            {{ selectedAddress.provinceName }} {{ selectedAddress.cityName }} {{ selectedAddress.areaName }}
            {{ selectedAddress.detailAddress }}
          </view>
        </view>
        <view v-else class="p-3">
          请选择收货地址
        </view>
      </view>

      <view>
        <wd-icon name="arrow-right" size="28rpx" />
      </view>
    </view>
  </view>
</template>
