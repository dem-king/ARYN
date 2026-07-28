<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { uploadDeliveryEvidence } from '@/api/upload'
import { requestDeliverySubscription } from '@/composables/useWechatSubscription'
import { useDeliveryStore, validateDeliveryEvidence } from '@/store/delivery'

const store = useDeliveryStore()
const taskId = ref('')
const localFiles = ref<string[]>([])
const uploading = ref(false)
const submitting = ref(false)
const draft = computed(() => store.evidenceDrafts[taskId.value])
const materialIds = computed(() => draft.value?.materialIds ?? [])

onLoad(async (query) => {
  taskId.value = String(query?.id || '')
  if (taskId.value)
    await store.loadTask(taskId.value)
})

async function chooseImages() {
  const remaining = 6 - materialIds.value.length
  if (remaining <= 0)
    return
  const result = await new Promise<UniApp.ChooseMediaSuccessCallbackResult>((resolve, reject) => {
    uni.chooseMedia({ count: remaining, mediaType: ['image'], sourceType: ['camera', 'album'], success: resolve, fail: reject })
  })
  uploading.value = true
  try {
    for (const file of result.tempFiles) {
      const materialId = await uploadDeliveryEvidence(file.tempFilePath)
      localFiles.value.push(file.tempFilePath)
      store.saveEvidenceDraft(taskId.value, [...materialIds.value, materialId])
    }
  }
  catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '上传失败', icon: 'none' })
  }
  finally {
    uploading.value = false
  }
}

function removeImage(index: number) {
  store.removeEvidenceDraftItem(taskId.value, index)
  localFiles.value.splice(index, 1)
}

async function submit() {
  if (!validateDeliveryEvidence(materialIds.value) || !store.currentTask)
    return
  submitting.value = true
  try {
    await store.submitDelivered(taskId.value, store.currentTask.version ?? 0)
    const templateId = import.meta.env.VITE_DELIVERY_SUBSCRIBE_TEMPLATE_ID
    if (templateId)
      await requestDeliverySubscription([templateId])
    uni.showToast({ title: '送达已提交', icon: 'success' })
    uni.redirectTo({ url: '/pages/tasks/index' })
  }
  catch (error) {
    if (store.handleMutationFailure(error))
      uni.redirectTo({ url: '/pages/tasks/index' })
    else
      uni.showToast({ title: '提交失败，凭证草稿已保留', icon: 'none' })
  }
  finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="page">
    <view class="tip"><text class="tip-title">送达凭证</text><text>请上传 1–6 张清晰照片。提交失败时素材草稿会保留，可直接重试。</text></view>
    <view class="grid">
      <view v-for="(materialId, index) in materialIds" :key="materialId" class="photo">
        <image v-if="localFiles[index]" :src="localFiles[index]" mode="aspectFill" />
        <view v-else class="uploaded">已上传<br>{{ index + 1 }}</view>
        <text class="remove" @click="removeImage(index)">×</text>
      </view>
      <view v-if="materialIds.length < 6" class="add" @click="chooseImages"><text class="plus">+</text><text>{{ uploading ? '上传中' : '拍照/相册' }}</text></view>
    </view>
    <text class="count">{{ materialIds.length }}/6 张</text>
    <view class="action-bar"><button class="primary" :disabled="!validateDeliveryEvidence(materialIds) || submitting" :loading="submitting" @click="submit">确认已送达</button></view>
  </view>
</template>

<style scoped lang="scss">
.page { box-sizing: border-box; min-height: 100vh; padding: 28rpx 28rpx 170rpx; }.tip { padding: 30rpx; color: #475569; font-size: 24rpx; line-height: 1.65; background: #fff; border-radius: 24rpx; }.tip-title { display: block; margin-bottom: 8rpx; color: #17233d; font-size: 30rpx; font-weight: 700; }.grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18rpx; margin-top: 28rpx; }.photo, .add { position: relative; overflow: hidden; aspect-ratio: 1; background: #e2e8f0; border-radius: 22rpx; }.photo image { width: 100%; height: 100%; }.uploaded, .add { display: grid; color: #64748b; font-size: 22rpx; text-align: center; place-content: center; }.add { box-sizing: border-box; background: #fff; border: 2rpx dashed #94a3b8; }.plus { display: block; margin-bottom: 8rpx; color: #2563eb; font-size: 52rpx; line-height: 1; }.remove { position: absolute; top: 8rpx; right: 8rpx; width: 42rpx; height: 42rpx; color: #fff; line-height: 38rpx; text-align: center; background: rgb(15 23 42 / 70%); border-radius: 999rpx; }.count { display: block; margin-top: 18rpx; color: #64748b; font-size: 24rpx; }.action-bar { position: fixed; right: 0; bottom: 0; left: 0; padding: 20rpx 28rpx calc(20rpx + env(safe-area-inset-bottom)); background: #fff; }.primary { color: #fff; font-size: 28rpx; background: #2563eb; border-radius: 20rpx; }.primary[disabled] { background: #94a3b8; }
</style>
