<script setup lang="ts">
import type { DeliveryProgress } from '@/api/delivery'
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { getOrderDeliveryProgress } from '@/api/delivery'

interface Props {
  /** 订单ID */
  orderId: string
}

const props = defineProps<Props>()
const { t } = useI18n()

const loading = ref(true)
const progress = ref<DeliveryProgress | null>(null)

/** 拨打配送员电话 */
function handleCallStaff(phone?: string) {
  if (!phone) {
    return uni.showToast({ title: t('shared.deliveryProgress.noPhone'), icon: 'none' })
  }
  uni.makePhoneCall({
    phoneNumber: phone,
    fail: () => {
      uni.showToast({ title: t('shared.deliveryProgress.callFailed'), icon: 'none' })
    },
  })
}

/** 预览送达凭证大图 */
function previewEvidence(index: number) {
  const urls = progress.value?.evidenceUrls ?? []
  if (urls.length === 0)
    return
  uni.previewImage({ urls, current: urls[index] })
}

/** 加载配送进度 */
async function fetchProgress() {
  if (!props.orderId)
    return
  loading.value = true
  try {
    const res = await getOrderDeliveryProgress(props.orderId).send()
    progress.value = res as DeliveryProgress
  }
  catch (error) {
    console.error('获取配送进度失败:', error)
    progress.value = null
  }
  finally {
    loading.value = false
  }
}

watch(() => props.orderId, () => {
  fetchProgress()
}, { immediate: true })
</script>

<template>
  <view class="delivery-progress">
    <view v-if="loading" class="dp-state">
      {{ t('common.loading') }}
    </view>

    <view v-else-if="progress" class="dp-card">
      <!-- 卡头：进度标题 + 出车单号 -->
      <view class="dp-header">
        <text class="dp-header__title">
          {{ t('shared.deliveryProgress.progressTitle') }}
        </text>
        <text v-if="progress.tripNo" class="dp-header__trip">
          {{ t('shared.deliveryProgress.tripNo') }} {{ progress.tripNo }}
        </text>
      </view>

      <!-- 配送员行：头像 + 姓名 + 拨打 -->
      <view v-if="progress.staffName" class="dp-staff">
        <view class="dp-staff__avatar">
          <text class="dp-staff__avatar-icon i-carbon:user-avatar" />
        </view>
        <view class="dp-staff__meta">
          <text class="dp-staff__name">
            {{ progress.staffName }}
          </text>
          <text class="dp-staff__role">
            {{ t('shared.deliveryProgress.staffRole') }}
          </text>
        </view>
        <view
          v-if="progress.staffPhone"
          class="dp-staff__call"
          @click="handleCallStaff(progress.staffPhone)"
        >
          <text class="dp-staff__call-icon i-carbon:phone" />
        </view>
      </view>

      <!-- 配送进度时间线 -->
      <view class="dp-timeline">
        <view
          v-for="(node, index) in progress.nodes"
          :key="index"
          class="dp-node"
        >
          <view class="dp-node__rail">
            <view
              class="dp-node__dot"
              :class="{
                'dp-node__dot--done': node.done,
                'dp-node__dot--active': node.active,
              }"
            >
              <text v-if="node.done" class="dp-node__check i-carbon:checkmark" />
            </view>
            <view
              v-if="index < progress.nodes.length - 1"
              class="dp-node__line"
              :class="{ 'dp-node__line--done': node.done }"
            />
          </view>
          <view class="dp-node__content">
            <text
              class="dp-node__name"
              :class="{
                'dp-node__name--active': node.active,
                'dp-node__name--done': node.done && !node.active,
                'dp-node__name--todo': !node.active && !node.done,
              }"
            >
              {{ node.name }}
            </text>
            <text v-if="node.time" class="dp-node__time">
              {{ node.time }}
            </text>
          </view>
        </view>
      </view>

      <!-- 送达凭证：后端已送达/已签收时才返回，空列表整块隐藏 -->
      <view
        v-if="progress.evidenceUrls && progress.evidenceUrls.length > 0"
        class="dp-evidence"
      >
        <text class="dp-evidence__title">
          {{ t('shared.deliveryProgress.evidence') }}
        </text>
        <view class="dp-evidence__grid">
          <image
            v-for="(url, index) in progress.evidenceUrls"
            :key="url"
            :src="url"
            class="dp-evidence__pic"
            mode="aspectFill"
            @click="previewEvidence(index)"
          />
        </view>
      </view>
    </view>

    <view v-else class="dp-state">
      {{ t('shared.deliveryProgress.empty') }}
    </view>
  </view>
