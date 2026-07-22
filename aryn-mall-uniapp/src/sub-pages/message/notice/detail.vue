<script setup lang="ts">
import type { NoticeInboxItem } from '@/api/message/types'
import type { BusinessCardType } from '@/utils/message'
import { onLoad } from '@dcloudio/uni-app'
import {
  getNoticeDetail,
  markNoticeRead,
  replyToNotice,
} from '@/api/message/notice'
import BusinessCard from '@/components/message/BusinessCard.vue'
import {

  createClientMessageId,
  parseMessagePayload,
} from '@/utils/message'

definePage({
  name: 'message-notice-detail',
  style: { navigationStyle: 'custom', navigationBarTitleText: '通知详情' },
})

const notice = ref<NoticeInboxItem>()
const loading = ref(true)

const businessCardType = computed<BusinessCardType | undefined>(() => {
  if (notice.value?.sourceType === 'ORDER_REFUND')
    return 'REFUND_CARD'
  if (notice.value?.sourceType?.startsWith('ORDER_'))
    return 'ORDER_CARD'
  return undefined
})

const businessCardPayload = computed(() =>
  parseMessagePayload(notice.value?.cardPayload),
)

onLoad(async (options) => {
  if (!options?.id)
    return
  try {
    notice.value = await getNoticeDetail(options.id).send()
    if (notice.value.readStatus === '0') {
      await markNoticeRead(options.id).send()
      notice.value.readStatus = '1'
    }
  }
  finally {
    loading.value = false
  }
})

async function reply() {
  if (!notice.value)
    return
  const message = await replyToNotice(
    notice.value.messageId,
    createClientMessageId(),
  ).send()
  uni.navigateTo({
    url: `/sub-pages/message/chat/index?conversationId=${message.conversationId}`,
  })
}
</script>

<template>
  <view class="notice-detail">
    <hr-navbar title="通知详情" />
    <view v-if="notice" class="notice-paper">
      <text class="notice-paper__category">
        {{ notice.category }} / {{ notice.priority }}
      </text>
      <text class="notice-paper__title">
        {{ notice.title }}
      </text>
      <view class="notice-paper__meta">
        <text>{{ notice.senderName || "系统" }}</text><text>{{ notice.publishTime }}</text>
      </view>
      <text v-if="notice.summary" class="notice-paper__summary">
        {{
          notice.summary
        }}
      </text>
      <text class="notice-paper__content">
        {{ notice.content }}
      </text>
      <BusinessCard
        v-if="businessCardType && notice.cardPayload"
        class="notice-paper__card"
        :payload="businessCardPayload"
        :type="businessCardType"
      />
      <button class="notice-paper__reply" @click="reply">
        回复 / 咨询客服
      </button>
    </view>
    <wd-loading v-else-if="loading" />
  </view>
</template>

<style scoped lang="scss">
.notice-detail {
  min-height: 100vh;
  padding-bottom: 40rpx;
  background: linear-gradient(180deg, #eaf5f2, #f7f8f8 360rpx);
}
.notice-paper {
  margin: 24rpx;
  padding: 42rpx 34rpx;
  border: 1rpx solid #dbe7e4;
  border-radius: 28rpx;
  background: #fff;
  box-shadow: 0 20rpx 50rpx rgb(25 61 56 / 8%);
  &__category,
  &__title,
  &__summary,
  &__content {
    display: block;
  }
  &__category {
    color: #0f766e;
    font-size: 19rpx;
    font-weight: 800;
    letter-spacing: 0.14em;
  }
  &__title {
    margin: 18rpx 0;
    color: #173f4f;
    font-size: 38rpx;
    font-weight: 800;
    line-height: 1.45;
  }
  &__meta {
    display: flex;
    gap: 28rpx;
    color: #9aaca7;
    font-size: 21rpx;
  }
  &__summary {
    margin: 30rpx 0 4rpx;
    padding: 22rpx;
    color: #4d6962;
    border-left: 6rpx solid #0f766e;
    background: #edf6f3;
    font-size: 25rpx;
    line-height: 1.6;
  }
  &__content {
    min-height: 300rpx;
    padding: 32rpx 0;
    color: #334f49;
    font-size: 28rpx;
    line-height: 1.9;
    white-space: pre-wrap;
  }
  &__reply {
    margin-top: 30rpx;
    color: #fff;
    border-radius: 22rpx;
    background: #0f766e;
    font-size: 27rpx;
  }
  &__card {
    margin: 0 0 30rpx;
  }
}
</style>
