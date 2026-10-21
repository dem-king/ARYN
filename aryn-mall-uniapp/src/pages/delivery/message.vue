<script setup lang="ts">
import type { NoticeInboxItem } from '@/api/message/types'
import { onLoad, onPullDownRefresh, onShow, onUnload } from '@dcloudio/uni-app'
import {
  getStaffNoticeInbox,
  markAllStaffNoticesRead,
  markStaffNoticeRead,
} from '@/api/delivery-message'
import { useDeliveryMessageStore } from '@/store/deliveryMessageStore'

/**
 * 配送员消息中心。
 *
 * 司机的站内信（派单通知、COD 收款提醒等）本来就落在 message 库的工作人员收件箱里，
 * 但配送端此前没有任何入口展示，等于「消息发了收不到」。本页把它接出来：
 * 身份复用配送 TOB token（服务端即 SYS_USER），接口走 /message/staff/notice。
 */
definePage({
  name: 'delivery-message',
  style: {
    enablePullDownRefresh: true,
    navigationStyle: 'custom',
    navigationBarTitleText: '消息',
  },
})

const messageStore = useDeliveryMessageStore()
const router = useRouter()
const notices = ref<NoticeInboxItem[]>([])
const cursor = ref<string>()
const hasMore = ref(false)
const loading = ref(false)

async function loadNotices(reset = true) {
  const page = await getStaffNoticeInbox({
    cursor: reset ? undefined : cursor.value,
    limit: 20,
  }).send()
  notices.value = reset ? page.records : [...notices.value, ...page.records]
  cursor.value = page.nextCursor
  hasMore.value = page.hasMore
}

async function refresh() {
  loading.value = true
  try {
    await Promise.all([loadNotices(), messageStore.refreshUnread()])
  }
  catch {
    // 拉取失败保留列表，下拉可重试
  }
  finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

/**
 * 点击通知：标记已读。
 *
 * 不跳管理端路由——司机在配送端没有订单/退单管理权限，
 * jumpPayload 里的 /order/** 是管理端页面，点过去只会 403。
 * 通知正文已包含单号与关键信息，够司机判断。
 */
async function openNotice(item: NoticeInboxItem) {
  if (item.readStatus !== '0')
    return
  try {
    await markStaffNoticeRead(item.messageId).send()
    item.readStatus = '1'
    messageStore.noticeUnread = Math.max(0, messageStore.noticeUnread - 1)
  }
  catch {
    // 已读失败不影响阅读
  }
}

async function readAll() {
  try {
    await markAllStaffNoticesRead().send()
    notices.value.forEach(item => (item.readStatus = '1'))
    messageStore.noticeUnread = 0
  }
  catch {
    // 忽略
  }
}

/** 跳回工作台 */
function toWorkbench() {
  router.replaceAll({ name: 'delivery-index' })
}

onLoad(() => {
  messageStore.connect()
  // 收到推送后刷新列表与未读（WS 只送轻量信号，正文走 HTTP 补拉）
  uni.$on('delivery-message-push', refresh)
})
onShow(refresh)
onPullDownRefresh(refresh)
onUnload(() => uni.$off('delivery-message-push', refresh))
</script>

<template>
  <view class="message-page">
    <hr-navbar title="消息" />
    <view
      v-if="notices.length > 0"
      class="toolbar mx-20rpx mt-20rpx flex items-center justify-between rounded-16rpx bg-white px-30rpx py-20rpx"
    >
      <text class="text-28rpx text-gray-600">
        {{ messageStore.noticeUnread > 0 ? `${messageStore.noticeUnread} 条未读` : '全部已读' }}
      </text>
      <text v-if="messageStore.noticeUnread > 0" class="text-26rpx text-primary" @click="readAll">
        全部已读
      </text>
    </view>

    <view
      v-for="item in notices"
      :key="item.recipientRecordId"
      class="notice-card mx-20rpx mt-20rpx rounded-16rpx bg-white p-30rpx"
      :class="{ unread: item.readStatus === '0' }"
      @click="openNotice(item)"
    >
      <view class="flex items-start justify-between">
        <view class="flex-1">
          <view class="flex items-center">
            <text class="text-28rpx text-gray-800 font-bold">
              {{ item.title }}
            </text>
            <text class="ml-12rpx flex-none rounded-6rpx bg-gray-100 px-10rpx py-2rpx text-20rpx text-gray-500">
              {{ item.category }}
            </text>
          </view>
          <text class="mt-12rpx block text-26rpx text-gray-600">
            {{ item.summary || item.content }}
          </text>
          <text class="mt-12rpx block text-22rpx text-gray-400">
            {{ item.publishTime || item.receivedTime }}
          </text>
        </view>
        <view v-if="item.readStatus === '0'" class="dot ml-16rpx mt-6rpx flex-none" />
      </view>
    </view>

    <view v-if="hasMore" class="load-more" @click="loadNotices(false)">
      加载更早通知
    </view>

    <view v-if="!loading && notices.length === 0" class="empty flex flex-col items-center pt-160rpx">
      <text class="i-carbon:notification text-100rpx text-gray-300" />
      <text class="mt-24rpx text-26rpx text-gray-400">
        暂时没有消息
      </text>
      <text class="mt-8rpx text-22rpx text-gray-400">
        有新任务派给你时会在这里提醒
      </text>
      <wd-button class="mt-40rpx" size="small" type="primary" @click="toWorkbench">
        返回工作台
      </wd-button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.message-page {
  min-height: 100vh;
  padding-bottom: 60rpx;
  background-color: #f6f8f7;
}

.notice-card {
  border-left: 6rpx solid transparent;

  &.unread {
    border-left-color: var(--wot-color-theme, #0084ff);
    background-color: #f5faff;
  }
}

.dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background-color: var(--wot-color-theme, #0084ff);
}

.load-more {
  padding: 30rpx 0;
  color: #576b95;
  font-size: 26rpx;
  text-align: center;
}
</style>
