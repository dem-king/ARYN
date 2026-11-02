<script setup lang="ts">
import type { Conversation, NoticeInboxItem } from '@/api/message/types'
import { onLoad, onPageScroll, onPullDownRefresh, onShow, onUnload } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { getConversationInbox } from '@/api/message/conversation'
import {
  getNoticeInbox,
  markAllNoticesRead,
  markNoticeRead,
} from '@/api/message/notice'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { useRequireLogin } from '@/composables/useRequireLogin'
import { useAuthStore } from '@/store/authStore'
import { useMessageStore } from '@/store/messageStore'
import {
  formatNoticeTime,
  isImportantNotice,
  noticeCategoryLabelOf,
} from '@/utils/notice'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'message-center',
  style: {
    enablePullDownRefresh: true,
    navigationStyle: 'custom',
    navigationBarTitleText: '消息中心',
  },
})

const { t } = useI18n()
const activeTab = ref<'conversation' | 'notice'>('notice')
const notices = ref<NoticeInboxItem[]>([])
const conversations = ref<Conversation[]>([])
const noticeCursor = ref<string>()
const conversationCursor = ref<string>()
const noticeHasMore = ref(false)
const conversationHasMore = ref(false)
const loading = ref(false)
const authStore = useAuthStore()
const messageStore = useMessageStore()
const { goLogin } = useRequireLogin()

/** 页头不写产品内部说明：有未读报数，无未读给确定态，别让用户读到设计文档的句子 */
const heroTitle = computed(() =>
  messageStore.totalUnread > 0
    ? t('message.hub.unreadTitle', { count: messageStore.totalUnread })
    : t('message.hub.allRead'),
)

/**
 * 悬浮玻璃卡吸顶位置：自定义导航栏高度 = 状态栏 + 44px 内容区。
 * 与 hr-navbar 里 wd-navbar 的占位口径一致；取不到系统信息时按无状态栏处理。
 */
function resolveStickyTop() {
  try {
    const { statusBarHeight } = uni.getWindowInfo()
    return (statusBarHeight ?? 0) + 44
  }
  catch {
    return 44
  }
}
const stickyTop = ref(resolveStickyTop())
const stuck = ref(false)

// 卡片吸顶后加重投影形成「浮起」层级；值没变不触发渲染，滚动回调里只做一次比较
onPageScroll((event) => {
  // 同一滚动回调里顺带驱动顶栏磨砂玻璃：页面在滚，内容会从固定顶栏下穿过
  handlePageScroll(event)
  const next = event.scrollTop > 8
  if (next !== stuck.value)
    stuck.value = next
})

async function loadNotices(reset = true) {
  // 消息中心是非白名单页：游客深链打开时先挡在这里，不发出需登录的收件箱请求
  if (!authStore.isLoggedIn)
    return
  const page = await getNoticeInbox({
    cursor: reset ? undefined : noticeCursor.value,
    limit: 20,
  }).send()
  notices.value = reset ? page.records : [...notices.value, ...page.records]
  noticeCursor.value = page.nextCursor
  noticeHasMore.value = page.hasMore
}

async function loadConversations(reset = true) {
  // 同上：游客不拉客服会话
  if (!authStore.isLoggedIn)
    return
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
  if (!authStore.isLoggedIn) {
    loading.value = false
    // 游客下拉刷新不发请求，但原生下拉动画必须收起，否则会一直转
    uni.stopPullDownRefresh()
    return
  }
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
  // 游客深链直达时不建连、不订阅推送：登录后由 redirect 回本页重新 onLoad
  if (!authStore.isLoggedIn)
    return
  messageStore.connect()
  uni.$on('message-push', refresh)
})
onShow(refresh)
onPullDownRefresh(refresh)
onUnload(() => uni.$off('message-push', refresh))
</script>

