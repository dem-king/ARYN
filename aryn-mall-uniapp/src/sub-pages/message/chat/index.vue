<script setup lang="ts">
import type { ChatMessageType, Conversation } from '@/api/message/types'
import type { BusinessCardType, ViewChatMessage } from '@/utils/message'
import { onLoad, onReady, onShow, onUnload } from '@dcloudio/uni-app'
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
  buildChatTimeline,
  createClientMessageId,
  formatMessageLocalDateTime,
  latestSequence,
  mergeCursorMessages,
  mergeServerMessage,
  resolveImageMimeType,
} from '@/utils/message'
import { formatNoticeDateTime, formatNoticeTime } from '@/utils/notice'

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
/** 加载更早消息进行中：避免连点重复拉取同一游标 */
const loadingOlder = ref(false)
/** 结束会话请求进行中：避免连点重复关闭 */
const ending = ref(false)
/** 重新咨询请求进行中：避免连点重复建会话 */
const reopening = ref(false)
/** 时间线已滚到接近底部：决定 WS 推送到达时是否自动拉底 */
const atBottom = ref(true)
/** 用户上滑阅读历史时来了新消息：回到底部按钮上显示小圆点 */
const hasNewMessage = ref(false)
/** 时间线内容/视口高度（rpx→px 由浏览器算），用于「是否在底部」判定 */
const timelineContentHeight = ref(0)
const timelineViewportHeight = ref(0)
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
/** 排队中：会话头文案与空态提示跟随该状态 */
const isWaiting = computed(() => conversation.value?.status === 'WAITING')
const assignedStaffName = computed(
  () => conversation.value?.assignedStaffName?.trim() ?? '',
)
const assignedStaffAvatar = computed(
  () => conversation.value?.assignedStaffAvatar?.trim() ?? '',
)

/**
 * 会话头标题：排队时强调「正在接入」，已分配/已关闭显示客服昵称，
 * 缺昵称时用「人工客服」兜底，不再把「客服：xxx」和横条重复展示两遍。
 */
const sessionName = computed(() => {
  if (isWaiting.value)
    return t('message.chat.queueTitle')
  return assignedStaffName.value || t('message.chat.agentFallbackName')
})

/** 会话头状态行：连接态 + 会话态合并成一句，颜色点区分在线/排队/关闭 */
const sessionStatus = computed<{ tone: 'muted' | 'ok' | 'warn', text: string }>(() => {
  // 会话详情还没拉到时先说实话：不能还没连上就显示「在线 · 消息实时同步」
  if (loading.value && !conversation.value?.id)
    return { tone: 'muted', text: t('message.chat.establishing') }
  if (isClosed.value)
    return { tone: 'muted', text: t('message.chat.closed') }
  if (isWaiting.value)
    return { tone: 'warn', text: t('message.chat.waiting') }
  if (messageStore.socketState !== 'open')
    return { tone: 'warn', text: t('message.chat.reconnecting') }
  return {
    tone: 'ok',
    text: `${t('message.chat.onlineNow')} · ${t('message.chat.sync')}`,
  }
})

/** 只有已分配坐席（ASSIGNED/ACTIVE）才显示在线点：排队/关闭/详情未知态不伪造在线 */
const showOnlineDot = computed(() => {
  const status = conversation.value?.status
  return status === 'ASSIGNED' || status === 'ACTIVE'
})

/** 空态副文案：排队中直接解释现状，其余提示如何开始咨询 */
const emptyHint = computed(() =>
  isWaiting.value ? t('message.chat.waiting') : t('message.chat.emptyHint'),
)

/** CLOSED 提示：有重开截止时间就用截止时间，缺失时退化为通用可重开文案 */
const reopenHint = computed(() => {
  const deadline = formatNoticeDateTime(conversation.value?.reopenDeadline)
  return deadline
    ? t('message.chat.closedHintBefore', { deadline })
    : t('message.chat.closedHint')
})

/** 时间分隔 + 分组气泡：时间文案复用全站紧凑格式（今天 HH:mm / 昨天 / MM-DD） */
const timeline = computed(() =>
  buildChatTimeline(messages.value, { formatTime: formatNoticeTime }),
)

