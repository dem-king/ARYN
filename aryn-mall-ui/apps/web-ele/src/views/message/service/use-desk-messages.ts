import type { Ref } from 'vue';

import type { ViewChatMessage } from '../message-state';
import type { ComposerState, QuoteTarget } from './service-state';

import type { ChatMessage, Conversation } from '#/api/message/types';

import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';
import { nanoid } from 'nanoid';

import {
  getConversationMessages,
  markConversationRead,
  recallConversationMessage,
  sendConversationMessage,
} from '#/api/message/conversation';
import { uploadConversationImage } from '#/api/message/upload';
import { useMessageStore } from '#/store/message';
import {
  broadcastServiceDeskRead,
  setServiceDeskConversation,
} from '#/store/service-window';

import {
  latestServerSequence,
  markMessageFailed,
  mergeCursorMessages,
  mergeServerMessage,
} from '../message-state';
import {
  buildImageMessagePayload,
  buildQuoteTarget,
  buildTimelineItems,
  canRecallMessage,
  composerState,
  isSendEnter,
  latestOutgoingMessage,
  readReceipt,
  shouldMarkRead,
} from './service-state';

/** 草稿按「坐席 + 会话」隔离存储，切会话/切窗口都不串稿。 */
const DRAFT_KEY_PREFIX = 'aryn-service-desk-draft';
/** 会话消息首屏条数。 */
const PAGE_SIZE = 100;
/** 撤回时间窗刷新间隔：2 分钟窗过期后隐藏入口，30s 粒度足够。 */
const RECALL_CLOCK_INTERVAL_MS = 30_000;

export interface UseDeskMessagesOptions {
  /** 当前会话由页面持有：actions 等编排逻辑需要先于消息域引用它。 */
  active: Ref<Conversation | undefined>;
  isWindowMode: Ref<boolean>;
  messageStore: ReturnType<typeof useMessageStore>;
  /** 当前坐席 ID：草稿按坐席隔离。 */
  userId: Ref<string>;
  /** 当前会话是否可回复（由页面根据会话状态/权限推导）。 */
  canReply: () => boolean;
}

/**
 * 客服工作台消息域：打开会话 / 时间线 / 上翻历史 / 滚动跟随 / 发送 / 已读。
 *
 * 与页面解耦的边界：不加载列表、不处理页签、不弹转交等业务弹窗；页面负责编排。
 */
