<script setup lang="ts">
import { computed, getCurrentInstance, nextTick, ref, watch } from 'vue'
import { checkDeliveryCaptcha, getDeliveryCaptcha, type DeliveryCaptchaData } from '@/api/auth'
import { buildCaptchaVerification, encryptCaptchaText, type CaptchaPoint } from '@/utils/captcha'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{
  close: []
  success: [captchaVerification: string]
}>()

const instance = getCurrentInstance()
const captcha = ref<DeliveryCaptchaData>()
const loading = ref(false)
const checking = ref(false)
const dragOffset = ref(0)
const dragStartOffset = ref(0)
const dragStartX = ref(0)
const dragging = ref(false)
const status = ref<'error' | 'idle' | 'success'>('idle')
const message = ref('向右拖动滑块完成验证')
const trackWidth = ref(310)

const knobWidth = computed(() => (trackWidth.value * 44) / 310)
const maxOffset = computed(() => Math.max(trackWidth.value - knobWidth.value, 0))
const sliderStyle = computed(() => ({ transform: `translateX(${dragOffset.value}px)` }))
const progressStyle = computed(() => ({ width: `${dragOffset.value + knobWidth.value}px` }))

function resetSlider() {
  dragOffset.value = 0
  dragging.value = false
  status.value = 'idle'
  message.value = '向右拖动滑块完成验证'
}

function measureTrack() {
  return new Promise<void>((resolve) => {
    uni.createSelectorQuery()
      .in(instance?.proxy)
      .select('.captcha-track')
      .boundingClientRect((rect) => {
        if (rect && !Array.isArray(rect) && typeof rect.width === 'number')
          trackWidth.value = rect.width
        resolve()
      })
      .exec()
  })
}

async function loadCaptcha() {
  loading.value = true
  captcha.value = undefined
  resetSlider()
  try {
    const result = await getDeliveryCaptcha()
    if (!result.success || result.repCode !== '0000' || !result.repData)
      throw new Error(result.repMsg || '验证码加载失败')
    captcha.value = result.repData
    await nextTick()
    await measureTrack()
  }
  catch (error) {
    message.value = error instanceof Error ? error.message : '验证码加载失败'
    status.value = 'error'
  }
  finally {
    loading.value = false
  }
}

function touchX(event: TouchEvent) {
  return event.touches[0]?.clientX ?? event.changedTouches[0]?.clientX ?? 0
}

function onTouchStart(event: TouchEvent) {
  if (!captcha.value || checking.value || status.value === 'success')
    return
  dragging.value = true
  dragStartX.value = touchX(event)
  dragStartOffset.value = dragOffset.value
}

function onTouchMove(event: TouchEvent) {
  if (!dragging.value)
    return
  const nextOffset = dragStartOffset.value + touchX(event) - dragStartX.value
  dragOffset.value = Math.min(Math.max(nextOffset, 0), maxOffset.value)
}

async function onTouchEnd() {
  if (!dragging.value || !captcha.value)
    return
  dragging.value = false
  if (dragOffset.value < 4)
    return

  checking.value = true
  message.value = '正在校验'
  const point: CaptchaPoint = {
    x: Math.round((dragOffset.value * 310) / trackWidth.value),
    y: 5,
  }
  try {
    const pointJson = encryptCaptchaText(JSON.stringify(point), captcha.value.secretKey)
    const result = await checkDeliveryCaptcha(pointJson, captcha.value.token)
    if (!result.success || result.repCode !== '0000')
      throw new Error(result.repMsg || '验证失败，请重试')

    status.value = 'success'
    message.value = '验证通过'
    const verification = buildCaptchaVerification(captcha.value.token, point, captcha.value.secretKey)
    setTimeout(() => emit('success', verification), 350)
  }
  catch (error) {
    status.value = 'error'
    message.value = error instanceof Error ? error.message : '验证失败，请重试'
    setTimeout(() => loadCaptcha(), 800)
  }
  finally {
    checking.value = false
  }
}

watch(() => props.visible, (visible) => {
  if (visible)
    loadCaptcha()
}, { immediate: true })
</script>

