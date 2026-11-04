<script setup lang="ts">
import type { ChatMessageType, Conversation } from '@/api/message/types'
import type { BusinessCardType, ViewChatMessage } from '@/utils/message'
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import {
  closeConversation,
  getConversation,
  getConversationMessages,
  getOrCreateCustomerService,
  markConversationRead,
  sendConversationMessage,
} from '@/api/message/conversation'
import { uploadFile } from '@/api/upms/file'
import { useRequireLogin } from '@/composables/useRequireLogin'
import { useAuthStore } from '@/store/authStore'
import { useMessageStore } from '@/store/messageStore'
import {
  createClientMessageId,
  latestSequence,
  mergeCursorMessages,
  mergeServerMessage,
  resolveImageMimeType,
} from '@/utils/message'
import { formatNoticeDateTime } from '@/utils/notice'

definePage({
  name: 'message-chat',
  style: { navigationStyle: 'custom', navigationBarTitleText: '在线客服' },
})

const { t } = useI18n()
const authStore = useAuthStore()
const { ensureLoggedIn } = useRequireLogin()
const conversation = ref<Conversation>()
const messages = ref<ViewChatMessage[]>([])
const draft = ref('')
const scrollTop = ref(0)
const hasMore = ref(false)
const loading = ref(true)
/** 结束会话请求进行中：避免连点重复关闭 */
const ending = ref(false)
/** 重新咨询请求进行中：避免连点重复建会话 */
const reopening = ref(false)
const messageStore = useMessageStore()
let initialCard:
  { messageType: BusinessCardType, payload: Record<string, any> } | undefined

/** 已关闭会话只读：禁止发送、上传与重试 */
const isClosed = computed(() => conversation.value?.status === 'CLOSED')
const canSend = computed(() => !!conversation.value && !isClosed.value)
/** 非 CLOSED 才能结束会话：WAITING 也允许用户主动取消排队 */
const canEndConversation = computed(
  () => !!conversation.value && !isClosed.value && !ending.value,
)

/**
 * 状态条文案：按会话状态渲染，而不是只区分「排队/其它」。
 * 深链详情拉取失败时 status 为空，此时不臆造状态，统一回退「客服会话」。
 */
const statusText = computed(() => {
  const status = conversation.value?.status
  if (status === 'CLOSED')
    return t('message.chat.closed')
  if (status === 'WAITING')
    return t('message.chat.waiting')
  if (status === 'ASSIGNED' || status === 'ACTIVE') {
    const staffName = conversation.value?.assignedStaffName?.trim()
    return staffName
      ? t('message.chat.staffLabel', { name: staffName })
      : t('message.chat.supportFallback')
  }
  return t('message.chat.supportFallback')
})

/** CLOSED 提示：有重开截止时间就用截止时间，缺失时退化为通用可重开文案 */
const reopenHint = computed(() => {
  const deadline = formatNoticeDateTime(conversation.value?.reopenDeadline)
  return deadline
    ? t('message.chat.closedHintBefore', { deadline })
    : t('message.chat.closedHint')
})

function warnClosed() {
  uni.showToast({ title: t('message.chat.closedReadonly'), icon: 'none' })
}

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
  await establishConversation(options?.conversationId, contextPayload)
  await loadInitial()
  if (initialCard) {
    const card = initialCard
    // 先清空再发：发送失败会留下可点重试的失败气泡，重开会话时不再重复自动发送
    initialCard = undefined
    await sendStructured(card.messageType, card.payload)
  }
}

/**
 * 建立会话上下文。
 *
 * 审计问题：旧实现拿到深链 conversationId 就直接构造最小对象渲染，已关闭会话
 * 仍显示输入框，点发送才失败。这里无论是否深链，都先 `getConversation` 校准
 * status / 客服昵称 / reopenDeadline，再加载消息；详情失败保留最小 `{id}` 并提示。
 */
