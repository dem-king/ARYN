<script setup lang="ts">
import type { ViewChatMessage } from '../message-state';

import type {
  ConversationContext,
  OrderContextItem,
} from '#/api/message/context';
import type { Conversation } from '#/api/message/types';

import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import {
  ElButton,
  ElCollapse,
  ElCollapseItem,
  ElMessage,
  ElTag,
} from 'element-plus';

import { getConversationContext } from '#/api/message/context';

import {
  extractConversationCards,
  formatDeliveryWay,
  formatOrderAmount,
  memberDisplayName,
  memberInitial,
  normalizeRecentOrders,
  orderDetailLink,
  orderStatusLabel,
  orderStatusTone,
} from './context-utils';
import {
  conversationDisplayName,
  conversationOwnerName,
  conversationStatusLabel,
  conversationStatusTone,
  formatContextPayload,
  formatDateTime,
} from './service-state';

/**
 * 右侧「会话上下文」面板。
 *
 * 旧实现是调试型扁平 dl + 原始 JSON pretty print，坐席需要的信息（会员资料、
 * 最近订单、关联商品/订单卡片、状态与关键时间）反而难找。现在按优先级分模块：
 * ① 会话状态卡 ② 会员 ③ 最近订单 ④ 卡片消息索引 ⑤ 会话上下文（默认折叠）。
 *
 * 会员/最近订单来自 message 域聚合接口 `GET /message/staff/conversation/{id}/context`
 * （P2-A 会员订单上下文契约 §8），失败只降级 ②③ 两个模块并给出重试，
 * 不阻塞其余模块；切换会话用请求代际校验，避免慢请求把上一个会话的数据写串。
 */
const props = defineProps<{
  conversation?: Conversation;
  dismissible?: boolean;
  messages: ViewChatMessage[];
}>();

const emit = defineEmits<{ close: [] }>();

const router = useRouter();

const title = computed(() => conversationDisplayName(props.conversation));
const statusLabel = computed(() =>
  conversationStatusLabel(props.conversation?.status),
);
const statusTone = computed(() =>
  conversationStatusTone(props.conversation?.status),
);
const ownerName = computed(() => conversationOwnerName(props.conversation));
const closedTime = computed(() =>
  formatDateTime(props.conversation?.closedTime),
);
const reopenDeadline = computed(() =>
  formatDateTime(props.conversation?.reopenDeadline),
);
const lastMessageTime = computed(() =>
  formatDateTime(props.conversation?.lastMessageTime),
);
const cards = computed(() => extractConversationCards(props.messages));
const contextPayload = computed(() =>
  formatContextPayload(props.conversation?.contextPayload),
);

/* ---------------------------------------------------------------------- */
/*  会员 / 最近订单：聚合接口 + 请求代际 + 失败只降级本模块（P2-A §8）        */
/* ---------------------------------------------------------------------- */

const contextLoading = ref(false);
const contextError = ref('');
const conversationContext = ref<ConversationContext>();

/** 请求代际：切换会话/重试都会自增，旧请求返回时据此丢弃，避免慢请求串会话。 */
let contextGeneration = 0;

const member = computed(() => conversationContext.value?.member ?? null);
const memberName = computed(() => memberDisplayName(member.value));
const memberInitialText = computed(() => memberInitial(member.value));
const memberAvatar = computed(() => member.value?.avatar?.trim() ?? '');
const memberId = computed(
  () => member.value?.id?.trim() || props.conversation?.customerId || '',
);
/** 订单行连同「可跳转链接」一起算好，模板不必重复调用纯函数。 */
const orderRows = computed(() =>
  normalizeRecentOrders(conversationContext.value?.orders).map((order) => ({
    link: orderDetailLink(order.orderId),
    order,
  })),
);

/** 失败提示取后端中文 msg（契约 §8.4），取不到时用通用文案。 */
function contextErrorMessage(error: unknown): string {
  const message = (error as { response?: { data?: { msg?: unknown } } })
    ?.response?.data?.msg;
  return typeof message === 'string' && message.trim()
    ? message.trim()
    : '会员 / 订单信息加载失败';
}

async function loadConversationContext(conversationId: string) {
  const generation = (contextGeneration += 1);
  contextLoading.value = true;
  contextError.value = '';
  try {
    const data = await getConversationContext(conversationId);
    // 请求期间已切换会话：丢弃旧响应，避免把上一个会话的会员/订单写进面板
    if (generation !== contextGeneration) return;
    conversationContext.value = data;
  } catch (error) {
    if (generation !== contextGeneration) return;
    conversationContext.value = undefined;
    contextError.value = contextErrorMessage(error);
  } finally {
    if (generation === contextGeneration) contextLoading.value = false;
  }
}

