<script setup lang="ts">
import type { ViewChatMessage } from './message-state';

import { computed } from 'vue';
import { useRouter } from 'vue-router';

import { chatCardLink, parseMessageCard } from './message-state';

/**
 * C 端发来的结构化消息（图片 / 商品·订单·退款·通知卡片）在管理端的渲染。
 * C 端发送侧负载字段：PRODUCT_CARD 为 image/title/summary/productId，
 * 其余卡片至少带各自业务主键，图片为 url。
 */
const props = defineProps<{ message: ViewChatMessage }>();

const router = useRouter();

const card = computed(() => parseMessageCard(props.message.payload));
const link = computed(() =>
  chatCardLink(props.message.messageType, card.value),
);

const typeLabel = computed(() => {
  const labels: Record<string, string> = {
    IMAGE: '图片',
    NOTICE_CARD: '通知卡片',
    ORDER_CARD: '订单卡片',
    PRODUCT_CARD: '商品卡片',
    REFUND_CARD: '退款卡片',
  };
  return labels[props.message.messageType] ?? '卡片消息';
});

const imageUrl = computed(() => {
  const value =
    props.message.messageType === 'IMAGE' ? card.value.url : card.value.image;
  return typeof value === 'string' && value ? value : '';
});

const title = computed(() => {
  const value = card.value.title;
  return typeof value === 'string' && value ? value : typeLabel.value;
});

const summary = computed(() => {
  const value = card.value.summary;
  return typeof value === 'string' && value && value !== title.value
    ? value
    : '';
});

function open() {
  if (!link.value) return;
  void router.push(link.value);
}
</script>

<template>
  <img
    v-if="message.messageType === 'IMAGE' && imageUrl"
    :src="imageUrl"
    class="chat-image"
  />
  <div v-else class="chat-card" :class="{ clickable: link }" @click="open">
    <img v-if="imageUrl" :src="imageUrl" class="chat-card-cover" />
    <span class="chat-card-copy">
      <small>{{ typeLabel }}</small>
      <strong>{{ title }}</strong>
      <small v-if="summary">{{ summary }}</small>
    </span>
    <span v-if="link" class="chat-card-arrow">›</span>
  </div>
</template>

<style scoped>
.chat-image {
  display: block;
  max-width: 260px;
  margin: 7px 0;
  border: 1px solid #1a3a4b;
  border-radius: 10px;
}

.chat-card {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 220px;
  max-width: 320px;
  padding: 10px 12px;
  margin: 7px 0;
  text-align: left;
  background: #0b202c;
  border: 1px solid #1d4356;
  border-radius: 10px;
}

.chat-card.clickable {
  cursor: pointer;
}

.chat-card.clickable:hover {
  border-color: rgb(45 212 191 / 40%);
}

.chat-card-cover {
  flex: none;
  width: 44px;
  height: 44px;
  object-fit: cover;
  border-radius: 8px;
}

.chat-card-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.chat-card-copy strong {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  color: #e6fffb;
  white-space: nowrap;
}

.chat-card-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 10px;
  color: #6d93a8;
  white-space: nowrap;
}

.chat-card-arrow {
  flex: none;
  color: #5eead4;
}
</style>