async function establishConversation(
  conversationId?: string,
  contextPayload?: string,
) {
  let seed: Conversation | undefined
  let targetId = conversationId
  if (!targetId) {
    // 无深链：customer-service 接口负责「复用进行中会话 → 24h 内重开 → 新建」
    seed = await getOrCreateCustomerService(contextPayload).send()
    targetId = seed.id
  }
  await refreshConversation(targetId, seed)
}

/**
 * 刷新会话详情。
 *
 * @param seed 兜底快照：接口失败时优先用它（如 getOrCreate 刚返回的会话），
 *   没有快照才退化成最小 `{id}`——状态未知按「进行中」处理，不能把网络故障
 *   误判成已关闭而禁用输入。
 */
async function refreshConversation(id: string, seed?: Conversation) {
  try {
    conversation.value = await getConversation(id).send()
  }
  catch {
    if (!conversation.value || conversation.value.id !== id)
      conversation.value = seed ?? ({ id } as Conversation)
    uni.showToast({ title: t('message.chat.detailLoadFailed'), icon: 'none' })
  }
}

async function loadInitial() {
  if (!conversation.value)
    return
  try {
    const page = await getConversationMessages(conversation.value.id, {
      limit: 50,
    }).send()
    messages.value = [...page.records].reverse()
    hasMore.value = page.hasMore
    await markLatestRead()
    scrollToBottom()
  }
  catch {
    // 消息拉取失败不阻塞状态展示：已关闭/排队状态仍要能正确渲染
    uni.showToast({ title: t('message.chat.messageLoadFailed'), icon: 'none' })
  }
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

/**
 * 按最新游标补拉消息并刷新会话状态。
 *
 * 触发源：WS 实时推送（message-push）、重连成功（message-reconnected）、
 * 页面回到前台（onShow）。重连补拉是审计要求的兜底：断线期间的推送会丢。
 */
async function recoverAfterPush() {
  if (!conversation.value)
    return
  const afterSeq = latestSequence(
    messages.value.filter(item => item.seqNo < Number.MAX_SAFE_INTEGER),
  )
  try {
    const [page] = await Promise.all([
      getConversationMessages(conversation.value.id, {
        afterSeq,
        limit: 100,
      }).send(),
      // 推送到达时一并刷新会话状态，避免坐席已领取/已关闭仍显示旧状态
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
  catch {
    // 补拉失败留给下一次推送/重连/onShow 再试，不在事件回调里抛未处理异常
  }
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

/**
 * 发送消息。
 *
 * `existingClientMessageId` 用于失败重试：重试必须复用首次生成的消息 ID，
 * 服务端按 clientMessageId 幂等去重；每次重试换新 ID 会绕过幂等、产生重复消息。
 */
async function sendMessage(
  messageType: ChatMessageType,
  content?: string,
  payload?: string,
  existingClientMessageId?: string,
) {
  if (!conversation.value)
    return false
  if (isClosed.value) {
    warnClosed()
    return false
  }
  const clientMessageId = existingClientMessageId ?? createClientMessageId()
  if (existingClientMessageId) {
    // 重试：复用原气泡，只把状态改回发送中，不再新增一条
    messages.value = messages.value.map(item =>
      item.clientMessageId === clientMessageId
        ? { ...item, sendState: 'sending' }
        : item,
    )
  }
  else {
    messages.value.push(
      optimistic(messageType, clientMessageId, content, payload),
    )
  }
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
    return true
  }
  catch {
    messages.value = messages.value.map(item =>
      item.clientMessageId === clientMessageId
        ? { ...item, sendState: 'failed' }
        : item,
    )
    return false
  }
}

async function sendText() {
  const content = draft.value.trim()
  if (!content)
    return
  if (!canSend.value) {
    warnClosed()
    return
  }
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
  if (!canSend.value) {
    warnClosed()
    return
  }
  uni.chooseImage({
    count: 6,
    success: async ({ tempFilePaths }) => {
      for (const path of tempFilePaths) {
        let url: string
        try {
          const response = await uploadFile(path)
          url = response.data
        }
        catch {
          // 单张上传失败提示后可继续其余图片，不阻断整轮发送
          uni.showToast({
            title: t('message.chat.uploadFailed'),
            icon: 'none',
          })
          continue
        }
        await sendMessage(
          'IMAGE',
          undefined,
          JSON.stringify({
            fileId: url,
            // 真实 MIME：后端只接受 jpeg/png/webp，旧实现固定 jpeg 与图片字节不符
            mimeType: resolveImageMimeType(path),
            url,
          }),
        )
      }
    },
  })
}

/** 失败气泡点击重发：复用原 clientMessageId（服务端幂等） */
function retry(message: ViewChatMessage) {
  if (message.sendState !== 'failed')
    return
  if (isClosed.value) {
    warnClosed()
    return
  }
  void sendMessage(
    message.messageType,
    message.content,
    message.payload,
    message.clientMessageId,
  )
}

/**
 * 结束会话：确认后关闭并重新拉详情/消息，输入区随状态切换为只读。
 */
async function endConversation() {
  if (!conversation.value || isClosed.value || ending.value)
    return
  const confirmed = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: t('message.chat.endSessionTitle'),
      content: t('message.chat.endSessionConfirm'),
      success: ({ confirm }) => resolve(confirm),
      fail: () => resolve(false),
    })
  })
  if (!confirmed)
    return
  const conversationId = conversation.value.id
  ending.value = true
  try {
    await closeConversation(conversationId).send()
    // 先本地置 CLOSED 再刷新：即使详情请求失败也不能继续显示可写输入框
    conversation.value = {
      ...conversation.value,
      status: 'CLOSED',
    } as Conversation
    await refreshConversation(conversationId, conversation.value)
    await loadInitial()
    uni.showToast({ title: t('message.chat.endSessionSuccess'), icon: 'none' })
  }
  catch {
    uni.showToast({ title: t('message.chat.endSessionFailed'), icon: 'none' })
  }
  finally {
    ending.value = false
  }
}

