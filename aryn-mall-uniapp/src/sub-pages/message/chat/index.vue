<script setup lang="ts">
import type { ChatMessageType, Conversation } from '@/api/message/types'
import type { BusinessCardType, ViewChatMessage } from '@/utils/message'
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import {
  getConversation,
  getConversationMessages,
  getOrCreateCustomerService,
  markConversationRead,
  sendConversationMessage,
} from '@/api/message/conversation'
import { uploadFile } from '@/api/upms/file'
import { useMessageStore } from '@/store/messageStore'
import {
  createClientMessageId,
  latestSequence,
  mergeCursorMessages,
  mergeServerMessage,
} from '@/utils/message'

definePage({
  name: 'message-chat',
  style: { navigationStyle: 'custom', navigationBarTitleText: '在线客服' },
})

const { t } = useI18n()
const conversation = ref<Conversation>()
const messages = ref<ViewChatMessage[]>([])
const draft = ref('')
const scrollTop = ref(0)
const hasMore = ref(false)
const loading = ref(true)
const messageStore = useMessageStore()
let initialCard:
  { messageType: BusinessCardType, payload: Record<string, any> } | undefined

async function initialize(options?: Record<string, any>) {
  const contextPayload = options?.cardPayload
    ? decodeURIComponent(options.cardPayload)
    : undefined
  if (options?.cardType && contextPayload) {
    const allowed: BusinessCardType[] = [
      'NOTICE_CARD',
      'ORDER_CARD',
      'PRODUCT_CARD',
      'REFUND_CARD',
    ]
    if (allowed.includes(options.cardType as BusinessCardType)) {
      try {
        initialCard = {
          messageType: options.cardType as BusinessCardType,
          payload: JSON.parse(contextPayload),
        }
      }
      catch {}
    }
  }
  conversation.value = options?.conversationId
    ? ({ id: options.conversationId } as Conversation)
    : await getOrCreateCustomerService(contextPayload).send()
  await loadInitial()
  if (initialCard)
    await sendStructured(initialCard.messageType, initialCard.payload)
}

async function loadInitial() {
  if (!conversation.value)
    return
  const page = await getConversationMessages(conversation.value.id, {
    limit: 50,
  }).send()
  messages.value = [...page.records].reverse()
  hasMore.value = page.hasMore
  await markLatestRead()
  scrollToBottom()
}

async function loadOlder() {
  if (!conversation.value || !messages.value.length || !hasMore.value)
    return
  const page = await getConversationMessages(conversation.value.id, {
    beforeSeq: messages.value[0].seqNo,
    limit: 30,
  }).send()
  messages.value = mergeCursorMessages(messages.value, page.records)
  hasMore.value = page.hasMore
}

async function recoverAfterPush() {
  if (!conversation.value)
    return
  const afterSeq = latestSequence(
    messages.value.filter(item => item.seqNo < Number.MAX_SAFE_INTEGER),
  )
  const [page] = await Promise.all([
    getConversationMessages(conversation.value.id, {
      afterSeq,
      limit: 100,
    }).send(),
    // 推送到达时一并刷新会话状态，避免坐席已领取仍显示“等待客服领取”
    getConversation(conversation.value.id)
      .send()
      .then((view) => {
        conversation.value = view
      })
      .catch(() => undefined),
  ])
  messages.value = mergeCursorMessages(messages.value, page.records)
  await markLatestRead()
  scrollToBottom()
}

async function markLatestRead() {
  if (!conversation.value)
    return
  const latest = latestSequence(
    messages.value.filter(item => item.seqNo < Number.MAX_SAFE_INTEGER),
  )
  if (!latest)
    return
  try {
    await markConversationRead(conversation.value.id, latest).send()
  }
  catch {
    // 已读上报失败不影响消息主流程（如会话刚好被关闭）
  }
}

function optimistic(
  messageType: ChatMessageType,
  clientMessageId: string,
  content?: string,
  payload?: string,
): ViewChatMessage {
  return {
    clientMessageId,
    content,
    conversationId: conversation.value!.id,
    createTime: new Date().toISOString(),
    id: `local-${clientMessageId}`,
    messageType,
    payload,
    sendState: 'sending',
    senderId: 'current',
    senderName: t('message.chat.me'),
    senderType: 'MALL_USER',
    seqNo: Number.MAX_SAFE_INTEGER,
  }
}

async function sendMessage(
  messageType: ChatMessageType,
  content?: string,
  payload?: string,
) {
  if (!conversation.value)
    return
  const clientMessageId = createClientMessageId()
  messages.value.push(
    optimistic(messageType, clientMessageId, content, payload),
  )
  scrollToBottom()
  try {
    const server = await sendConversationMessage(conversation.value.id, {
      clientMessageId,
      content,
      messageType,
      payload,
    }).send()
    messages.value = mergeServerMessage(messages.value, server)
    await markLatestRead()
  }
  catch (error) {
    messages.value = messages.value.map(item =>
      item.clientMessageId === clientMessageId
        ? { ...item, sendState: 'failed' }
        : item,
    )
    throw error
  }
}