/** 输入框空内容时发送按钮禁用（禁用语义 + 视觉降级） */
const canSubmitDraft = computed(() => canSend.value && draft.value.trim().length > 0)
/** 离开底部且已有消息时才显示「回到最新消息」，避免空会话冒按钮 */
const showJumpToLatest = computed(
  () => !atBottom.value && messages.value.length > 0,
)

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
    measureTimeline()
  }
  catch {
    // 消息拉取失败不阻塞状态展示：已关闭/排队状态仍要能正确渲染
    uni.showToast({ title: t('message.chat.messageLoadFailed'), icon: 'none' })
  }
}

async function loadOlder() {
  if (
    loadingOlder.value
    || !conversation.value
    || !messages.value.length
    || !hasMore.value
  ) {
    return
  }
  loadingOlder.value = true
  try {
    const page = await getConversationMessages(conversation.value.id, {
      beforeSeq: messages.value[0].seqNo,
      limit: 30,
    }).send()
    messages.value = mergeCursorMessages(messages.value, page.records)
    hasMore.value = page.hasMore
    measureTimeline()
  }
  catch {
    // 分页失败保留当前消息，用户可以再点一次；不在事件回调里抛未处理异常
  }
  finally {
    loadingOlder.value = false
  }
}

/**
 * 按最新游标补拉消息并刷新会话状态。
 *
 * 触发源：WS 实时推送（message-push）、重连成功（message-reconnected）、
 * 页面回到前台（onShow）。重连补拉是审计要求的兜底：断线期间的推送会丢。
 *
 * 用户正在上滑阅读历史时不强行拉底（否则会打断阅读），改为显示回底按钮 +
 * 新消息圆点。
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
    // 请求期间用户可能已经上滑读历史：用落库后的实时位置决定是否拉底，
    // 避免「推送到达时在底部」的旧快照把人从历史里拽回最新。
    if (atBottom.value) {
      scrollToBottom()
    }
    else if (page.records.length) {
      hasNewMessage.value = true
    }
    measureTimeline()
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
    createTime: formatMessageLocalDateTime(),
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
  // 自己的消息永远拉到底：即使正在上滑读历史，也应该看到刚发出的气泡
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
    measureTimeline()
    return true
  }
  catch {
    messages.value = messages.value.map(item =>
      item.clientMessageId === clientMessageId
        ? { ...item, sendState: 'failed' }
        : item,
    )
    measureTimeline()
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

/**
 * 滚到底部。
 *
 * 仍用 `scroll-top` 累加触发（与旧实现同源）：每次设一个大于内容高度的值，
 * 小程序与 H5 都会落到底部；同时复位「离开底部」标记。
 */
function scrollToBottom() {
  atBottom.value = true
  hasNewMessage.value = false
  nextTick(() => {
    scrollTop.value += 100000
  })
}

/** 滚动事件：只做一次减法，判断是否离开底部（阈值 64px：轻微上滑不打断阅读） */
function onTimelineScroll(event: any) {
  const top = Number(event?.detail?.scrollTop ?? 0)
  const distance
    = timelineContentHeight.value - timelineViewportHeight.value - top
  atBottom.value = distance <= 64
  if (atBottom.value)
    hasNewMessage.value = false
}

/**
 * 量一次时间线内容/视口高度，供滚动事件判断「是否在底部」。
 * 查询失败（页面未就绪/节点被回收）时保持旧值，回底按钮只是不出现，不报错。
 */
