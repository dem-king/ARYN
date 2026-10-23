<script setup lang="ts">
import type { GroupBuyActivity } from '@/api/promotion/groupBuyActivity'
import { computed, ref, watch } from 'vue'
import { stripGroupBadgePrefix } from '@/utils/activity-name'

const props = defineProps<{
  activity: GroupBuyActivity
  /**
   * 商品主图。
   *
   * 拼团活动 VO 本身不带图片字段，由列表页按本页 spuId 批量取回后传入
   * （一次 getByIds，避免每张卡各发一次请求）；缺省时渲染缺图占位。
   */
  imageUrl?: string
}>()

const emit = defineEmits<{
  (e: 'click', activity: GroupBuyActivity): void
}>()

/** 远程图加载失败后不再重试同一 URL，直接走缺图占位，避免破图 */
const imageFailed = ref(false)
watch(() => props.imageUrl, () => {
  imageFailed.value = false
})

const showImage = computed(() => Boolean(props.imageUrl) && !imageFailed.value)

/** 成团人数徽标：存量活动可能没有 groupNum，降级为「拼团」 */
const badgeLabel = computed(() =>
  (props.activity.groupNum ?? 0) > 0 ? `${props.activity.groupNum}人成团` : '拼团',
)

/**
 * 卡内活动名：剥掉与图上徽标重复的「N人成团·」前缀。
 * 仅在 groupNum > 0（徽标确实会渲染成团人数）时剥离，避免误伤把
 * 「3人份」「2人份」这类规格写进名称的活动。
 */
const displayName = computed(() =>
  stripGroupBadgePrefix(props.activity.activityName || '拼团活动', props.activity.groupNum ?? 0),
)

function formatPrice(value?: number) {
  if (typeof value !== 'number' || !Number.isFinite(value))
    return ''
  return value.toFixed(2)
}

const priceText = computed(() => formatPrice(props.activity.groupPrice))
const originalText = computed(() => formatPrice(props.activity.originalPrice))

/** 省额：原价严格大于拼团价才展示，避免给未填原价的存量活动划出假线 */
const saveText = computed(() => {
  const original = props.activity.originalPrice ?? 0
  const group = props.activity.groupPrice ?? 0
  return original > group ? (original - group).toFixed(2) : ''
})

/**
 * 参与信息：已拼件数 + 限购件数合成一句话。
 * 第二轮之前是三个并列灰胶囊（已拼 / 限购 / 进行中），
 * 「进行中」还和右侧「去拼团」语义重复，已删除。
 */
const metaText = computed(() => {
  const parts: string[] = []
  if ((props.activity.virtualNum ?? 0) > 0)
    parts.push(`已拼${props.activity.virtualNum}件`)
  if ((props.activity.limitNum ?? 0) > 0)
    parts.push(`限购${props.activity.limitNum}件`)
  return parts.join(' · ')
})

const statusLabel = computed(() => {
  if (props.activity.activityStatus === '0')
    return '即将开始'
  if (props.activity.activityStatus === '1')
    return '进行中'
  return '已结束'
})

const statusClass = computed(() => `status-${props.activity.activityStatus ?? '2'}`)
const isActive = computed(() => props.activity.activityStatus === '1')
const isPending = computed(() => props.activity.activityStatus === '0')
const isEnded = computed(() => !isActive.value && !isPending.value)

const ctaLabel = computed(() => {
  if (isActive.value)
    return '去拼团'
  if (isPending.value)
    return '即将开团'
  return '已结束'
})

function handleClick() {
  emit('click', props.activity)
}
</script>

