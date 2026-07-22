<script setup lang="ts">
import type { Conversation, NoticeInboxItem } from '@/api/message/types'
import { onLoad, onPullDownRefresh, onShow, onUnload } from '@dcloudio/uni-app'
import { getConversationInbox } from '@/api/message/conversation'
import {
  getNoticeInbox,
  markAllNoticesRead,
  markNoticeRead,
} from '@/api/message/notice'
import { useMessageStore } from '@/store/messageStore'

definePage({
  name: 'message-center',
  style: {
    enablePullDownRefresh: true,
    navigationStyle: 'custom',
    navigationBarTitleText: '消息中心',
  },
})

const activeTab = ref<'conversation' | 'notice'>('notice')
const notices = ref<NoticeInboxItem[]>([])
const conversations = ref<Conversation[]>([])
const noticeCursor = ref<string>()
const conversationCursor = ref<string>()
const noticeHasMore = ref(false)
const conversationHasMore = ref(false)
const loading = ref(false)
const messageStore = useMessageStore()

async function loadNotices(reset = true) {
  const page = await getNoticeInbox({
    cursor: reset ? undefined : noticeCursor.value,
    limit: 20,
  }).send()
  notices.value = reset ? page.records : [...notices.value, ...page.records]
  noticeCursor.value = page.nextCursor
  noticeHasMore.value = page.hasMore
}

async function loadConversations(reset = true) {
  const page = await getConversationInbox({
    cursor: reset ? undefined : conversationCursor.value,
    limit: 20,
  }).send()
  conversations.value = reset
    ? page.records
    : [...conversations.value, ...page.records]
  conversationCursor.value = page.nextCursor
  conversationHasMore.value = page.hasMore
}