<template>
  <view v-if="visible" class="captcha-mask" @click.self="emit('close')">
    <view class="captcha-dialog" role="dialog" aria-label="安全验证">
      <view class="captcha-heading">
        <view>
          <text class="captcha-title">安全验证</text>
          <text class="captcha-subtitle">拖动拼图到缺口位置</text>
        </view>
        <button class="icon-button" aria-label="关闭验证码" @click="emit('close')">
          ×
        </button>
      </view>

      <view class="captcha-image-wrap">
        <view v-if="loading" class="captcha-loading">正在加载验证码</view>
        <template v-else-if="captcha">
          <image
            class="captcha-image"
            mode="scaleToFill"
            :src="`data:image/png;base64,${captcha.originalImageBase64}`"
          />
          <image
            class="captcha-piece"
            mode="scaleToFill"
            :src="`data:image/png;base64,${captcha.jigsawImageBase64}`"
            :style="sliderStyle"
          />
          <button class="refresh-button" aria-label="刷新验证码" @click="loadCaptcha">
            ↻
          </button>
        </template>
      </view>

      <view
        class="captcha-track"
        :class="[`is-${status}`, { 'is-disabled': loading || !captcha }]"
        @touchmove.stop.prevent="onTouchMove"
        @touchend.stop.prevent="onTouchEnd"
        @touchcancel.stop.prevent="onTouchEnd"
      >
        <view class="captcha-progress" :style="progressStyle" />
        <text class="captcha-message">{{ message }}</text>
        <view class="captcha-knob" :style="sliderStyle" @touchstart.stop.prevent="onTouchStart">
          {{ status === 'success' ? '✓' : '›' }}
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.captcha-mask {
  position: fixed;
  z-index: 1000;
  inset: 0;
  display: grid;
  padding: 40rpx;
  background: rgb(15 23 42 / 52%);
  place-items: center;
}

.captcha-dialog {
  box-sizing: border-box;
  width: 680rpx;
  max-width: 340px;
  padding: 34rpx 30rpx 32rpx;
  background: #fff;
  border-radius: 16rpx;
  box-shadow: 0 24rpx 72rpx rgb(15 23 42 / 22%);
}

.captcha-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 28rpx;
}

.captcha-title, .captcha-subtitle { display: block; }
.captcha-title { color: #172033; font-size: 32rpx; font-weight: 700; }
.captcha-subtitle { margin-top: 8rpx; color: #667085; font-size: 24rpx; }

.icon-button, .refresh-button {
  display: grid;
  margin: 0;
  padding: 0;
  color: #475467;
  background: rgb(255 255 255 / 92%);
  border: 0;
  border-radius: 50%;
  place-items: center;
}

.icon-button { width: 60rpx; height: 60rpx; font-size: 42rpx; line-height: 60rpx; }
.icon-button::after, .refresh-button::after { border: 0; }

.captcha-image-wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 2 / 1;
  overflow: hidden;
  background: #eef2f6;
  border-radius: 8rpx;
}

.captcha-image { width: 100%; height: 100%; }
.captcha-piece { position: absolute; top: 0; left: 0; width: 15.161%; height: 100%; }
.captcha-loading { display: grid; height: 100%; color: #667085; font-size: 26rpx; place-items: center; }

.refresh-button {
  position: absolute;
  top: 16rpx;
  right: 16rpx;
  width: 64rpx;
  height: 64rpx;
  font-size: 38rpx;
  line-height: 64rpx;
}

.captcha-track {
  position: relative;
  box-sizing: border-box;
  width: 100%;
  height: 88rpx;
  margin-top: 24rpx;
  overflow: hidden;
  background: #f2f4f7;
  border: 2rpx solid #d0d5dd;
  border-radius: 8rpx;
  touch-action: none;
}

.captcha-progress { position: absolute; inset: 0 auto 0 0; background: #dbeafe; }
.captcha-message { position: absolute; inset: 0; color: #667085; font-size: 24rpx; line-height: 84rpx; text-align: center; }

.captcha-knob {
  position: absolute;
  top: -2rpx;
  left: -2rpx;
  display: grid;
  width: 88rpx;
  height: 88rpx;
  color: #175cd3;
  font-size: 52rpx;
  font-weight: 600;
  background: #fff;
  border: 2rpx solid #84adff;
  border-radius: 8rpx;
  box-shadow: 0 4rpx 14rpx rgb(15 23 42 / 12%);
  place-items: center;
}

.captcha-track.is-success { border-color: #12b76a; }
.captcha-track.is-success .captcha-progress { background: #d1fadf; }
.captcha-track.is-success .captcha-knob { color: #067647; border-color: #12b76a; }
.captcha-track.is-error { border-color: #f04438; }
.captcha-track.is-error .captcha-progress { background: #fee4e2; }
.captcha-track.is-disabled { opacity: .62; }
</style>
