<script setup lang="ts">
import type { NoticeInboxItem } from '@/api/message/types'
import type { BusinessCardType } from '@/utils/message'
import { onLoad, onPageScroll } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import {
  getNoticeDetail,
  markNoticeRead,
  replyToNotice,
} from '@/api/message/notice'
import BusinessCard from '@/components/message/BusinessCard.vue'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { useRequireLogin } from '@/composables/useRequireLogin'
import {
  createClientMessageId,
  parseMessagePayload,
} from '@/utils/message'
import { resolveNoticeJump } from '@/utils/notice-jump'
import {
  formatNoticeDateTime,
  isImportantNotice,
  noticeCategoryLabelOf,
} from '@/utils/notice'
import { navigateToUrl } from '@/utils/tab-bar'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'message-notice-detail',
  style: { navigationStyle: 'custom', navigationBarTitleText: '通知详情' },
})

const { t } = useI18n()
const { ensureLoggedIn } = useRequireLogin()
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

/**
 * 通知的站内跳转目标（订单/退款/商品/优惠券/端内路由）。
 *
 * 详情页不只有正文：能安全映射时给出「查看详情」按钮并让业务卡片点击同样
 * 落到该路由；无安全目标时按钮不渲染，避免出现「可点但没反应」的入口。
 */
const jumpRoute = computed(() =>
  notice.value ? resolveNoticeJump(notice.value) : undefined,
)

/** 标记已读：失败只影响未读计数，不阻断详情展示与跳转 */
async function markReadIfNeeded() {
  if (!notice.value || notice.value.readStatus !== '0')
    return
  try {
    await markNoticeRead(notice.value.messageId).send()
    notice.value.readStatus = '1'
  }
  catch {
    // 已读上报失败忽略：用户正在阅读正文，不应被一次计数请求打断
  }
}

async function openJump() {
  if (!jumpRoute.value)
    return
  await markReadIfNeeded()
  navigateToUrl(jumpRoute.value)
}

onLoad(async (options) => {
  // 通知详情是非白名单页（可被 H5 深链/历史链接直达）：游客不拉详情、不上报已读，
  // 只记录含 id 的回跳路径并打开登录页，登录成功后自动回到本页。
  const redirectPath = options?.id
    ? `/sub-pages/message/notice/detail?id=${options.id}`
    : undefined
  if (!ensureLoggedIn(redirectPath)) {
    loading.value = false
    return
  }
  if (!options?.id) {
    // 深链没有 id 时没有可拉取的内容：必须收起骨架屏，否则已登录用户会一直看到 loading
    loading.value = false
    return
  }
  try {
    notice.value = await getNoticeDetail(options.id).send()
    await markReadIfNeeded()
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

// 顶栏磨砂玻璃：字面量注册滚动（小程序按页面源码生成滚动位标记，包进 composable 会静默失效）
onPageScroll(handlePageScroll)
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
        :route="jumpRoute"
        :type="businessCardType"
      />
      <!-- 有安全站内跳转目标才渲染入口：无目标时按钮不出现，避免「可点但没反应」 -->
      <button v-if="jumpRoute" class="notice-paper__jump" @click="openJump">
        {{ t('message.noticeDetail.viewDetail') }}
      </button>
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
  background: var(--app-bg);
}
.notice-paper {
  margin: 24rpx;
  padding: 42rpx 34rpx;
  border: 1rpx solid var(--app-border);
  border-radius: 28rpx;
  background: var(--app-card);
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
    background: var(--app-card-2);
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
    color: var(--app-text);
    font-size: 38rpx;
    font-weight: 800;
    line-height: 1.45;
  }
  &__meta {
    display: flex;
    gap: 28rpx;
    color: var(--app-text-3);
    font-size: 21rpx;
  }
  &__summary {
    display: block;
    margin: 30rpx 0 4rpx;
    padding: 22rpx;
    color: var(--app-text);
    border-left: 6rpx solid var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
    background: var(--app-card-2);
    font-size: 25rpx;
    line-height: 1.6;
  }
  &__content {
    display: block;
    min-height: 300rpx;
    padding: 32rpx 0;
    color: var(--app-text);
    font-size: 28rpx;
    line-height: 1.9;
    white-space: pre-wrap;
  }
  &__jump {
    margin-top: 30rpx;
    color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
    border: 1rpx solid currentcolor;
    border-radius: 22rpx;
    background: transparent;
    font-size: 27rpx;
  }
  &__reply {
    margin-top: 20rpx;
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
