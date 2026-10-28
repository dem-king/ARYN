<script setup lang="ts">
import type { NoticeInboxItem } from '@/api/message/types'
import type { BusinessCardType } from '@/utils/message'
import { onLoad } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
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
import {
  formatNoticeDateTime,
  isImportantNotice,
  noticeCategoryLabelOf,
} from '@/utils/notice'

definePage({
  name: 'message-notice-detail',
  style: { navigationStyle: 'custom', navigationBarTitleText: '通知详情' },
})

const { t } = useI18n()
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
    <hr-navbar :title="t('message.nav.noticeDetail')" />
    <view v-if="notice" class="notice-paper">
      <!-- 分类/优先级是后端枚举码，用户侧只显示收敛后的中文标签，绝不回显 ORDER / NORMAL -->
      <view class="notice-paper__tags">
        <text class="notice-paper__tag">
          {{ noticeCategoryLabelOf(notice) }}
        </text>
        <text
          v-if="isImportantNotice(notice.priority)"
          class="notice-paper__tag notice-paper__tag--important"
        >
          {{ t('message.notice.important') }}
        </text>
      </view>
      <text class="notice-paper__title">
        {{ notice.title }}
      </text>
      <view class="notice-paper__meta">
        <text>{{ notice.senderName || t('message.notice.systemSender') }}</text><text>
          {{
            formatNoticeDateTime(notice.publishTime || notice.receivedTime)
          }}
        </text>
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
        {{ t('message.noticeDetail.reply') }}
      </button>
    </view>
    <wd-loading v-else-if="loading" />
  </view>
</template>

<style scoped lang="scss">
/* 与消息中心同一套主题色口径：品牌色走 CSS 变量，中性色只负责可读性 */
.notice-detail {
  min-height: 100vh;
  padding-bottom: 40rpx;
  background: #f4f6f7;
}
.notice-paper {
  margin: 24rpx;
  padding: 42rpx 34rpx;
  border: 1rpx solid #eceff0;
  border-radius: 28rpx;
  background: #fff;
  box-shadow: 0 20rpx 50rpx rgb(17 27 38 / 6%);
  &__tags {
    display: flex;
    align-items: center;
    gap: 12rpx;
  }
  &__tag {
    padding: 5rpx 16rpx;
    color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
    border-radius: 999rpx;
    background: #f2f4f4;
    font-size: 19rpx;
    font-weight: 700;
    line-height: 1.5;
  }
  &__tag--important {
    color: #d64545;
    background: #fdecec;
  }
  &__title {
    display: block;
    margin: 20rpx 0 14rpx;
    color: #1f2a2e;
    font-size: 38rpx;
    font-weight: 800;
    line-height: 1.45;
  }
  &__meta {
    display: flex;
    gap: 28rpx;
    color: #a3ada9;
    font-size: 21rpx;
  }
  &__summary {
    display: block;
    margin: 30rpx 0 4rpx;
    padding: 22rpx;
    color: #4c5854;
    border-left: 6rpx solid var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
    background: #f5f7f7;
    font-size: 25rpx;
    line-height: 1.6;
  }
  &__content {
    display: block;
    min-height: 300rpx;
    padding: 32rpx 0;
    color: #39433f;
    font-size: 28rpx;
    line-height: 1.9;
    white-space: pre-wrap;
  }
  &__reply {
    margin-top: 30rpx;
    color: #fff;
    border-radius: 22rpx;
    background: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
    font-size: 27rpx;
  }
  &__card {
    margin: 0 0 30rpx;
  }
}
</style>