function retryConversationContext() {
  const conversationId = props.conversation?.id;
  if (conversationId) void loadConversationContext(conversationId);
}

function openOrder(order: OrderContextItem) {
  const link = orderDetailLink(order.orderId);
  if (!link) return;
  void router.push(link);
}

watch(
  () => props.conversation?.id,
  (conversationId) => {
    // 先自增代际：上一个会话的在途请求返回时会被丢弃
    contextGeneration += 1;
    conversationContext.value = undefined;
    contextError.value = '';
    contextLoading.value = false;
    if (conversationId) void loadConversationContext(conversationId);
  },
  { immediate: true },
);

const CARD_TYPE_LABEL: Record<string, string> = {
  IMAGE: '图片',
  NOTICE_CARD: '通知',
  ORDER_CARD: '订单',
  PRODUCT_CARD: '商品',
  REFUND_CARD: '退款',
};

function openCard(link?: string) {
  if (!link) return;
  void router.push(link);
}

async function copyConversationId() {
  const conversationId = props.conversation?.id;
  if (!conversationId) return;
  try {
    await navigator.clipboard.writeText(conversationId);
    ElMessage.success('已复制会话 ID');
  } catch {
    ElMessage.warning('复制失败，请手动选择文本复制');
  }
}
</script>

<template>
  <aside class="info-panel">
    <div class="panel-heading">
      <strong>会话上下文</strong>
      <ElButton
        v-if="dismissible"
        class="panel-close"
        size="small"
        text
        @click="emit('close')"
      >
        收起
      </ElButton>
    </div>

    <template v-if="conversation">
      <!-- ① 会话状态卡 -->
      <section class="panel-section">
        <div class="section-title">
          <span>会话状态</span>
          <ElTag :type="statusTone" effect="plain" round size="small">
            {{ statusLabel }}
          </ElTag>
        </div>
        <dl class="info-grid">
          <div>
            <dt>会话标题</dt>
            <dd>{{ title }}</dd>
          </div>
          <div>
            <dt>会员 ID</dt>
            <dd>{{ conversation.customerId || '—' }}</dd>
          </div>
          <div>
            <dt>负责客服</dt>
            <dd>{{ ownerName }}</dd>
          </div>
          <div>
            <dt>会话队列</dt>
            <dd>{{ conversation.queueCode || 'DEFAULT' }}</dd>
          </div>
          <div>
            <dt>最后消息</dt>
            <dd>{{ lastMessageTime || '—' }}</dd>
          </div>
          <div v-if="closedTime">
            <dt>关闭时间</dt>
            <dd>{{ closedTime }}</dd>
          </div>
          <div v-if="reopenDeadline">
            <dt>重开截止</dt>
            <dd>{{ reopenDeadline }}</dd>
          </div>
          <div>
            <dt>未读消息</dt>
            <dd>{{ conversation.unreadCount || 0 }}</dd>
          </div>
        </dl>
        <ElButton class="copy-id" size="small" text @click="copyConversationId">
          复制会话 ID
        </ElButton>
      </section>

      <!-- ② 会员（P2-A §8：头像/昵称/会员 ID，缺失回退） -->
      <section
        class="panel-section"
        :aria-busy="contextLoading ? 'true' : undefined"
      >
        <div class="section-title">
          <span>会员</span>
          <small v-if="memberId && !contextLoading && !contextError">
            ID {{ memberId }}
          </small>
        </div>

        <div v-if="contextLoading" class="section-state" role="status">
          会员信息加载中…
        </div>
        <div
          v-else-if="contextError"
          class="section-state section-state--error"
          role="alert"
        >
          <span>{{ contextError }}</span>
          <ElButton
            class="retry-button"
            size="small"
            text
            @click="retryConversationContext"
          >
            重试
          </ElButton>
        </div>
        <p v-else-if="!member" class="section-empty">暂无会员信息</p>
        <div v-else class="member-card">
          <img
            v-if="memberAvatar"
            :src="memberAvatar"
            alt=""
            class="member-avatar"
          />
          <span
            v-else
            class="member-avatar member-avatar--fallback"
            aria-hidden="true"
          >
            {{ memberInitialText }}
          </span>
          <span class="member-copy">
            <strong class="member-name">{{ memberName }}</strong>
            <small class="member-id">会员 ID：{{ memberId || '—' }}</small>
          </span>
        </div>
      </section>

      <!-- ③ 最近订单（P2-A §8：最多 3 条，点击跳订单详情） -->
      <section
        class="panel-section"
        :aria-busy="contextLoading ? 'true' : undefined"
      >
        <div class="section-title">
          <span>最近订单</span>
          <small v-if="!contextLoading && !contextError">
            {{ orderRows.length }} 条
          </small>
        </div>

        <div v-if="contextLoading" class="section-state" role="status">
          订单加载中…
        </div>
        <div
          v-else-if="contextError"
          class="section-state section-state--error"
          role="alert"
        >
          <span>订单信息加载失败</span>
          <ElButton
            class="retry-button"
            size="small"
            text
            @click="retryConversationContext"
          >
            重试
          </ElButton>
        </div>
        <p v-else-if="orderRows.length === 0" class="section-empty">暂无订单</p>
        <ul v-else class="order-list">
          <li
            v-for="row in orderRows"
            :key="row.order.orderId"
            class="order-row"
          >
            <button
              type="button"
              class="order-button"
              :disabled="!row.link"
              :aria-label="`查看订单 ${row.order.orderNo?.trim() || row.order.orderId} 详情`"
              @click="openOrder(row.order)"
            >
              <span class="order-head">
                <strong class="order-no">
                  {{ row.order.orderNo?.trim() || row.order.orderId }}
                </strong>
                <ElTag
                  v-if="
                    orderStatusLabel(row.order.status, row.order.statusName)
                  "
                  :type="orderStatusTone(row.order.status)"
                  effect="plain"
                  round
                  size="small"
                >
                  {{ orderStatusLabel(row.order.status, row.order.statusName) }}
                </ElTag>
              </span>
              <span class="order-meta">
                <span
                  v-if="formatOrderAmount(row.order.payAmount)"
                  class="order-amount"
                >
                  {{ formatOrderAmount(row.order.payAmount) }}
                </span>
                <span
                  v-if="formatDeliveryWay(row.order.deliveryWay)"
                  class="order-delivery"
                >
                  {{ formatDeliveryWay(row.order.deliveryWay) }}
                </span>
                <span
                  v-if="formatDateTime(row.order.createTime ?? undefined)"
                  class="order-time"
                >
                  {{ formatDateTime(row.order.createTime ?? undefined) }}
                </span>
              </span>
              <span
                v-if="row.order.productSummary?.trim()"
                class="order-summary"
              >
                {{ row.order.productSummary.trim() }}
              </span>
            </button>
          </li>
        </ul>
      </section>

      <!-- ④ 卡片消息索引 -->
      <section class="panel-section">
        <div class="section-title">
          <span>卡片消息</span>
          <small>{{ cards.length }} 条</small>
        </div>
        <p v-if="cards.length === 0" class="section-empty">
          本次会话暂无商品 / 订单 / 退款卡片
        </p>
        <ul v-else class="card-list">
          <li
            v-for="card in cards"
            :key="card.id"
            class="card-item"
            :class="{ clickable: Boolean(card.link) }"
            @click="openCard(card.link)"
          >
            <span class="card-type">{{
              CARD_TYPE_LABEL[card.type] || '卡片'
            }}</span>
            <span class="card-copy">
              <strong>{{ card.title }}</strong>
              <small v-if="card.summary">{{ card.summary }}</small>
            </span>
            <span v-if="card.link" class="card-arrow">›</span>
          </li>
        </ul>
      </section>

      <!-- ⑤ 会话上下文（默认折叠） -->
      <ElCollapse v-if="contextPayload" class="context-collapse">
        <ElCollapseItem title="会话上下文（原始 JSON）" name="context">
          <pre class="context-json">{{ contextPayload }}</pre>
        </ElCollapseItem>
      </ElCollapse>
    </template>

    <p v-else class="panel-empty">未选择会话</p>
  </aside>
