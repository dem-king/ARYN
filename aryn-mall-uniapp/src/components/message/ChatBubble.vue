<script setup lang="ts">
import type { ViewChatMessage } from '@/utils/message'
import { parseMessagePayload } from '@/utils/message'

defineProps<{ message: ViewChatMessage }>()

function previewImage(payload?: string) {
  const url = parseMessagePayload(payload).url
  if (url)
    uni.previewImage({ urls: [url] })
}
</script>

<template>
  <view
    class="message-row"
    :class="{
      mine: message.senderType === 'MALL_USER',
      system: message.senderType === 'SYSTEM',
    }"
  >
    <view v-if="message.senderType === 'SYSTEM'" class="system-message">
      {{ message.content || "系统消息" }}
    </view>
    <view v-else class="message-bubble">
      <text v-if="message.messageType === 'TEXT'" class="message-text">
        {{
          message.content
        }}
      </text>
      <image
        v-else-if="message.messageType === 'IMAGE'"
        class="message-image"
        :src="parseMessagePayload(message.payload).url"
        mode="widthFix"
        @click="previewImage(message.payload)"
      />
      <MessageBusinessCard
        v-else-if="
          ['NOTICE_CARD', 'ORDER_CARD', 'PRODUCT_CARD', 'REFUND_CARD'].includes(
            message.messageType,
          )
        "
        :type="message.messageType as any"
        :payload="parseMessagePayload(message.payload)"
      />
      <text class="message-state">
        {{
          message.sendState === "sending"
            ? "发送中…"
            : message.sendState === "failed"
              ? "发送失败，点击重试"
              : message.createTime
        }}
      </text>
    </view>
  </view>
</template>

<style scoped lang="scss">
.message-row {
  display: flex;
  margin-bottom: 24rpx;
  &.mine {
    justify-content: flex-end;
  }
  &.system {
    justify-content: center;
  }
}
.message-bubble {
  max-width: 76%;
  padding: 18rpx 22rpx;
  border: 1rpx solid #dce9e6;
  border-radius: 8rpx 26rpx 26rpx;
  background: #fff;
  box-shadow: 0 10rpx 26rpx rgb(25 67 64 / 6%);
}
.mine .message-bubble {
  color: #fff;
  border-color: #0f766e;
  border-radius: 26rpx 8rpx 26rpx 26rpx;
  background: #0f766e;
}
.message-text {
  display: block;
  font-size: 28rpx;
  line-height: 1.6;
  white-space: pre-wrap;
}
.message-image {
  width: 360rpx;
  max-height: 460rpx;
  border-radius: 14rpx;
}
.message-state {
  display: block;
  margin-top: 10rpx;
  color: inherit;
  font-size: 18rpx;
  opacity: 0.55;
}
.system-message {
  padding: 10rpx 18rpx;
  color: #81938e;
  border-radius: 30rpx;
  background: #e9f0ee;
  font-size: 20rpx;
}
</style>