</template>

<style lang="scss" scoped>
/*
 * 配送进度卡：主题色贯穿（完成=主题色实心对勾、当前=柔光光环、待办=灰点），
 * 不再使用写死的蓝绿渐变——商城可换肤，硬编码色值会与全站主题打架。
 */
$brand: var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237));
$ink: var(--app-text);
$ink-2: var(--app-text-2);
$ink-3: var(--app-text-3);
$hairline: var(--app-border);

.delivery-progress {
  width: 100%;
}

.dp-state {
  padding: 48rpx 0;
  font-size: 26rpx;
  color: $ink-3;
  text-align: center;
}

.dp-card {
  padding: 28rpx 28rpx 4rpx;
  background: var(--app-card);
  border-radius: 24rpx;
  box-shadow: 0 6rpx 24rpx rgba(17, 24, 39, 0.04);
}

.dp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 24rpx;
}

.dp-header__title {
  font-size: 30rpx;
  font-weight: 700;
  color: $ink;
}

.dp-header__trip {
  margin-left: 24rpx;
  font-size: 22rpx;
  color: $ink-3;
  text-align: right;
}

/* ===================== 配送员 ===================== */

.dp-staff {
  display: flex;
  align-items: center;
  padding: 20rpx 0 28rpx;
  border-top: 1rpx solid $hairline;
}

.dp-staff__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: var(--app-card-2);
  flex-shrink: 0;
}

.dp-staff__avatar-icon {
  font-size: 40rpx;
  color: $brand;
}

.dp-staff__meta {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
}

.dp-staff__name {
  font-size: 28rpx;
  font-weight: 600;
  color: $ink;
}

.dp-staff__role {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: $ink-3;
}

.dp-staff__call {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: $brand;
  flex-shrink: 0;
}

.dp-staff__call-icon {
  font-size: 36rpx;
  color: #fff;
}

/* ===================== 时间线 ===================== */

.dp-timeline {
  padding: 4rpx 0 12rpx;
}

.dp-node {
  display: flex;
}

.dp-node__rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 44rpx;
  flex-shrink: 0;
}

.dp-node__dot {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28rpx;
  height: 28rpx;
  border-radius: 50%;
  background: var(--app-border);
  flex-shrink: 0;
  z-index: 1;
  margin-top: 4rpx;
  transition: all 0.3s ease;
}

.dp-node__dot--done {
  background: $brand;
}

/* 当前节点：白底挖空 + 柔光光环（伪元素实现，避免对主题变量做 rgba 合成） */
.dp-node__dot--active {
  background: var(--app-card);
}

.dp-node__dot--active::before {
  content: '';
  position: absolute;
  top: -8rpx;
  right: -8rpx;
  bottom: -8rpx;
  left: -8rpx;
  border-radius: 50%;
  background: $brand;
  opacity: 0.15;
}

.dp-node__dot--active::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 20rpx;
  height: 20rpx;
  margin: -10rpx 0 0 -10rpx;
  border-radius: 50%;
  background: $brand;
}

.dp-node__check {
  font-size: 18rpx;
  color: #fff;
}

.dp-node__line {
  width: 4rpx;
  flex: 1;
  margin-top: 4rpx;
  background: var(--app-border);
}

.dp-node__line--done {
  background: $brand;
}

.dp-node__content {
  flex: 1;
  min-width: 0;
  padding-left: 20rpx;
  padding-bottom: 36rpx;
}

.dp-node:last-child .dp-node__content {
  padding-bottom: 24rpx;
}

.dp-node__name {
  display: block;
  font-size: 26rpx;
  line-height: 36rpx;
}

.dp-node__name--active {
  font-weight: 600;
  color: $brand;
}

.dp-node__name--done {
  color: $ink;
}

.dp-node__name--todo {
  color: $ink-3;
}

.dp-node__time {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: $ink-3;
}

/* ===================== 送达凭证 ===================== */

.dp-evidence {
  padding: 24rpx 0;
  border-top: 1rpx solid $hairline;
}

.dp-evidence__title {
  display: block;
  font-size: 24rpx;
  color: $ink-2;
}

.dp-evidence__grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 16rpx;
}

.dp-evidence__pic {
  width: 144rpx;
  height: 144rpx;
  border-radius: 16rpx;
  background: var(--app-card-2);
}
</style>