<template>
  <view class="message-center">
    <hr-navbar :title="t('message.nav.center')" />
    <!--
      游客态：消息中心是非白名单页（可被 H5 深链/历史链接直达），未登录时不渲染
      hero / tabs / 列表，页面也不发出任何需登录请求，只留一条登录引导；
      登录成功后由登录页按 redirectPath 回到本页重新加载。
    -->
    <view v-if="!authStore.isLoggedIn" class="message-guest">
      <view class="message-guest__badge">
        <wd-icon name="notification" size="52rpx" color="#ffffff" />
      </view>
      <text class="message-guest__text">
        {{ t('message.hub.loginRequired') }}
      </text>
      <view class="message-guest__action" @tap="goLogin()">
        {{ t('common.goLogin') }}
      </view>
    </view>

    <template v-else>
      <view
        class="message-hero"
        :class="{ 'is-stuck': stuck }"
        :style="{ top: `${stickyTop}px` }"
      >
        <view class="message-hero__copy">
          <text class="message-hero__title">
            {{ heroTitle }}
          </text>
          <text class="message-hero__tip">
            {{ t('message.hub.tip') }}
          </text>
        </view>
        <view class="message-hero__badge">
          <wd-icon name="notification" size="42rpx" color="#ffffff" />
        </view>
      </view>
      <view class="message-tabs">
        <view
          :class="{ active: activeTab === 'notice' }"
          @click="activeTab = 'notice'"
        >
          {{ t('message.tabs.notice') }}
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
          {{ t('message.tabs.support') }}
          <text v-if="messageStore.conversationUnread">
            {{
              messageStore.conversationUnread
            }}
          </text>
        </view>
      </view>

      <view v-if="activeTab === 'notice'" class="message-list">
        <view class="message-list__toolbar">
          <text>{{ t('message.notice.inbox') }}</text><text
            v-if="messageStore.noticeUnread"
            class="message-list__action"
            @click="readAll"
          >
            {{ t('message.notice.markAllRead') }}
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
            <view class="notice-item__head">
              <text class="notice-item__title">
                {{ item.title }}
              </text><text class="notice-item__category">
                {{
                  noticeCategoryLabelOf(item)
                }}
              </text><text
                v-if="isImportantNotice(item.priority)"
                class="notice-item__important"
              >
                {{ t('message.notice.important') }}
              </text>
            </view>
            <text class="notice-item__summary">
              {{
                item.summary || item.content
              }}
            </text>
            <text class="notice-item__time">
              {{
                formatNoticeTime(item.publishTime || item.receivedTime)
              }}
            </text>
          </view>
          <wd-icon name="arrow-right" size="28rpx" color="#c3cbc8" />
        </view>
        <view v-if="noticeHasMore" class="load-more" @click="loadNotices(false)">
          {{ t('message.notice.loadEarlier') }}
        </view>
        <wd-status-tip
          v-else-if="!loading && !notices.length"
          image="message"
          :tip="t('message.notice.empty')"
        />
      </view>

      <view v-else class="message-list">
        <view class="message-list__toolbar">
          <text>{{ t('message.support.listTitle') }}</text>
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
              <text>{{ t('message.support.mallSupport') }}</text><text>{{ formatNoticeTime(item.lastMessageTime) }}</text>
            </view><text>{{ item.lastMessageSummary || t('message.support.startHint') }}</text>
          </view>
          <wd-badge v-if="item.unreadCount" :model-value="item.unreadCount" />
        </view>
        <view
          v-if="conversationHasMore"
          class="load-more"
          @click="loadConversations(false)"
        >
          {{ t('message.support.loadHistory') }}
        </view>
        <wd-status-tip
          v-else-if="!loading && !conversations.length"
          image="content"
          :tip="t('message.support.empty')"
        />
        <button
          class="start-service"
          @click="startConversation"
        >
          {{ t('message.support.start') }}
        </button>
      </view>
    </template>
  </view>
</template>

<style scoped lang="scss">
/* 页面主色统一跟随商城主题（--wot-color-theme-primary 由 App.ku.vue 下发，
   --theme-color-primary 供装修页覆盖），不再写死消息模块专属的青绿色。 */
