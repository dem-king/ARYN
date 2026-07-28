<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { uploadDeliveryEvidence } from '@/api/upload'
import { useDeliveryStore } from '@/store/delivery'

const store = useDeliveryStore()
const taskId = ref('')
const reasonCode = ref('CONTACT_FAILED')
const description = ref('')
const materialIds = ref<string[]>([])
const localFiles = ref<string[]>([])
const submitting = ref(false)
const reasons = [
  { label: '联系不上客户', value: 'CONTACT_FAILED' },
  { label: '地址异常', value: 'ADDRESS_INVALID' },
  { label: '商品异常', value: 'GOODS_EXCEPTION' },
  { label: '其他原因', value: 'OTHER' },
]

onLoad(async (query) => {
  taskId.value = String(query?.id || '')
  if (taskId.value)
    await store.loadTask(taskId.value)
})

async function addImages() {
  const result = await new Promise<UniApp.ChooseMediaSuccessCallbackResult>((resolve, reject) => {
    uni.chooseMedia({ count: Math.min(6 - materialIds.value.length, 6), mediaType: ['image'], sourceType: ['camera', 'album'], success: resolve, fail: reject })
  })
  for (const file of result.tempFiles) {
    try {
      materialIds.value.push(await uploadDeliveryEvidence(file.tempFilePath))
      localFiles.value.push(file.tempFilePath)
    }
    catch (error) {
      uni.showToast({ title: error instanceof Error ? error.message : '上传失败', icon: 'none' })
      break
    }
  }
}

async function submit() {
  if (!store.currentTask || !description.value.trim()) {
    uni.showToast({ title: '请填写异常说明', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await store.submitException(store.currentTask, reasonCode.value, description.value.trim(), materialIds.value)
    uni.showToast({ title: '异常已上报', icon: 'success' })
    uni.redirectTo({ url: '/pages/tasks/index' })
  }
  catch (error) {
    if (store.handleMutationFailure(error))
      uni.redirectTo({ url: '/pages/tasks/index' })
    else
      uni.showToast({ title: error instanceof Error ? error.message : '上报失败', icon: 'none' })
  }
  finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="page">
    <text class="label">异常类型</text>
    <view class="reasons"><text v-for="reason in reasons" :key="reason.value" class="reason" :class="{ active: reasonCode === reason.value }" @click="reasonCode = reason.value">{{ reason.label }}</text></view>
    <text class="label block">异常说明</text>
    <textarea v-model="description" class="textarea" maxlength="300" placeholder="说明现场情况、已联系客户的结果等" />
    <view class="section-head"><text class="label">现场图片（选填）</text><text>{{ materialIds.length }}/6</text></view>
    <view class="grid"><image v-for="src in localFiles" :key="src" :src="src" mode="aspectFill" /><view v-if="materialIds.length < 6" class="add" @click="addImages">+ 添加图片</view></view>
    <view class="action-bar"><button class="danger" :disabled="!description.trim() || submitting" :loading="submitting" @click="submit">确认上报异常</button></view>
  </view>
</template>

<style scoped lang="scss">
.page { box-sizing: border-box; min-height: 100vh; padding: 32rpx 28rpx 170rpx; }.label { color: #475569; font-size: 25rpx; font-weight: 650; }.block { display: block; margin-top: 38rpx; }.reasons { display: flex; flex-wrap: wrap; gap: 16rpx; margin-top: 20rpx; }.reason { padding: 18rpx 22rpx; color: #64748b; font-size: 24rpx; background: #fff; border-radius: 16rpx; }.reason.active { color: #b91c1c; background: #fee2e2; }.textarea { box-sizing: border-box; width: 100%; height: 240rpx; margin-top: 18rpx; padding: 26rpx; font-size: 26rpx; background: #fff; border-radius: 22rpx; }.section-head { display: flex; justify-content: space-between; margin-top: 38rpx; color: #94a3b8; font-size: 24rpx; }.grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16rpx; margin-top: 18rpx; }.grid image, .add { width: 100%; aspect-ratio: 1; border-radius: 20rpx; }.add { display: grid; box-sizing: border-box; color: #64748b; font-size: 23rpx; background: #fff; border: 2rpx dashed #94a3b8; place-content: center; }.action-bar { position: fixed; right: 0; bottom: 0; left: 0; padding: 20rpx 28rpx calc(20rpx + env(safe-area-inset-bottom)); background: #fff; }.danger { color: #fff; font-size: 28rpx; background: #dc2626; border-radius: 20rpx; }.danger[disabled] { background: #fca5a5; }
</style>