/**
 * 重新咨询：走 customer-service 接口（24h 内重开原会话 / 超时新建），
 * 成功后刷新会话详情与消息，页面从只读态恢复为可发送。
 */
async function reopenConversation() {
  if (reopening.value)
    return
  reopening.value = true
  try {
    const view = await getOrCreateCustomerService().send()
    conversation.value = view
    messages.value = []
    if (view.status === 'CLOSED') {
      // 后端未成功重开（如超过重开时限且新建失败）：保持只读并提示
      uni.showToast({ title: t('message.chat.reopenFailed'), icon: 'none' })
      return
    }
    await refreshConversation(view.id, view)
    await loadInitial()
    uni.showToast({ title: t('message.chat.reopenSuccess'), icon: 'none' })
  }
  catch {
    uni.showToast({ title: t('message.chat.reopenFailed'), icon: 'none' })
  }
  finally {
    reopening.value = false
  }
}

function scrollToBottom() {
  nextTick(() => (scrollTop.value += 100000))
}

onLoad(async (options) => {
  // 在线客服是非白名单页（可被深链/分享直达）：游客先记录回跳并打开登录页，
  // 不建连、不初始化会话；loading 必须收起，否则空骨架屏会一直转。
  if (!ensureLoggedIn()) {
    loading.value = false
    return
  }
  messageStore.connect()
  uni.$on('message-push', recoverAfterPush)
  // WS 重连成功（断线期间推送会丢）：按 afterSeq 补拉一次
  uni.$on('message-reconnected', recoverAfterPush)
  try {
    await initialize(options as Record<string, any>)
  }
  finally {
    loading.value = false
  }
})
onShow(() => {
  // 登录页返回瞬间（用户取消登录）本页仍在栈顶：未登录不发请求，
  // 等登录成功后由 redirect 重建页面再走 onLoad。
  if (!authStore.isLoggedIn)
    return
  // 后台挂起期间的推送可能丢失（socket 假在线由 store 心跳兜底），
  // 回到前台时按游标补拉一次，保证坐席回复最终可见。
  if (!loading.value)
    void recoverAfterPush()
})
onUnload(() => {
  uni.$off('message-push', recoverAfterPush)
  uni.$off('message-reconnected', recoverAfterPush)
})
</script>

