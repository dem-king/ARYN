<script setup lang="ts">
import type { BusinessCardType, ViewChatMessage } from '@/utils/message'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { parseMessagePayload } from '@/utils/message'

const props = withDefaults(
  defineProps<{
    message: ViewChatMessage
    /** 同发送方连续消息组的首条：显示头像，组内后续消息只占位对齐 */
    groupStart?: boolean
    /** 客服头像（会话快照），空则品牌色 service 图标兜底 */
    staffAvatar?: string
    /** 客服昵称：头像 aria-label 用 */
    staffName?: string
  }>(),
  {
    groupStart: true,
    staffAvatar: '',
    staffName: '',
  },
)

const emit = defineEmits<{ retry: [] }>()

const CARD_TYPES: BusinessCardType[] = [
  'NOTICE_CARD',
  'ORDER_CARD',
  'PRODUCT_CARD',
  'REFUND_CARD',
]

const { t } = useI18n()

/**
 * 进入动画只在挂载时判定一次：组内后续消息不播动画，
 * 之后分组重算（groupStart 变化）也不会让历史消息重新入场。
 */
const animateIn = props.groupStart

const isSystem = computed(() => props.message.senderType === 'SYSTEM')
const isMine = computed(() => props.message.senderType === 'MALL_USER')
const isCard = computed(() =>
  CARD_TYPES.includes(props.message.messageType as BusinessCardType),
)
const cardType = computed(() => props.message.messageType as BusinessCardType)
const cardPayload = computed(() => parseMessagePayload(props.message.payload))
const imageUrl = computed(() => parseMessagePayload(props.message.payload).url)
const isSending = computed(() => props.message.sendState === 'sending')
const isFailed = computed(() => props.message.sendState === 'failed')
const avatarLabel = computed(() =>
  props.staffName.trim()
    ? t('message.chat.staffLabel', { name: props.staffName.trim() })
    : t('message.chat.supportFallback'),
)

/** 失败态整条消息可点重发（气泡、状态行、卡片遮罩都收敛到这里） */
function retryMessage() {
  if (isFailed.value)
    emit('retry')
}

function previewImage() {
  // 失败态点击交给整行重试，避免「重发 + 预览」两个动作同时触发
  if (isFailed.value)
    return
  if (imageUrl.value)
    uni.previewImage({ urls: [imageUrl.value] })
}
</script>

<template>
  <view
    class="chat-message"
    :class="{
      'chat-message--mine': isMine,
      'chat-message--staff': !isMine && !isSystem,
      'chat-message--system': isSystem,
      'is-group-start': groupStart,
      'is-group-inner': !groupStart,
      'is-card': isCard,
      'is-failed': isFailed,
      'is-animated': animateIn && !isSystem,
    }"
    :aria-role="isFailed ? 'button' : undefined"
    :aria-label="isFailed ? t('message.sendFailedRetry') : undefined"
    @click="retryMessage"
  >
    <!-- 系统消息：居中胶囊，不参与左右分列 -->
    <view v-if="isSystem" class="chat-system">
      <text class="chat-system__text">
        {{ message.content || t('message.systemMessage') }}
      </text>
    </view>

    <template v-else>
      <!--
        客服头像列：只在组首渲染内容，组内保留等宽占位，
        保证同组连续气泡左边缘始终对齐。
      -->
      <view
        v-if="!isMine"
        class="chat-avatar"
        :aria-role="groupStart ? 'img' : undefined"
        :aria-label="groupStart ? avatarLabel : undefined"
      >
        <image
          v-if="groupStart && staffAvatar"
          class="chat-avatar__image"
          :src="staffAvatar"
          mode="aspectFill"
        />
        <view v-else-if="groupStart" class="chat-avatar__fallback">
          <wd-icon name="service" size="40rpx" custom-style="color: #ffffff" />
        </view>
      </view>

      <view class="chat-content">
        <view v-if="isCard" class="chat-card">
          <MessageBusinessCard :type="cardType" :payload="cardPayload" />
          <!-- 失败态用透明遮罩接管点击，避免卡片跳转与重发同时发生 -->
          <view
            v-if="isFailed"
            class="chat-card__retry-layer"
            @click.stop="retryMessage"
          />
        </view>

        <view
          v-else
          class="chat-bubble"
          :class="{
            'chat-bubble--mine': isMine,
            'chat-bubble--staff': !isMine,
            'chat-bubble--media': message.messageType === 'IMAGE',
          }"
          :hover-class="isFailed ? 'chat-bubble--pressed' : 'none'"
        >
          <text v-if="message.messageType === 'TEXT'" class="chat-bubble__text">
            {{ message.content }}
          </text>
          <image
            v-else-if="message.messageType === 'IMAGE'"
            class="chat-bubble__image"
            :src="imageUrl"
            mode="widthFix"
            @click="previewImage"
          />
          <text v-else-if="message.content" class="chat-bubble__text">
            {{ message.content }}
          </text>
        </view>

        <!-- 发送状态行：放在气泡下方，气泡内不再塞时间戳 -->
        <view
          v-if="isMine && (isSending || isFailed)"
          class="chat-send-state"
          :class="{ 'chat-send-state--failed': isFailed }"
        >
          <view v-if="isSending" class="chat-send-state__spinner" />
          <wd-icon
            v-else
            name="warning"
            size="24rpx"
            custom-style="color: var(--wot-color-danger, #fa4350)"
          />
          <text class="chat-send-state__text">
            {{ isSending ? t('message.sending') : t('message.sendFailedRetry') }}
          </text>
        </view>
      </view>
    </template>
  </view>