</template>

<style scoped>
.info-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  width: 100%;
  min-width: 280px;
  max-width: 320px;
  padding: 18px;
  overflow: auto;
  background: var(--desk-surface);
  border-left: 1px solid var(--desk-line-faint);
}

.panel-heading {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: space-between;
}

.panel-heading strong {
  font-size: 14px;
  color: var(--desk-text);
}

.panel-close {
  color: var(--desk-muted);
}

.panel-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--desk-line-faint);
}

.panel-section:last-of-type {
  border-bottom: 0;
}

.section-title {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
  color: var(--desk-muted);
}

.section-title small {
  font-size: 11px;
  font-weight: 400;
  color: var(--desk-dim);
}

.section-empty {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--desk-dim);
}

.info-grid {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin: 0;
}

.info-grid div {
  display: flex;
  gap: 10px;
  align-items: baseline;
  justify-content: space-between;
}

.info-grid dt {
  flex: none;
  font-size: 12px;
  color: var(--desk-dim);
}

.info-grid dd {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--desk-text);
  white-space: nowrap;
}

.copy-id {
  align-self: flex-start;
  padding: 0;
  font-size: 11px;
  color: var(--desk-accent-strong);
}

/* 会员卡 / 最近订单：加载态、失败重试与空态共用同一套轻量排版 */
.section-state {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  align-items: baseline;
  justify-content: space-between;
  font-size: 12px;
  line-height: 1.6;
  color: var(--desk-muted);
}