<template>
  <!--
    左图右文货架卡（与首页拼团楼层 diy-limited-activity 同构）：
    商品图 200rpx + 成团徽标 / 活动名两行（已剥重复前缀）/ 双价 + 省额 /
    一句参与信息（已拼 N 件 · 限购 N 件）+ 去拼团。
    促销强度「中」：只让价格、徽标与实心 CTA 用主题色，其余白底 + 发丝线。
  -->
  <view class="group-buy-card" :class="{ 'is-ended': isEnded }" @tap="handleClick">
    <view class="gb-media">
      <image
        v-if="showImage"
        class="gb-img"
        :src="resolveImageSrc(imageUrl)"
        mode="aspectFill"
        lazy-load
        @error="imageFailed = true"
      />
      <view v-else class="gb-img gb-img--empty">
        <text>拼团</text>
      </view>
      <view class="gb-badge">
        {{ badgeLabel }}
      </view>
    </view>

    <view class="gb-body">
      <view class="gb-name">
        {{ displayName }}
      </view>

      <view class="gb-price">
        <text class="gb-sym">
          ¥
        </text>
        <text class="gb-val">
          {{ priceText || '--' }}
        </text>
        <text v-if="originalText" class="gb-orig">¥{{ originalText }}</text>
        <text v-if="saveText" class="gb-save">省¥{{ saveText }}</text>
      </view>

      <view class="gb-foot">
        <text v-if="metaText" class="gb-meta">{{ metaText }}</text>
        <text v-else class="gb-meta gb-meta--status" :class="statusClass">{{ statusLabel }}</text>
        <!-- 视觉按钮 64rpx / 命中区 ≥88rpx（§4.3 触控要求） -->
        <view class="gb-cta-hit">
          <view class="gb-btn" :class="{ 'is-pending': isPending, 'is-ended': isEnded }">
            {{ ctaLabel }}
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 取值口径：圆角 20rpx、图 16rpx、发丝线 #f1f2f4、卡阴影 0 8rpx 24rpx rgba(15,23,42,.05)；
   卡内边距 20rpx（图示 200rpx），卡间距由列表页给 24rpx。 */
.group-buy-card {
  display: flex;
  box-sizing: border-box;
  gap: 20rpx;
  padding: 20rpx;
  background: #fff;
  border: 1rpx solid #f1f2f4;
  border-radius: 20rpx;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
}

.group-buy-card.is-ended {
  opacity: 0.6;
}

.gb-media {
  position: relative;
  flex: 0 0 auto;
}

/* 小程序 <image> 基座自带显式默认高，必须同时给 rpx 宽高 */
.gb-img {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 200rpx;
  height: 200rpx;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 22rpx;
  background: #f3f4f6;
  border-radius: 16rpx;
}

.gb-img--empty {
  background: var(--wot-color-theme-background, #fff1ee);
}

/* 成团徽标：实心主色（去掉二次渐变，与秒杀立省角标同一门语言） */
.gb-badge {
  position: absolute;
  top: 10rpx;
  left: 10rpx;
  display: flex;
  align-items: center;
  height: 34rpx;
  padding: 0 12rpx;
  color: #fff;
  font-size: 19rpx;
  font-weight: 700;
  letter-spacing: 0.4rpx;
  background: var(--wot-color-theme-primary, #ff2237);
  border-radius: 999rpx;
}

.gb-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.gb-name {
  display: -webkit-box;
  height: 68rpx;
  overflow: hidden;
  color: #1f2329;
  font-size: 25rpx;
  font-weight: 500;
  line-height: 34rpx;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.gb-price {
  display: flex;
  align-items: baseline;
  margin-top: 8rpx;
  gap: 8rpx;
  white-space: nowrap;
}

.gb-sym {
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 20rpx;
  font-weight: 700;
}

.gb-val {
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 42rpx;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.6rpx;
}

.gb-orig {
  color: #b6b9c0;
  font-size: 20rpx;
  text-decoration: line-through;
}

.gb-save {
  padding: 4rpx 10rpx;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 19rpx;
  font-weight: 600;
  background: var(--wot-color-theme-background, #fff1ee);
  border-radius: 8rpx;
}

.gb-foot {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 12rpx;
  gap: 12rpx;
}

/* 参与信息：一句文本（22rpx/400 muted），不再用并列灰胶囊 */
.gb-meta {
  display: block;
  overflow: hidden;
  min-width: 0;
  flex: 1;
  color: #8a8f99;
  font-size: 22rpx;
  font-weight: 400;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* 只在下限（无已拼/限购数据）时用状态词兜底，避免与右侧 CTA 重复 */
.gb-meta--status.status-1 {
  color: var(--wot-color-theme-primary, #ff2237);
  font-weight: 600;
}

.gb-meta--status.status-2 {
  color: #b6b9c0;
}

.gb-cta-hit {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: flex-end;
  min-height: 88rpx;
}

.gb-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 64rpx;
  min-width: 152rpx;
  padding: 0 28rpx;
  color: #fff;
  font-size: 24rpx;
  font-weight: 700;
  letter-spacing: 0.4rpx;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff6b7a),
    var(--wot-color-theme-primary, #ff2237)
  );
  border-radius: 999rpx;
}

.gb-btn.is-pending {
  color: var(--wot-color-theme-primary, #ff2237);
  background: var(--wot-color-theme-background, #fff1ee);
}

.gb-btn.is-ended {
  color: #b6b9c0;
  background: #f1f2f4;
}
</style>