async function refresh() {
  loading.value = true
  try {
    await Promise.all([
      loadNotices(),
      loadConversations(),
      messageStore.refreshUnread(),
    ])
  }
  finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

async function openNotice(item: NoticeInboxItem) {
  if (item.readStatus === '0') {
    await markNoticeRead(item.messageId).send()
    item.readStatus = '1'
    messageStore.noticeUnread = Math.max(0, messageStore.noticeUnread - 1)
  }
  uni.navigateTo({
    url: `/sub-pages/message/notice/detail?id=${item.messageId}`,
  })
}

function openConversation(item: Conversation) {
  uni.navigateTo({
    url: `/sub-pages/message/chat/index?conversationId=${item.id}`,
  })
}

function startConversation() {
  uni.navigateTo({ url: '/sub-pages/message/chat/index' })
}

async function readAll() {
  await markAllNoticesRead().send()
  notices.value.forEach(item => (item.readStatus = '1'))
  messageStore.noticeUnread = 0
}

onLoad((options) => {
  if (options?.tab === 'conversation')
    activeTab.value = 'conversation'
  messageStore.connect()
  uni.$on('message-push', refresh)
})
onShow(refresh)
onPullDownRefresh(refresh)
onUnload(() => uni.$off('message-push', refresh))
</script>

<template>
  <view class="message-center">
    <hr-navbar title="消息中心" />
    <view class="message-hero">
      <view>
        <text class="message-hero__eyebrow">
          MESSAGE HUB
        </text>
        <text class="message-hero__title">
          通知与客服，保持同一条上下文
        </text>
      </view>
      <view class="message-hero__count">
        {{
          messageStore.totalUnread > 99 ? "99+" : messageStore.totalUnread
        }}
      </view>
    </view>
    <view class="message-tabs">
      <view
        :class="{ active: activeTab === 'notice' }"
        @click="activeTab = 'notice'"
      >
        通知
        <text v-if="messageStore.noticeUnread">
          {{
            messageStore.noticeUnread
          }}
        </text>
      </view>
      <view
        :class="{ active: activeTab === 'conversation' }"
        @click="activeTab = 'conversation'"
      >
        客服
        <text v-if="messageStore.conversationUnread">
          {{
            messageStore.conversationUnread
          }}
        </text>
      </view>
    </view>

    <view v-if="activeTab === 'notice'" class="message-list">
      <view class="message-list__toolbar">
        <text>站内通知</text><text @click="readAll">
          全部已读
        </text>
      </view>
      <view
        v-for="item in notices"
        :key="item.recipientRecordId"
        class="notice-item"
        :class="{ unread: item.readStatus === '0' }"
        @click="openNotice(item)"
      >
        <view class="notice-item__dot" />
        <view class="notice-item__copy">
          <view>
            <text class="notice-item__title">
              {{ item.title }}
            </text><text class="notice-item__category">
              {{
                item.category
              }}
            </text>
          </view>
          <text class="notice-item__summary">
            {{
              item.summary || item.content
            }}
          </text>
          <text class="notice-item__time">
            {{
              item.publishTime || item.receivedTime
            }}
          </text>
        </view>
        <wd-icon name="arrow-right" color="#9aaca7" />
      </view>
      <view v-if="noticeHasMore" class="load-more" @click="loadNotices(false)">
        加载更早通知
      </view>
      <wd-status-tip
        v-else-if="!loading && !notices.length"
        image="message"
        tip="暂时没有通知"
      />
    </view>

    <view v-else class="message-list">
      <view class="message-list__toolbar">
        <text>客服会话</text><text :class="`socket-${messageStore.socketState}`">
          {{
            messageStore.socketState === "open" ? "实时连接" : "HTTP 补拉"
          }}
        </text>
      </view>
      <view
        v-for="item in conversations"
        :key="item.id"
        class="conversation-item"
        @click="openConversation(item)"
      >
        <view class="conversation-item__avatar">
          <wd-icon name="service" size="38rpx" />
        </view>
        <view class="conversation-item__copy">
          <view>
            <text>商城客服</text><text>{{ item.lastMessageTime }}</text>
          </view><text>{{ item.lastMessageSummary || "开始咨询" }}</text>
        </view>
        <wd-badge v-if="item.unreadCount" :model-value="item.unreadCount" />
      </view>
      <view
        v-if="conversationHasMore"
        class="load-more"
        @click="loadConversations(false)"
      >
        加载历史会话
      </view>
      <wd-status-tip
        v-else-if="!loading && !conversations.length"
        image="content"
        tip="还没有客服会话"
      />
      <button
        class="start-service"
        @click="startConversation"
      >
        发起客服咨询
      </button>
    </view>
  </view>
</template>

<style scoped lang="scss">
.message-center {
  min-height: 100vh;
  padding-bottom: 50rpx;
  background: linear-gradient(180deg, #eaf5f2 0, #f6f8f7 430rpx);
}
.message-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 18rpx 24rpx;
  padding: 34rpx;
  color: #fff;
  border-radius: 28rpx;
  background:
    radial-gradient(circle at 92% 0, rgb(45 212 191 / 32%), transparent 42%),
    linear-gradient(135deg, #102f43, #0f5e5a);
  box-shadow: 0 22rpx 48rpx rgb(16 47 67 / 20%);
  &__eyebrow,
  &__title {
    display: block;
  }
  &__eyebrow {
    color: #7ee8d8;
    font-size: 18rpx;
    font-weight: 800;
    letter-spacing: 0.18em;
  }
  &__title {
    width: 470rpx;
    margin-top: 12rpx;
    font-size: 32rpx;
    font-weight: 700;
    line-height: 1.45;
  }
  &__count {
    display: grid;
    width: 84rpx;
    height: 84rpx;
    place-items: center;
    border: 1rpx solid rgb(255 255 255 / 28%);
    border-radius: 24rpx;
    background: rgb(255 255 255 / 10%);
    font-size: 30rpx;
    font-weight: 800;
  }
}
.message-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin: 24rpx;
  padding: 8rpx;
  border-radius: 20rpx;
  background: #dfece8;
  view {
    padding: 18rpx;
    color: #78918a;
    text-align: center;
    border-radius: 15rpx;
  }
  view.active {
    color: #0f5e5a;
    background: #fff;
    box-shadow: 0 8rpx 20rpx rgb(20 70 60 / 8%);
    font-weight: 700;
  }
  text {
    margin-left: 7rpx;
    color: #e04f5f;
  }
}
.message-list {
  padding: 0 24rpx;
  &__toolbar {
    display: flex;
    justify-content: space-between;
    padding: 16rpx 5rpx;
    color: #78918a;
    font-size: 22rpx;
  }
}
.notice-item,
.conversation-item {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin-bottom: 16rpx;
  padding: 25rpx;
  border: 1rpx solid #e0eae7;
  border-radius: 22rpx;
  background: rgb(255 255 255 / 94%);
  box-shadow: 0 12rpx 30rpx rgb(27 66 59 / 5%);
}
.notice-item__dot {
  width: 8rpx;
  height: 76rpx;
  flex: none;
  border-radius: 8rpx;
  background: #d1ddd9;
}
.notice-item.unread .notice-item__dot {
  background: #0f766e;
}
.notice-item__copy {
  min-width: 0;
  flex: 1;
}
.notice-item__copy > view {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.notice-item__title {
  overflow: hidden;
  color: #173f4f;
  font-size: 28rpx;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.notice-item__category {
  padding: 5rpx 10rpx;
  color: #0f766e;
  border-radius: 10rpx;
  background: #e5f4f0;
  font-size: 18rpx;
}
.notice-item__summary,
.notice-item__time {
  display: block;
  overflow: hidden;
  color: #81938e;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.notice-item__summary {
  margin: 10rpx 0;
  font-size: 23rpx;
}
.notice-item__time {
  color: #a3b1ad;
  font-size: 19rpx;
}
.conversation-item__avatar {
  display: grid;
  width: 76rpx;
  height: 76rpx;
  flex: none;
  place-items: center;
  color: #0f766e;
  border-radius: 22rpx;
  background: #e2f3ef;
}
.conversation-item__copy {
  min-width: 0;
  flex: 1;
}
.conversation-item__copy view {
  display: flex;
  justify-content: space-between;
  color: #173f4f;
  font-size: 27rpx;
  font-weight: 700;
}
.conversation-item__copy view text:last-child {
  color: #a3b1ad;
  font-size: 18rpx;
  font-weight: 400;
}
.conversation-item__copy > text {
  display: block;
  margin-top: 10rpx;
  overflow: hidden;
  color: #81938e;
  font-size: 22rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.load-more {
  padding: 24rpx;
  color: #718c84;
  text-align: center;
  font-size: 22rpx;
}
.start-service {
  margin: 30rpx 0;
  color: #fff;
  border-radius: 24rpx;
  background: #0f766e;
  font-size: 28rpx;
}
.socket-open {
  color: #0f9f75;
}
.socket-closed,
.socket-connecting {
  color: #d1873c;
}
</style>