<template>
  <view class="chat-page">
    <hr-navbar :title="t('message.chat.title')">
      <template #right>
        <!-- 非 CLOSED 才提供结束会话入口；CLOSED 只能等待重开/新建 -->
        <view
          v-if="canEndConversation"
          class="chat-navbar-action"
          @click.stop="endConversation"
        >
          {{ t('message.chat.endSession') }}
        </view>
      </template>
    </hr-navbar>
    <view class="chat-status">
      <view :class="`state-${messageStore.socketState}`" /><text>
        {{
          messageStore.socketState === "open"
            ? t('message.chat.sync')
            : t('message.chat.reconnecting')
        }}
      </text><text
        class="chat-status__session"
        :class="{ 'is-closed': isClosed }"
      >
        {{ statusText }}
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
    <!--
      CLOSED：输入区替换为只读提示 + 重开入口（customer-service 走 24h 重开/新建），
      不再渲染输入框/上传工具，从 UI 层直接禁止发送。
    -->
    <view v-if="isClosed" class="chat-closed">
      <text class="chat-closed__tip">
        {{ reopenHint }}
      </text>
      <button
        class="chat-closed__action"
        :disabled="reopening"
        @click="reopenConversation"
      >
        {{
          reopening ? t('message.chat.reopening') : t('message.chat.reopen')
        }}
      </button>
    </view>
    <view v-else class="chat-composer">
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
  background: var(--app-bg);
}
/* 标题栏右侧「结束会话」：小胶囊按钮，避免抢占会话内容区空间 */
.chat-navbar-action {
  padding: 6rpx 18rpx;
  color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
  border: 1rpx solid currentcolor;
  border-radius: 999rpx;
  font-size: 22rpx;
  line-height: 1.6;
  white-space: nowrap;
}
.chat-status {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 16rpx 24rpx;
  color: var(--app-text-3);
  border-bottom: 1rpx solid var(--app-border);
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
.chat-status__session {
  margin-left: auto;
  overflow: hidden;
  color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
  font-size: 20rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chat-status__session.is-closed {
  color: #9aa3a0;
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
  color: var(--app-text-3);
  text-align: center;
  font-size: 21rpx;
}
/* CLOSED 只读区：替代输入区，占位高度与输入区接近，避免底部突然塌陷 */
.chat-closed {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 22rpx 24rpx max(env(safe-area-inset-bottom), 22rpx);
  border-top: 1rpx solid var(--app-border);
  background: var(--app-card);
  &__tip {
    min-width: 0;
    flex: 1;
    color: var(--app-text-3);
    font-size: 22rpx;
    line-height: 1.55;
  }
  &__action {
    height: 68rpx;
    margin: 0;
    padding: 0 34rpx;
    flex: none;
    color: #fff;
    border-radius: 999rpx;
    background: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
    font-size: 24rpx;
    line-height: 68rpx;
  }
  &__action[disabled] {
    opacity: 0.6;
  }
}
.chat-composer {
  padding: 18rpx 22rpx max(env(safe-area-inset-bottom), 18rpx);
  border-top: 1rpx solid var(--app-border);
  background: var(--app-card);
  &__tools {
    display: flex;
    align-items: center;
    gap: 18rpx;
    margin-bottom: 14rpx;
    color: var(--app-text-3);
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
    background: var(--app-card-2);
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
