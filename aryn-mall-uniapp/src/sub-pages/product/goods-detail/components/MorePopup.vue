<script setup lang="ts">
interface Props {
  modelValue: boolean
  collectId: string
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'toHome'): void
  (e: 'collect'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const show = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

function onClose() {
  emit('update:modelValue', false)
}

function onToHome() {
  emit('toHome')
}

function onCollect() {
  emit('collect')
}
</script>

<template>
  <wd-action-sheet v-model="show" title="更多功能" custom-class="action-sheet-custom" @close="onClose">
    <view class="show-content-container">
      <view class="show-content-item">
        <view class="pic-icon-warp" @click="onToHome">
          <wd-icon name="home" color="#ffffff" size="38rpx" />
        </view>
        <view>
          <wd-text size="22rpx" text="返回首页" />
        </view>
      </view>
      <view class="show-content-item">
        <view class="share-icon-warp" @click="onCollect">
          <wd-icon :name="collectId ? 'star-filled' : 'star'" color="#ffffff" size="38rpx" />
        </view>
        <view>
          <wd-text size="22rpx" :text="collectId ? '取消收藏' : '收藏'" />
        </view>
      </view>
    </view>
  </wd-action-sheet>
</template>

<style lang="scss" scoped>
.action-sheet-custom {
  margin: 0 10px calc(var(--window-bottom) + 10px) 10px !important;
  border-radius: 16px !important;
  background: transparent !important;
}

.show-content-container {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 20rpx;

  .show-content-item {
    padding: 0 40px;
    display: flex;
    flex-direction: column;
    align-items: center;

    .pic-icon-warp {
      background-color: #ff3d36;
      border-radius: 50%;
      width: 60rpx;
      /* 确保宽高相等 */
      height: 60rpx;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .share-icon-warp {
      background-color: #fe560a;
      border-radius: 50%;
      width: 60rpx;
      /* 确保宽高相等 */
      height: 60rpx;
      display: flex;
      align-items: center;
      justify-content: center;
    }
  }
}
</style>
