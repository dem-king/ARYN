<script setup lang="ts">
interface Props {
  modelValue: boolean

}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'share'): void
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

function onShare() {
  emit('share')
}
</script>

<template>
  <wd-action-sheet v-model="show" title="分享至" custom-class="action-sheet-custom" @close="onClose">
    <view class="show-content-container">
      <!-- #ifdef MP-WEIXIN -->
      <view class="show-content-item">
        <view class="share-icon-warp">
          <wd-button type="icon" open-type="share" icon="share" custom-style="color:#fff" />
        </view>
        <view>
          <wd-button type="text" open-type="share" custom-style="color:#333333">
            分享好友
          </wd-button>
        </view>
      </view>
      <!-- #endif -->
      <!-- #ifdef H5 -->
      <view class="show-content-item" @click="onShare">
        <view class="share-icon-warp">
          <wd-button type="icon" open-type="share" icon="share" custom-style="color:#fff" />
        </view>
        <view>
          <wd-button type="text" open-type="share" custom-style="color:#333333">
            分享链接
          </wd-button>
        </view>
      </view>
      <!-- #endif -->
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
    padding: 0 30px;
    display: flex;
    flex-direction: column;
    align-items: center;

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