function measureTimeline() {
  nextTick(() => {
    uni.createSelectorQuery()
      .select('.chat-timeline')
      .boundingClientRect()
      .select('.chat-timeline__inner')
      .boundingClientRect()
      .exec((result) => {
        const [viewport, content] = result as Array<
          { height?: number } | null
        >
        if (viewport?.height)
          timelineViewportHeight.value = viewport.height
        if (content?.height)
          timelineContentHeight.value = content.height
      })
  })
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
    // 骨架屏撤掉后内容高度才稳定：补量一次，回底判定从首屏起就准确
    measureTimeline()
  }
})
onReady(() => {
  measureTimeline()
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
    <hr-navbar :title="t('message.chat.title')" />

    <!--
      会话头：把「客服身份 / 连接状态 / 结束会话」收进一条固定横条，
      标题栏只留返回 + 居中标题，解决结束按钮贴标题、状态条字号过小的问题。
    -->
    <view class="chat-session">
      <view class="chat-session__avatar">
        <image
          v-if="assignedStaffAvatar"
          class="chat-session__avatar-image"
          :src="assignedStaffAvatar"
          mode="aspectFill"
        />
        <view v-else class="chat-session__avatar-fallback">
          <wd-icon name="service" size="40rpx" color="#ffffff" />
        </view>
        <view v-if="showOnlineDot" class="chat-session__online-dot" />
      </view>
      <view class="chat-session__body">
        <text class="chat-session__name">
          {{ sessionName }}
        </text>
        <view
          class="chat-session__status"
          :class="`is-${sessionStatus.tone}`"
        >
          <view class="chat-session__status-dot" />
          <text class="chat-session__status-text">
            {{ sessionStatus.text }}
          </text>
        </view>
      </view>
      <!-- 非 CLOSED 才提供结束会话入口；CLOSED 只能等待重开/新建 -->
      <view
        v-if="canEndConversation"
        class="chat-session__end"
        :class="{ 'is-ending': ending }"
        hover-class="is-pressed"
        :hover-stay-time="80"
        aria-role="button"
        :aria-label="t('message.chat.endSession')"
        @click.stop="endConversation"
      >
        {{ t('message.chat.endSession') }}
      </view>
    </view>

    <view class="chat-timeline-wrap">
      <scroll-view
        class="chat-timeline"
        scroll-y
        :scroll-top="scrollTop"
        @scroll="onTimelineScroll"
      >
        <view class="chat-timeline__inner">
          <view
            v-if="hasMore"
            class="chat-history"
            :class="{ 'is-loading': loadingOlder }"
            hover-class="is-pressed"
            :hover-stay-time="80"
            aria-role="button"
            @click="loadOlder"
          >
            <text>
              {{
                loadingOlder
                  ? t('message.chat.loadingEarlier')
                  : t('message.chat.loadEarlier')
              }}
            </text>
          </view>

          <!-- 骨架屏只在首屏无消息时出现：深链 cardPayload 发送期间已有气泡，不再叠骨架 -->
          <view
            v-if="loading && !messages.length"
            class="chat-skeleton"
            aria-hidden="true"
          >
            <view class="chat-skeleton__row">
              <view class="chat-skeleton__avatar" />
              <view class="chat-skeleton__bubble is-left" />
            </view>
            <view class="chat-skeleton__row is-mine">
              <view class="chat-skeleton__bubble is-right" />
            </view>
            <view class="chat-skeleton__row">
              <view class="chat-skeleton__avatar" />
              <view class="chat-skeleton__bubble is-left is-short" />
            </view>
          </view>

          <view v-else-if="!timeline.length" class="chat-empty">
            <view class="chat-empty__icon">
              <wd-icon
                name="chat"
                size="52rpx"
                color="var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237))"
              />
            </view>
            <text class="chat-empty__title">
              {{ t('message.chat.emptyTitle') }}
            </text>
            <text class="chat-empty__hint">
              {{ emptyHint }}
            </text>
            <text class="chat-empty__capabilities">
              {{ t('message.chat.composerHint') }}
            </text>
          </view>

          <view
            v-for="item in timeline"
            :key="item.key"
            class="chat-timeline__item"
          >
            <view v-if="item.kind === 'time'" class="chat-divider">
              <text class="chat-divider__label">
                {{ item.label }}
              </text>
            </view>
            <MessageChatBubble
              v-else-if="item.kind === 'message'"
              :message="item.message"
              :group-start="item.groupStart"
              :staff-avatar="assignedStaffAvatar"
              :staff-name="assignedStaffName"
              @retry="retry(item.message)"
            />
          </view>
        </view>
      </scroll-view>

      <!-- 上滑阅读历史时出现：回到最新消息，新消息到达时带一个品牌色小圆点 -->
      <view
        v-if="showJumpToLatest"
        class="chat-jump"
        hover-class="is-pressed"
        :hover-stay-time="80"
        aria-role="button"
        :aria-label="t('message.chat.jumpToLatest')"
        @click="scrollToBottom"
      >
        <wd-icon name="arrow-down" size="32rpx" color="var(--app-text-2)" />
        <view v-if="hasNewMessage" class="chat-jump__dot" />
      </view>
    </view>

    <!--
      CLOSED：输入区替换为只读卡 + 重开入口（customer-service 走 24h 重开/新建），
      不再渲染输入框/上传工具，从 UI 层直接禁止发送。
    -->
    <view v-if="isClosed" class="chat-closed">
      <view class="chat-closed__icon">
        <wd-icon
          name="lock-on"
          size="40rpx"
          color="var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237))"
        />
      </view>
      <text class="chat-closed__title">
        {{ t('message.chat.closed') }}
      </text>
      <text class="chat-closed__tip">
        {{ reopenHint }}
      </text>
      <button
        class="chat-closed__action"
        :disabled="reopening"
        hover-class="is-pressed"
        @click="reopenConversation"
      >
        {{
          reopening ? t('message.chat.reopening') : t('message.chat.reopen')
        }}
      </button>
    </view>
    <view v-else class="chat-composer">
      <view
        class="chat-composer__media"
        hover-class="is-pressed"
        :hover-stay-time="80"
        aria-role="button"
        :aria-label="t('message.chat.sendImageAria')"
        @click="chooseImages"
      >
        <wd-icon name="image" size="40rpx" color="var(--app-text-2)" />
      </view>
      <view class="chat-composer__field">
        <wd-textarea
          v-model="draft"
          :maxlength="10000"
          auto-height
          :placeholder="t('message.chat.inputPlaceholder')"
          confirm-type="send"
          :show-confirm-bar="false"
          :cursor-spacing="16"
          @confirm="sendText"
        />
      </view>
      <button
        class="chat-composer__send"
        :class="{ 'is-disabled': !canSubmitDraft }"
        :disabled="!canSubmitDraft"
        hover-class="is-pressed"
        @click="sendText"
      >
        {{ t('message.chat.send') }}
      </button>
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

/* 通用按压反馈：只改透明度，不动布局 */
.is-pressed {
  opacity: 0.72;
}

/* ===== 会话头 ===== */
.chat-session {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx 24rpx;
  flex: none;
  border-bottom: 1rpx solid var(--app-border);
  background: var(--app-card);

  &__avatar {
    position: relative;
    width: 80rpx;
    height: 80rpx;
    flex: none;
  }

  &__avatar-image {
    display: block;
    width: 100%;
    height: 100%;
    border-radius: 50%;
    background: var(--app-card-2);
  }

  &__avatar-fallback {
    display: flex;
    width: 100%;
    height: 100%;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    /* 品牌色 + 轻微黑色渐变：与气泡同一品牌口径，纯色底在小尺寸下更饱满 */
    background-color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
    background-image: linear-gradient(135deg, rgb(0 0 0 / 8%), rgb(0 0 0 / 24%));
    box-shadow: inset 0 0 0 2rpx rgb(255 255 255 / 18%);
  }

  &__online-dot {
    position: absolute;
    right: -2rpx;
    bottom: -2rpx;
    width: 18rpx;
    height: 18rpx;
    border: 4rpx solid var(--app-card);
    border-radius: 50%;
    background: #18a875;
  }

  &__body {
    min-width: 0;
    flex: 1;
  }

  &__name {
    display: block;
    overflow: hidden;
    color: var(--app-text);
    font-size: 30rpx;
    font-weight: 600;
    line-height: 1.35;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__status {
    display: flex;
    align-items: center;
    gap: 8rpx;
    margin-top: 6rpx;
    min-width: 0;
  }

  &__status-dot {
    width: 12rpx;
    height: 12rpx;
    flex: none;
    border-radius: 50%;
    background: #9aa3a0;
  }

  &__status.is-ok &__status-dot {
    background: #18a875;
    box-shadow: 0 0 0 6rpx rgb(24 168 117 / 12%);
  }

  &__status.is-warn &__status-dot {
    background: #dd9344;
  }

  &__status.is-muted &__status-dot {
    background: #9aa3a0;
  }

  &__status-text {
    overflow: hidden;
    /* 次要文字用 text-2（#666）：text-3（#999）在浅色底上仅 2.85:1，不满足可读性下限 */
    color: var(--app-text-2);
    font-size: 22rpx;
    line-height: 1.4;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  /* 纯文字按钮：保留 88rpx（44pt）命中高度，视觉仍是轻量文字。
   * 颜色用中性 text-2 而非品牌色：4 字小字压品牌橙在浅底只有 3.21:1，
   * 换成 #666 后 5.74:1；结束会话是低频次要动作，中性色也更克制。 */
  &__end {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 88rpx;
    padding: 0 8rpx 0 20rpx;
    flex: none;
    color: var(--app-text-2);
    font-size: 24rpx;
    line-height: 1;
    white-space: nowrap;
  }

  &__end.is-ending {
    opacity: 0.45;
    pointer-events: none;
  }
}

/* ===== 时间线 ===== */
.chat-timeline-wrap {
  position: relative;
  display: flex;
  min-height: 0;
  flex: 1;
}

.chat-timeline {
  min-height: 0;
  flex: 1;
}

.chat-timeline__inner {
  display: flex;
  box-sizing: border-box;
  min-height: 100%;
  padding: 20rpx 24rpx 32rpx;
  flex-direction: column;
}

.chat-timeline__item {
  flex: none;
}

.chat-history {
  display: flex;
  min-height: 72rpx;
  margin-bottom: 20rpx;
  padding: 10rpx 28rpx;
  align-self: center;
  align-items: center;
  justify-content: center;
  color: var(--app-text-2);
  border-radius: 999rpx;
  background: var(--app-card-2);
  font-size: 21rpx;
  line-height: 1.5;
}

.chat-history.is-loading {
  opacity: 0.6;
}

.chat-divider {
  display: flex;
  padding: 20rpx 0 12rpx;
  justify-content: center;
}

.chat-divider__label {
  color: var(--app-text-2);
  font-size: 20rpx;
  line-height: 1.4;
}

/* ===== 骨架屏 ===== */
.chat-skeleton {
  display: flex;
  gap: 24rpx;
  padding-top: 8rpx;
  flex-direction: column;
}

.chat-skeleton__row {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
}

.chat-skeleton__row.is-mine {
  justify-content: flex-end;
}

.chat-skeleton__avatar {
  width: 64rpx;
  height: 64rpx;
  flex: none;
  border-radius: 50%;
  background: var(--app-skeleton-band);
  animation: chat-pulse 1.4s ease-in-out infinite;
}

.chat-skeleton__bubble {
  height: 96rpx;
  border-radius: 24rpx;
  /* 用 skeleton-band：skeleton 色与页面底过于接近，加载中的占位几乎看不见 */
  background: var(--app-skeleton-band);
  animation: chat-pulse 1.4s ease-in-out infinite;
}

.chat-skeleton__bubble.is-left {
  width: 70%;
}

.chat-skeleton__bubble.is-right {
  width: 55%;
}

.chat-skeleton__bubble.is-short {
  width: 60%;
  height: 72rpx;
}

@keyframes chat-pulse {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.55;
  }
}

/* ===== 空态 ===== */
.chat-empty {
  display: flex;
  padding: 64rpx 40rpx 0;
  align-items: center;
  flex-direction: column;
  text-align: center;
}

.chat-empty__icon {
  display: flex;
  width: 128rpx;
  height: 128rpx;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--app-card);
  box-shadow: 0 12rpx 32rpx rgb(17 27 38 / 6%);
}

.chat-empty__title {
  margin-top: 28rpx;
  color: var(--app-text);
  font-size: 30rpx;
  font-weight: 600;
}

.chat-empty__hint {
  margin-top: 12rpx;
  color: var(--app-text-2);
  font-size: 24rpx;
  line-height: 1.6;
}

.chat-empty__capabilities {
  margin-top: 28rpx;
  padding: 8rpx 20rpx;
  color: var(--app-text-2);
  border-radius: 999rpx;
  background: var(--app-card-2);
  font-size: 20rpx;
  line-height: 1.4;
}

/* ===== 回到最新消息（88rpx = 44pt 命中区） ===== */
.chat-jump {
  position: absolute;
  right: 24rpx;
  bottom: 24rpx;
  z-index: 5;
  display: flex;
  width: 88rpx;
  height: 88rpx;
  align-items: center;
  justify-content: center;
  border: 1rpx solid var(--app-border);
  border-radius: 50%;
  background: var(--app-card);
  box-shadow: 0 8rpx 24rpx rgb(17 27 38 / 12%);
}

.chat-jump__dot {
  position: absolute;
  top: 6rpx;
  right: 6rpx;
  width: 14rpx;
  height: 14rpx;
  border: 3rpx solid var(--app-card);
  border-radius: 50%;
  background: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
}

/* ===== 输入区 ===== */
.chat-composer {
  display: flex;
  padding: 16rpx 24rpx max(env(safe-area-inset-bottom), 16rpx);
  flex: none;
  align-items: flex-end;
  gap: 16rpx;
  border-top: 1rpx solid var(--app-border);
  background: var(--app-card);
}

.chat-composer__media {
  display: flex;
  width: 88rpx;
  height: 88rpx;
  flex: none;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--app-card-2);
}

.chat-composer__field {
  display: flex;
  box-sizing: border-box;
  min-height: 88rpx;
  padding: 18rpx 28rpx;
  flex: 1;
  align-items: center;
  border-radius: 44rpx;
  background: var(--app-card-2);
}

.chat-composer__field :deep(.wd-textarea) {
  width: 100%;
  padding: 0;
  background: transparent;
}

.chat-composer__field :deep(.wd-textarea)::after {
  display: none;
}

.chat-composer__field :deep(.wd-textarea__value) {
  padding: 0;
  background: transparent;
}

.chat-composer__field :deep(.wd-textarea__inner) {
  min-height: 44rpx;
  padding: 0;
  color: var(--app-text);
  background: transparent;
  font-size: 28rpx;
  line-height: 1.5;
}

/* 品牌色叠一层自上而下的黑色遮罩：白字对比度从 3.2:1 提到 ≥4.5:1（最不利像素同样达标） */
.chat-composer__send {
  display: flex;
  width: 128rpx;
  height: 88rpx;
  padding: 0;
  flex: none;
  align-items: center;
  justify-content: center;
  color: #fff;
  border: none;
  border-radius: 44rpx;
  background-color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
  background-image: linear-gradient(180deg, rgb(0 0 0 / 20%), rgb(0 0 0 / 30%));
  font-size: 26rpx;
  line-height: 1;
}

.chat-composer__send::after {
  border: none;
}

/* 覆盖各端原生 button[disabled] 的基础灰底，保留品牌色 + 降透明度的禁用语义 */
.chat-composer__send[disabled],
.chat-composer__send.is-disabled {
  color: #fff;
  opacity: 0.45;
  background-color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
  background-image: none;
}

/* ===== CLOSED 只读卡 ===== */
.chat-closed {
  display: flex;
  padding: 32rpx 40rpx max(env(safe-area-inset-bottom), 32rpx);
  flex: none;
  align-items: center;
  flex-direction: column;
  border-top: 1rpx solid var(--app-border);
  background: var(--app-card);
}

.chat-closed__icon {
  display: flex;
  width: 96rpx;
  height: 96rpx;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--app-card-2);
}