export function useDeskMessages(options: UseDeskMessagesOptions) {
  const active = options.active;
  const messages = ref<ViewChatMessage[]>([]);
  const draft = ref('');
  const timeline = ref<HTMLElement>();
  const messageLoading = ref(false);
  const messageLoadFailed = ref(false);
  const hasOlder = ref(false);
  const olderCursor = ref<number>();
  const olderLoading = ref(false);
  const stickToBottom = ref(true);
  const unreadWhileAway = ref(0);
  /** 打开会话前的已读游标：时间线据此画「以下为新消息」分隔。 */
  const unreadBoundarySeq = ref(0);
  /** 当前引用目标（composer 引用条）；发送后清空。 */
  const quote = ref<QuoteTarget>();
  /** 撤回时间窗时钟：定时刷新，超过 2 分钟自动隐藏「撤回」入口。 */
  const recallClock = ref(Date.now());
  /** 图片上传中：禁用图片入口并显示上传状态。 */
  const uploadingImage = ref(false);
  let recallTimer: ReturnType<typeof setInterval> | undefined;

  /** 打开会话的请求代际：慢网连点时只认最后一次请求的结果。 */
  let openGeneration = 0;

  const composer = computed<ComposerState>(() => composerState(active.value));
  /** 时间线渲染项：日期分隔 + 未读边界 + 同人聚合。 */
  const timelineItems = computed(() =>
    buildTimelineItems(messages.value, {
      unreadAfterSeq: unreadBoundarySeq.value,
    }),
  );
  const latestOutgoing = computed(() => latestOutgoingMessage(messages.value));
  const receipt = computed(() =>
    readReceipt(active.value?.peerLastReadSeq, latestOutgoing.value),
  );
  const receiptLabel = computed(() => {
    if (receipt.value === 'read') return '已读';
    if (receipt.value === 'unread') return '未读';
    return '';
  });

  /** 当前打开的会话是否仍然有效（防慢请求回来串会话）。 */
  function isCurrentOpen(generation: number, conversationId: string) {
    return generation === openGeneration && active.value?.id === conversationId;
  }

  function conversationDraftKey(conversationId: string) {
    return `${DRAFT_KEY_PREFIX}:${options.userId.value || 'unknown'}:${conversationId}`;
  }

  function loadConversationDraft(conversationId: string): string {
    try {
      return localStorage.getItem(conversationDraftKey(conversationId)) ?? '';
    } catch {
      return '';
    }
  }

  function saveConversationDraft(conversationId: string, value: string) {
    try {
      const key = conversationDraftKey(conversationId);
      if (value.trim()) {
        localStorage.setItem(key, value);
      } else {
        localStorage.removeItem(key);
      }
    } catch {
      // 隐私模式/存储不可用时静默降级：只影响草稿记忆，不影响发送
    }
  }

  /**
   * 跨窗口草稿同步：另一个窗口编辑同一会话的草稿时，本窗口跟着更新。
   *
   * 只同步当前打开的会话，避免把别的会话草稿灌进当前输入框。
   */
  function onDraftStorage(event: StorageEvent) {
    const conversationId = active.value?.id;
    if (!conversationId) return;
    if (event.key !== conversationDraftKey(conversationId)) return;
    const next = event.newValue ?? '';
    if (next !== draft.value) draft.value = next;
  }

  onMounted(() => {
    if (typeof window !== 'undefined') {
      window.addEventListener('storage', onDraftStorage);
    }
    recallTimer = setInterval(() => {
      recallClock.value = Date.now();
    }, RECALL_CLOCK_INTERVAL_MS);
  });
  onBeforeUnmount(() => {
    if (typeof window !== 'undefined') {
      window.removeEventListener('storage', onDraftStorage);
    }
    if (recallTimer) clearInterval(recallTimer);
  });

  /** 草稿随输入实时写回当前会话；切会话时由 openConversation 载入对应草稿。 */
  watch(draft, (value) => {
    const conversationId = active.value?.id;
    if (!conversationId) return;
    saveConversationDraft(conversationId, value);
  });

  /**
   * 打开会话。
   *
   * 先清空旧消息再发请求；响应回来后同时校验请求代际与 active.id，
   * 慢网连点不会把 A 会话的消息渲染到 B 会话里。
   */
  async function openConversation(conversation: Conversation) {
    const generation = ++openGeneration;
    active.value = conversation;
    if (options.isWindowMode.value) setServiceDeskConversation(conversation.id);
    // 草稿按会话隔离：切会话先落旧稿（watch 已实时保存），再载入新会话草稿。
    draft.value = loadConversationDraft(conversation.id);
    // 引用只对当前会话有效：切会话必须清空，避免把 A 会话的消息引用到 B 会话。
    quote.value = undefined;
    messages.value = [];
    messageLoadFailed.value = false;
    messageLoading.value = true;
    olderCursor.value = undefined;
    hasOlder.value = false;
    stickToBottom.value = true;
    unreadWhileAway.value = 0;
    // 先记住打开前的已读游标：markActiveRead 会立刻推进它，但界面仍需画出「新消息」分隔
    unreadBoundarySeq.value =
      conversation.lastSeq > (conversation.lastReadSeq ?? 0)
        ? (conversation.lastReadSeq ?? 0)
        : 0;
    try {
      const page = await getConversationMessages(conversation.id, {
        limit: PAGE_SIZE,
      });
      if (!isCurrentOpen(generation, conversation.id)) return;
      messages.value = [...page.records].reverse();
      olderCursor.value = page.nextBeforeSeq;
      hasOlder.value = page.hasMore === true && Boolean(page.nextBeforeSeq);
      await markActiveRead(generation);
    } catch {
      if (!isCurrentOpen(generation, conversation.id)) return;
      messages.value = [];
      messageLoadFailed.value = true;
    } finally {
      if (isCurrentOpen(generation, conversation.id)) {
        messageLoading.value = false;
      }
    }
    if (!isCurrentOpen(generation, conversation.id)) return;
    await nextTick();
    scrollToBottom('auto');
  }

  /** 推进当前会话已读游标：只有已分配的进行中会话才上报，避免越权。 */
  async function markActiveRead(generation: number) {
    const conversation = active.value;
    if (!conversation || !shouldMarkRead(conversation)) return;
    const latest = Math.max(
      latestServerSequence(messages.value),
      conversation.lastSeq || 0,
    );
    if (latest <= (conversation.lastReadSeq || 0)) return;
    try {
      await markConversationRead(conversation.id, latest);
    } catch {
      // 已读上报失败不阻塞查看消息；铃铛会在下次推送/刷新时收敛。
      return;
    }
    if (!isCurrentOpen(generation, conversation.id)) return;
    const next = { ...conversation, lastReadSeq: latest, unreadCount: 0 };
    active.value = next;
    // 跨窗收敛：另一个窗口的铃铛/列表未读需要跟着降下来（无服务端已读推送）。
    broadcastServiceDeskRead(conversation.id, latest);
    void options.messageStore.refreshNotifications();
  }

  function scrollToBottom(behavior: ScrollBehavior = 'smooth') {
    stickToBottom.value = true;
    unreadWhileAway.value = 0;
    timeline.value?.scrollTo({
      behavior,
      top: timeline.value.scrollHeight,
    });
  }

  /** 时间线滚动：距底 ≤80px 视为跟随；滚到顶部附近自动加载更早消息。 */
  function onTimelineScroll() {
    const element = timeline.value;
    if (!element) return;
    const distance =
      element.scrollHeight - element.scrollTop - element.clientHeight;
    stickToBottom.value = distance <= 80;
    if (stickToBottom.value) unreadWhileAway.value = 0;
    if (element.scrollTop <= 40 && hasOlder.value && !olderLoading.value) {
      void loadOlder();
    }
  }

  /** 上翻加载更早消息：插入后按高度差回填滚动位置，避免视口跳动。 */
  async function loadOlder() {
    const conversation = active.value;
    const cursor = olderCursor.value;
    if (!conversation || !cursor || olderLoading.value || !hasOlder.value) {
      return;
    }
    const generation = openGeneration;
    const conversationId = conversation.id;
    const element = timeline.value;
    const previousHeight = element?.scrollHeight ?? 0;
    const previousTop = element?.scrollTop ?? 0;
    olderLoading.value = true;
    try {
      const page = await getConversationMessages(conversationId, {
        beforeSeq: cursor,
        limit: PAGE_SIZE,
      });
      if (!isCurrentOpen(generation, conversationId)) return;
      const known = new Set(messages.value.map((item) => item.id));
      const older = [...page.records]
        .reverse()
        .filter((item) => !known.has(item.id));
      if (older.length > 0) {
        messages.value = [...older, ...messages.value];
      }
      olderCursor.value = page.nextBeforeSeq;
      hasOlder.value = page.hasMore === true && Boolean(page.nextBeforeSeq);
      await nextTick();
      if (timeline.value) {
        timeline.value.scrollTop =
          timeline.value.scrollHeight - previousHeight + previousTop;
      }
    } catch {
      ElMessage.warning('更早的消息加载失败，请重试');
    } finally {
      olderLoading.value = false;
    }
  }

  /**
   * 用服务端快照覆盖本地同 id 消息（撤回等就地变化），再补齐新消息。
   *
   * 增量补拉只看 afterSeq，被撤回的是老消息（seqNo 不在增量区间），
   * 所以推送带 messageId 且该消息已在本地时，改拉最新一页做快照覆盖。
   */
  function mergeServerSnapshots(
    current: ViewChatMessage[],
    incoming: ChatMessage[],
  ): ViewChatMessage[] {
    const byId = new Map(incoming.map((item) => [item.id, item]));
    const merged = current.map((item): ViewChatMessage => {
      const server = byId.get(item.id);
      return server ? { ...server, sendState: 'sent' } : item;
    });
    const known = new Set(merged.map((item) => item.id));
    for (const message of incoming) {
      if (known.has(message.id)) continue;
      merged.push({ ...message, sendState: 'sent' });
    }
    return merged.sort((first, second) => first.seqNo - second.seqNo);
  }

  /**
   * 推送到达后刷新当前会话，并按需推进已读游标。
   *
   * 普通新消息按 afterSeq 增量补拉；推送带 messageId 且该消息已存在本地时，
   * 视为撤回/变更（契约 §16.4 复用 CONVERSATION_MESSAGE），改拉最新一页覆盖。
   */
  async function refreshActiveConversation(pushMessageId?: string) {
    const conversation = active.value;
    if (!conversation) return;
    const generation = openGeneration;
    const knownBefore = pushMessageId
      ? messages.value.find((item) => item.id === pushMessageId)
      : undefined;
    try {
      if (knownBefore && knownBefore.recallStatus !== '1') {
        const latestPage = await getConversationMessages(conversation.id, {
          limit: PAGE_SIZE,
        });
        if (!isCurrentOpen(generation, conversation.id)) return;
        messages.value = mergeServerSnapshots(
          messages.value,
          latestPage.records,
        );
        await markActiveRead(generation);
        return;
      }
      const afterSeq = latestServerSequence(messages.value);
      const page = await getConversationMessages(conversation.id, {
        afterSeq,
        limit: PAGE_SIZE,
      });
      if (!isCurrentOpen(generation, conversation.id)) return;
      if (page.records.length > 0) {
        messages.value = mergeCursorMessages(messages.value, page.records);
        if (stickToBottom.value) {
          await nextTick();
          scrollToBottom('auto');
        } else {
          // 用户正在翻旧消息：不拽回底部，改为提示有新消息可跳转
          unreadWhileAway.value += page.records.length;
        }
      }
      await markActiveRead(generation);
    } catch {
      // 实时刷新失败不影响页面可用性，等下一次推送或手动刷新。
    }
  }

  /** 输入法组合中的 Enter 是确认候选词，不能当发送；Shift+Enter 换行。 */
  function onComposerKeydown(event: Event) {
    if (!(event instanceof KeyboardEvent)) return;
    if (!isSendEnter(event)) return;
    event.preventDefault();
    void send();
  }

  /** 设置引用目标（气泡「引用」操作）；不可引用消息给提示。 */
  function setQuote(message: ViewChatMessage) {
    const target = buildQuoteTarget(message, active.value);
    if (!target) {
      ElMessage.warning('该消息不支持引用');
      return;
    }
    quote.value = target;
  }

  function clearQuote() {
    quote.value = undefined;
  }

  /** 追加本地乐观消息（文字/图片），返回 clientMessageId 供幂等发送与失败重发。 */
  function appendOptimisticMessage(input: {
    content?: string;
    conversationId: string;
    messageType: ViewChatMessage['messageType'];
    payload?: string;
    quote?: QuoteTarget;
  }): string {
    const clientMessageId = nanoid();
    const optimistic: ViewChatMessage = {
      clientMessageId,
      conversationId: input.conversationId,
      createTime: new Date().toISOString(),
      id: `local:${clientMessageId}`,
      messageType: input.messageType,
      seqNo: Number.MAX_SAFE_INTEGER,
      senderId: 'current',
      senderName: '我',
      senderType: 'SYS_USER',
      sendState: 'sending',
    };
    if (input.content) optimistic.content = input.content;
    if (input.payload) optimistic.payload = input.payload;
    if (input.quote) {
      optimistic.quotedMessageId = input.quote.id;
      optimistic.quotedMessageType = input.quote.messageType;
      optimistic.quotedMessageSummary = input.quote.summary;
      optimistic.quotedRecalled = false;
      optimistic.quotedSenderName = input.quote.senderName;
    }
    messages.value.push(optimistic);
    return clientMessageId;
  }

  async function send() {
    const conversation = active.value;
    const content = draft.value.trim();
    if (!conversation || !content || messageLoading.value) return;
    if (!options.canReply()) return;
    const conversationId = conversation.id;
    const quoted = quote.value;
    const clientMessageId = appendOptimisticMessage({
      content,
      conversationId,
      messageType: 'TEXT',
      quote: quoted,
    });
    draft.value = '';
    quote.value = undefined;
    await nextTick();
    scrollToBottom();
    try {
      const message = await sendConversationMessage(conversationId, {
        clientMessageId,
        content,
        messageType: 'TEXT',
        ...(quoted ? { quotedMessageId: quoted.id } : {}),
      });
      // 响应回来时会话可能已切换：只有仍停留在原会话才合并到时间线，
      // 否则交给重新打开时的历史加载去合并。
      if (active.value?.id === conversationId) {
        messages.value = mergeServerMessage(messages.value, message);
        await nextTick();
        scrollToBottom();
      }
    } catch {
      // 请求层已统一弹出失败原因；气泡标记为「发送失败，点击重发」。
      if (active.value?.id === conversationId) {
        messages.value = markMessageFailed(messages.value, clientMessageId);
      }
    }
  }

  /**
   * 选择图片 → 上传 → 发送 IMAGE 消息（契约 §17.1）。
   *
   * 上传失败不产生空消息；上传成功后先插入本地乐观图片消息再调发送接口，
   * 发送失败沿用既有「点击重发」路径（payload 原样保留）。
   */
  async function uploadAndSendImage(file: File) {
    const conversation = active.value;
    if (!conversation || uploadingImage.value || messageLoading.value) return;
    if (!options.canReply()) return;
    const conversationId = conversation.id;
    const quoted = quote.value;
    uploadingImage.value = true;
    try {
      const upload = await uploadConversationImage(file);
      if (active.value?.id !== conversationId) return;
      quote.value = undefined;
      const payload = buildImageMessagePayload(upload);
      const clientMessageId = appendOptimisticMessage({
        conversationId,
        messageType: 'IMAGE',
        payload,
        quote: quoted,
      });
      await nextTick();
      scrollToBottom();
      try {
        const message = await sendConversationMessage(conversationId, {
          clientMessageId,
          messageType: 'IMAGE',
          payload,
          ...(quoted ? { quotedMessageId: quoted.id } : {}),
        });
        if (active.value?.id === conversationId) {
          messages.value = mergeServerMessage(messages.value, message);
          await nextTick();
          scrollToBottom();
        }
      } catch {
        if (active.value?.id === conversationId) {
          messages.value = markMessageFailed(messages.value, clientMessageId);
        }
      }
    } catch {
      // 上传失败：请求层已 toast（大小/类型/存储未配置/服务不可用），不产生空消息
    } finally {
      uploadingImage.value = false;
    }
  }

  /** 点击失败气泡重发：复用原 clientMessageId，服务端幂等去重。 */
  async function resend(message: ViewChatMessage) {
    const conversation = active.value;
    if (!conversation || message.sendState !== 'failed') return;
    if (!options.canReply()) return;
    const conversationId = conversation.id;
    messages.value = messages.value.map((item) =>
      item.clientMessageId === message.clientMessageId
        ? { ...item, sendState: 'sending' }
        : item,
    );
    try {
      const serverMessage = await sendConversationMessage(conversationId, {
        clientMessageId: message.clientMessageId,
        content: message.content,
        messageType: message.messageType,
        payload: message.payload,
        ...(message.quotedMessageId
          ? { quotedMessageId: message.quotedMessageId }
          : {}),
      });
      if (active.value?.id === conversationId) {
        messages.value = mergeServerMessage(messages.value, serverMessage);
        await nextTick();
        scrollToBottom();
      }
    } catch {
      // 请求层已统一提示；失败气泡继续保留，可再次点击重发。
      if (active.value?.id === conversationId) {
        messages.value = markMessageFailed(
          messages.value,
          message.clientMessageId,
        );
      }
    }
  }

  /**
   * 撤回消息（契约 §16）：前端先按「本人 + TEXT/IMAGE + 2 分钟窗 + 会话未关闭」
   * 过滤并二次确认，后端仍做最终校验；成功后用返回的脱敏快照覆盖本地消息。
   */
  async function recallMessage(message: ViewChatMessage): Promise<boolean> {
    const conversation = active.value;
    if (!conversation) return false;
    if (
      !canRecallMessage(message, {
        conversationStatus: conversation.status,
        now: recallClock.value,
        userId: options.userId.value,
      })
    ) {
      ElMessage.warning('该消息已超过撤回时间或不可撤回');
      return false;
    }
    const confirmed = await ElMessageBox.confirm(
      '撤回后对方将看到「消息已撤回」，且不可恢复。确认撤回？',
      '撤回消息',
      {
        cancelButtonText: '取消',
        confirmButtonText: '撤回',
        type: 'warning',
      },
    )
      .then(() => true)
      .catch(() => false);
    if (!confirmed) return false;
    const conversationId = conversation.id;
    try {
      const updated = await recallConversationMessage(
        conversationId,
        message.id,
      );
      if (active.value?.id === conversationId) {
        messages.value = mergeServerMessage(messages.value, updated);
      }
      ElMessage.success('消息已撤回');
      return true;
    } catch {
      // 请求层已提示原因（超时/非本人/会话已关闭等）
      return false;
    }
  }

  /** 清空当前会话（转交/关闭后由页面调用）。 */
  function clearActive() {
    openGeneration += 1;
    active.value = undefined;
    messages.value = [];
    draft.value = '';
    quote.value = undefined;
    hasOlder.value = false;
    olderCursor.value = undefined;
    unreadWhileAway.value = 0;
    unreadBoundarySeq.value = 0;
  }

  return {
    active,
    clearActive,
    clearQuote,
    composer,
    draft,
    hasOlder,
    loadOlder,
    messages,
    messageLoadFailed,
    messageLoading,
    onComposerKeydown,
    onTimelineScroll,
    openConversation,
    olderLoading,
    quote,
    receipt,
    receiptLabel,
    recallClock,
    recallMessage,
    refreshActiveConversation,
    resend,
    scrollToBottom,
    send,
    setQuote,
    stickToBottom,
    timeline,
    timelineItems,
    unreadWhileAway,
    uploadingImage,
    uploadAndSendImage,
  };
}