.section-state--error {
  color: var(--desk-warning);
}

.retry-button {
  padding: 0;
  font-size: 11px;
  color: var(--desk-accent-strong);
}

.member-card {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.member-avatar {
  flex: none;
  width: 38px;
  height: 38px;
  object-fit: cover;
  background: var(--desk-avatar-bg);
  border: 1px solid var(--desk-avatar-line);
  border-radius: 50%;
}

.member-avatar--fallback {
  display: grid;
  place-items: center;
  font-size: 15px;
  font-weight: 600;
  color: var(--desk-accent-strong);
}

.member-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.member-name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 600;
  color: var(--desk-text);
  white-space: nowrap;
}

.member-id {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  color: var(--desk-dim);
  white-space: nowrap;
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.order-row {
  min-width: 0;
}

.order-button {
  display: flex;
  flex-direction: column;
  gap: 5px;
  width: 100%;
  padding: 8px 10px;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: var(--desk-canvas);
  border: 1px solid var(--desk-line-faint);
  border-radius: 10px;
}

.order-button:hover {
  background: var(--desk-hover);
  border-color: var(--desk-accent-line);
}

.order-button:disabled {
  cursor: default;
}

.order-head {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
}

.order-no {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  font-weight: 500;
  color: var(--desk-text);
  white-space: nowrap;
}

.order-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  align-items: center;
  font-size: 11px;
  color: var(--desk-muted);
}

.order-amount {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--desk-accent-strong);
}

.order-delivery {
  padding: 0 5px;
  font-size: 10px;
  color: var(--desk-muted);
  border: 1px solid var(--desk-line);
  border-radius: 4px;
}

.order-time {
  font-variant-numeric: tabular-nums;
  color: var(--desk-dim);
}

.order-summary {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  color: var(--desk-muted);
  white-space: nowrap;
}

.card-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.card-item {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 8px 10px;
  font-size: 12px;
  background: var(--desk-canvas);
  border: 1px solid var(--desk-line-faint);
  border-radius: 10px;
}

.card-item.clickable {
  cursor: pointer;
}

.card-item.clickable:hover {
  background: var(--desk-hover);
  border-color: var(--desk-accent-line);
}

.card-type {
  flex: none;
  padding: 1px 6px;
  font-size: 10px;
  color: var(--desk-accent-strong);
  background: var(--desk-accent-soft);
  border-radius: 5px;
}

.card-copy {
  flex: 1;
  min-width: 0;
}

.card-copy strong,
.card-copy small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-copy strong {
  font-size: 12px;
  font-weight: 500;
  color: var(--desk-text);
}

.card-copy small {
  margin-top: 3px;
  font-size: 11px;
  color: var(--desk-muted);
}

.card-arrow {
  flex: none;
  font-size: 14px;
  color: var(--desk-dim);
}

.context-collapse {
  --el-collapse-border-color: var(--desk-line-faint);
  --el-collapse-header-bg-color: transparent;
  --el-collapse-content-bg-color: transparent;
  --el-collapse-header-text-color: var(--desk-muted);
}

.context-json {
  margin: 0;
  overflow: auto;
  font-size: 11px;
  line-height: 1.6;
  color: var(--desk-muted);
  word-break: break-all;
  white-space: pre-wrap;
}

.panel-empty {
  font-size: 12px;
  color: var(--desk-dim);
}
</style>