.message-center {
  min-height: 100vh;
  padding-bottom: 50rpx;
  background: var(--app-bg);
}
/* 游客态：非白名单页深链直达且未登录时，只展示登录引导，不渲染需登录内容 */
.message-guest {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 160rpx 64rpx;
  text-align: center;
  &__badge {
    display: grid;
    width: 120rpx;
    height: 120rpx;
    place-items: center;
    border-radius: 34rpx;
    background: linear-gradient(
      135deg,
      var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237)),
      var(--theme-color-secondary, var(--wot-color-theme-secondary, #ff6b7a))
    );
    box-shadow: 0 12rpx 28rpx rgb(20 29 45 / 16%);
  }
  &__text {
    margin-top: 32rpx;
    color: var(--app-text);
    font-size: 30rpx;
    font-weight: 700;
    line-height: 1.5;
  }
  &__action {
    height: 80rpx;
    margin-top: 36rpx;
    padding: 0 64rpx;
    color: #fff;
    border-radius: 999rpx;
    background: linear-gradient(
      135deg,
      var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237)),
      var(--theme-color-secondary, var(--wot-color-theme-secondary, #ff6b7a))
    );
    box-shadow: 0 10rpx 24rpx rgb(20 29 45 / 16%);
    font-size: 28rpx;
    font-weight: 600;
    line-height: 80rpx;
  }
}
/*
  悬浮玻璃卡（玻璃拟态）：吸顶常驻，列表从卡下穿过。
  玻璃感来自四层：
  1. 半透明白底 + backdrop-filter：透出的内容被模糊成卡面底色（不是静态白块）；
  2. 1rpx 半透明白描边 + 上缘高光（::before）：玻璃厚边与反光；
  3. 双层投影：常态轻浮起，吸顶后（.is-stuck）加重，把卡和内容分层；
  4. 右上角品牌色光晕（::after）：仍跟随商城主题色，但压得足够淡，不遮模糊底。
  不支持 backdrop-filter 的内核（部分安卓 X5）退化为高不透明白，文案仍可读。
*/
.message-hero {
  position: sticky;
  z-index: 30;
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin: 16rpx 24rpx;
  padding: 24rpx 26rpx;
  overflow: hidden;
  border: 1rpx solid rgb(255 255 255 / 70%);
  border-radius: 26rpx;
  background: linear-gradient(135deg, rgb(255 255 255 / 82%) 0%, rgb(255 255 255 / 58%) 100%);
  /* WEBKIT 大写写法避开 autoprefixer 的过时前缀清理（iOS 17- 的 WKWebView 只认带前缀的 backdrop-filter） */
  -WEBKIT-backdrop-filter: blur(28px) saturate(200%);
  backdrop-filter: blur(28px) saturate(200%);
  box-shadow:
    0 12rpx 32rpx rgb(20 29 45 / 10%),
    0 2rpx 8rpx rgb(20 29 45 / 6%),
    inset 0 1rpx 0 rgb(255 255 255 / 85%);
  transition: box-shadow 220ms ease;
  &::before {
    position: absolute;
    top: 0;
    right: 12%;
    left: 12%;
    height: 1rpx;
    background: linear-gradient(90deg, transparent, rgb(255 255 255 / 94%), transparent);
    content: '';
    pointer-events: none;
  }
  &::after {
    position: absolute;
    top: -120rpx;
    right: -80rpx;
    width: 300rpx;
    height: 300rpx;
    background: radial-gradient(circle, var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237)) 0%, transparent 68%);
    border-radius: 50%;
    content: '';
    opacity: 0.12;
    pointer-events: none;
  }
  &__copy {
    position: relative;
    z-index: 1;
    min-width: 0;
    flex: 1;
  }
  &__title,
  &__tip {
    display: block;
  }
  &__title {
    color: var(--app-text);
    font-size: 32rpx;
    font-weight: 700;
    line-height: 1.4;
  }
  &__tip {
    margin-top: 8rpx;
    color: #5b616e;
    font-size: 21rpx;
    line-height: 1.5;
  }
  &__badge {
    position: relative;
    z-index: 1;
    display: grid;
    width: 72rpx;
    height: 72rpx;
    flex: none;
    place-items: center;
    border-radius: 20rpx;
    background: linear-gradient(
      135deg,
      var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237)),
      var(--theme-color-secondary, var(--wot-color-theme-secondary, #ff6b7a))
    );
    box-shadow:
      0 8rpx 18rpx rgb(20 29 45 / 18%),
      inset 0 1rpx 0 rgb(255 255 255 / 35%);
  }
}
.message-hero.is-stuck {
  box-shadow:
    0 20rpx 48rpx rgb(20 29 45 / 16%),
    0 2rpx 8rpx rgb(20 29 45 / 6%),
    inset 0 1rpx 0 rgb(255 255 255 / 90%);
}
.message-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin: 24rpx;
  padding: 8rpx;
  border-radius: 20rpx;
  background: var(--app-border);
  view {
    padding: 18rpx;
    color: var(--app-text-2);
    text-align: center;
    font-size: 26rpx;
    border-radius: 15rpx;
  }
  view.active {
    color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
    background: var(--app-card);
    box-shadow: 0 8rpx 20rpx rgb(20 40 60 / 6%);
    font-weight: 700;
  }
  text {
    margin-left: 7rpx;
    color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
  }
}
.message-list {
  padding: 0 24rpx;
  &__toolbar {
    display: flex;
    justify-content: space-between;
    padding: 16rpx 5rpx;
    color: var(--app-text-3);
    font-size: 22rpx;
  }
  &__action {
    color: var(--app-text-2);
  }
}
.notice-item,
.conversation-item {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin-bottom: 16rpx;
  padding: 25rpx;
  border: 1rpx solid var(--app-border);
  border-radius: 22rpx;
  background: var(--app-card);
  box-shadow: 0 10rpx 26rpx rgb(17 27 38 / 4%);
}
.notice-item__dot {
  width: 8rpx;
  height: 76rpx;
  flex: none;
  border-radius: 8rpx;
  background: var(--app-border);
}
.notice-item.unread .notice-item__dot {
  background: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
}
.notice-item__copy {
  min-width: 0;
  flex: 1;
}
.notice-item__head {
  display: flex;
  align-items: center;
  gap: 10rpx;
}
.notice-item__title {
  overflow: hidden;
  color: var(--app-text);
  font-size: 28rpx;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.notice-item__category,
.notice-item__important {
  padding: 4rpx 12rpx;
  flex: none;
  border-radius: 999rpx;
  font-size: 19rpx;
  line-height: 1.5;
}
.notice-item__category {
  color: var(--app-text-2);
  background: var(--app-card-2);
}
.notice-item__important {
  color: #d64545;
  background: #fdecec;
}
.notice-item__summary,
.notice-item__time {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.notice-item__summary {
  margin: 10rpx 0;
  color: var(--app-text-3);
  font-size: 23rpx;
}
.notice-item__time {
  color: var(--app-text-3);
  font-size: 19rpx;
}
.conversation-item__avatar {
  display: grid;
  width: 76rpx;
  height: 76rpx;
  flex: none;
  place-items: center;
  color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
  border-radius: 22rpx;
  background: var(--app-card-2);
}
.conversation-item__copy {
  min-width: 0;
  flex: 1;
}
.conversation-item__copy view {
  display: flex;
  justify-content: space-between;
  color: var(--app-text);
  font-size: 27rpx;
  font-weight: 700;
}
.conversation-item__copy view text:last-child {
  color: var(--app-text-3);
  font-size: 18rpx;
  font-weight: 400;
}
.conversation-item__copy > text {
  display: block;
  margin-top: 10rpx;
  overflow: hidden;
  color: var(--app-text-3);
  font-size: 22rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.load-more {
  padding: 24rpx;
  color: var(--app-text-3);
  text-align: center;
  font-size: 22rpx;
}
.start-service {
  margin: 30rpx 0;
  color: #fff;
  border-radius: 24rpx;
  background: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
  font-size: 28rpx;
}
</style>