.chat-closed__title {
  margin-top: 20rpx;
  color: var(--app-text);
  font-size: 30rpx;
  font-weight: 600;
}

.chat-closed__tip {
  margin-top: 10rpx;
  color: var(--app-text-2);
  font-size: 22rpx;
  line-height: 1.55;
  text-align: center;
}

.chat-closed__action {
  display: flex;
  width: auto;
  height: 88rpx;
  margin: 24rpx 0 0;
  padding: 0 56rpx;
  align-items: center;
  justify-content: center;
  color: #fff;
  border: none;
  border-radius: 44rpx;
  background-color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
  background-image: linear-gradient(180deg, rgb(0 0 0 / 20%), rgb(0 0 0 / 30%));
  font-size: 26rpx;
  line-height: 1;
}

.chat-closed__action::after {
  border: none;
}

/* 覆盖原生 button[disabled] 灰底：保留品牌色，仅降低透明度表达「进行中」 */
.chat-closed__action[disabled] {
  color: #fff;
  opacity: 0.6;
  background-color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
  background-image: none;
}

/* 尊重系统「减少动态效果」：仅 H5 内核支持该媒体查询 */
/* #ifdef H5 */
@media (prefers-reduced-motion: reduce) {
  .chat-skeleton__avatar,
  .chat-skeleton__bubble {
    animation: none;
  }
}
/* #endif */
</style>