</template>

<style scoped lang="scss">
/* ================= 行布局 =================
 * 组内 8rpx / 组间 28rpx：分组间距由气泡自身负责，
 * 页面只需要按 buildChatTimeline 的 groupStart 传参，不要重复加 margin。
 */
.chat-message {
  display: flex;
  align-items: flex-start;
  margin-bottom: 28rpx;

  &.is-group-inner {
    margin-bottom: 8rpx;
  }

  &--mine {
    justify-content: flex-end;
  }

  &--system {
    justify-content: center;
    margin-bottom: 24rpx;
  }

  &.is-animated {
    animation: chat-message-enter 180ms ease-out both;
  }
}

/* ================= 客服头像列 ================= */
.chat-avatar {
  width: 72rpx;
  height: 72rpx;
  flex: none;
  overflow: hidden;
  margin-right: 16rpx;
  border-radius: 50%;

  &__image {
    display: block;
    width: 100%;
    height: 100%;
    background: var(--app-card-2);
  }

  &__fallback {
    display: flex;
    width: 100%;
    height: 100%;
    align-items: center;
    justify-content: center;
    /* 品牌色 + 黑色遮罩渐变：与「我」的气泡保持同一品牌口径 */
    background-color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
    background-image: linear-gradient(135deg, rgba(0, 0, 0, 0.2), rgba(0, 0, 0, 0.3));
  }
}

/* ================= 气泡 ================= */
.chat-content {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
  max-width: 74%;
}

.chat-message--mine .chat-content {
  align-items: flex-end;
}

/* 业务卡片：外层铺满，不加气泡内边距 */
.chat-message.is-card .chat-content {
  width: 100%;
  max-width: 560rpx;
}

.chat-card {
  position: relative;
  width: 100%;
}

.chat-card__retry-layer {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 2;
}

.chat-bubble {
  box-sizing: border-box;
  max-width: 100%;
  padding: 20rpx 24rpx;
  color: var(--app-text);
  border: 1rpx solid var(--app-border);
  border-radius: 8rpx 24rpx 24rpx 24rpx;
  background: var(--app-card);
  font-size: 30rpx;
  line-height: 1.6;
  transition: opacity 150ms ease-out;

  &--mine {
    color: #fff;
    border-color: transparent;
    border-radius: 24rpx 8rpx 24rpx 24rpx;
    /* 品牌色 + 黑色渐变遮罩：白字对比度 ≥ 4.5:1（品牌色由租户配置，不能直晒白字） */
    background-color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
    background-image: linear-gradient(135deg, rgba(0, 0, 0, 0.2), rgba(0, 0, 0, 0.3));
  }

  &--media {
    padding: 8rpx;
  }

  &--pressed {
    opacity: 0.85;
  }
}

.chat-bubble__text {
  display: block;
  word-break: break-word;
  white-space: pre-wrap;
}

.chat-bubble__image {
  display: block;
  width: 360rpx;
  max-width: 100%;
  border-radius: 16rpx;
}

/* ================= 发送状态行 ================= */
.chat-send-state {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-top: 8rpx;
  color: var(--app-text-3);
  font-size: 22rpx;
  line-height: 1.4;

  &--failed {
    color: var(--wot-color-danger, #fa4350);
  }

  &__spinner {
    box-sizing: border-box;
    width: 22rpx;
    height: 22rpx;
    border: 3rpx solid var(--app-text-3);
    border-top-color: transparent;
    border-radius: 50%;
    animation: chat-spin 900ms linear infinite;
  }

  &__text {
    word-break: break-word;
  }
}

/* ================= 系统消息 ================= */
.chat-system {
  max-width: 80%;
  padding: 8rpx 20rpx;
  /* 白卡 + 细边框：card-2 底与页面底几乎同色，系统提示在浅色下会「消失」 */
  color: var(--app-text-2);
  border: 1rpx solid var(--app-border);
  border-radius: 999rpx;
  background: var(--app-card);
  font-size: 22rpx;
  line-height: 1.5;
  text-align: center;

  &__text {
    word-break: break-word;
  }
}

/* 动画只动 transform / opacity */
@keyframes chat-message-enter {
  from {
    opacity: 0;
    transform: translateY(12rpx);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes chat-spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .chat-message.is-animated {
    animation: none;
  }

  .chat-bubble {
    transition: none;
  }

  .chat-send-state__spinner {
    animation: none;
  }
}
</style>