async function sendText() {
  const content = draft.value.trim()
  if (!content)
    return
  draft.value = ''
  await sendMessage('TEXT', content)
}

async function sendStructured(
  messageType: BusinessCardType,
  payload: Record<string, any>,
) {
  await sendMessage(messageType, undefined, JSON.stringify(payload))
}

function chooseImages() {
  uni.chooseImage({
    count: 6,
    success: async ({ tempFilePaths }) => {
      for (const path of tempFilePaths) {
        const response = await uploadFile(path)
        await sendMessage(
          'IMAGE',
          undefined,
          JSON.stringify({
            fileId: response.data,
            mimeType: 'image/jpeg',
            url: response.data,
          }),
        )
      }
    },
  })
}

function retry(message: ViewChatMessage) {
  if (message.sendState !== 'failed')
    return
  messages.value = messages.value.filter(
    item => item.clientMessageId !== message.clientMessageId,
  )
  void sendMessage(message.messageType, message.content, message.payload)
}

function scrollToBottom() {
  nextTick(() => (scrollTop.value += 100000))
}

onLoad(async (options) => {
  messageStore.connect()
  uni.$on('message-push', recoverAfterPush)
  try {
    await initialize(options as Record<string, any>)
  }
  finally {
    loading.value = false
  }
})
onShow(() => {
  // 后台挂起期间的推送可能丢失（socket 假在线由 store 心跳兜底），
  // 回到前台时按游标补拉一次，保证坐席回复最终可见。
  if (!loading.value)
    void recoverAfterPush()
})
onUnload(() => uni.$off('message-push', recoverAfterPush))
</script>

<template>
  <view class="chat-page">
    <hr-navbar :title="t('message.chat.title')" />
    <view class="chat-status">
      <view :class="`state-${messageStore.socketState}`" /><text>
        {{
          messageStore.socketState === "open"
            ? t('message.chat.sync')
            : t('message.chat.reconnecting')
        }}
      </text><text v-if="conversation">
        {{
          conversation.status === "WAITING" ? t('message.chat.waiting') : t('message.chat.label')
        }}
      </text>
    </view>
    <scroll-view scroll-y class="chat-timeline" :scroll-top="scrollTop">
      <view v-if="hasMore" class="load-history" @click="loadOlder">
        {{ t('message.chat.loadEarlier') }}
      </view>
      <MessageChatBubble
        v-for="message in messages"
        :key="message.id"
        :message="message"
        @click="retry(message)"
      />
      <view v-if="loading" class="loading-copy">
        {{ t('message.chat.establishing') }}
      </view>
    </scroll-view>
    <view class="chat-composer">
      <view class="chat-composer__tools">
        <wd-icon name="image" size="42rpx" @click="chooseImages" /><text>
          {{ t('message.chat.composerHint') }}
        </text>
      </view>
      <view class="chat-composer__row">
        <wd-textarea
          v-model="draft"
          :maxlength="10000"
          auto-height
          :placeholder="t('message.chat.inputPlaceholder')"
        /><button @click="sendText">
          {{ t('message.chat.send') }}
        </button>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.chat-page {
  display: flex;
  height: 100vh;
  flex-direction: column;
  overflow: hidden;
  background: #f4f6f7;
}
.chat-status {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 16rpx 24rpx;
  color: #7b8783;
  border-bottom: 1rpx solid #eceff0;
  background: rgb(255 255 255 / 92%);
  font-size: 20rpx;
}
.chat-status view {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
}
.state-open {
  background: #18a875;
  box-shadow: 0 0 0 7rpx rgb(24 168 117 / 12%);
}
.state-closed,
.state-connecting {
  background: #dd9344;
}
.chat-status text:last-child {
  margin-left: auto;
  color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
}
.chat-timeline {
  min-height: 0;
  flex: 1;
  box-sizing: border-box;
  padding: 28rpx 24rpx;
}
.load-history,
.loading-copy {
  padding: 20rpx;
  color: #8a9591;
  text-align: center;
  font-size: 21rpx;
}
.chat-composer {
  padding: 18rpx 22rpx max(env(safe-area-inset-bottom), 18rpx);
  border-top: 1rpx solid #eceff0;
  background: #fff;
  &__tools {
    display: flex;
    align-items: center;
    gap: 18rpx;
    margin-bottom: 14rpx;
    color: #87938f;
    font-size: 20rpx;
  }
  &__row {
    display: flex;
    align-items: flex-end;
    gap: 14rpx;
  }
  :deep(.wd-textarea) {
    flex: 1;
    padding: 12rpx 18rpx;
    border-radius: 20rpx;
    background: #f4f6f7;
  }
  button {
    width: 130rpx;
    height: 72rpx;
    margin: 0;
    color: #fff;
    border-radius: 20rpx;
    background: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
    font-size: 25rpx;
    line-height: 72rpx;
  }
}
</style>
