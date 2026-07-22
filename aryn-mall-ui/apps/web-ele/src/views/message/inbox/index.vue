<script setup lang="ts">
import type { NoticeInboxItem } from '#/api/message/types';

import { onMounted, ref } from 'vue';

import { ElButton, ElEmpty, ElMessageBox, ElTag } from 'element-plus';

import {
  getStaffNoticeInbox,
  hideStaffNotice,
  markAllStaffNoticesRead,
  markStaffNoticeRead,
} from '#/api/message/notice';

const records = ref<NoticeInboxItem[]>([]);
const nextCursor = ref<string>();
const hasMore = ref(false);

async function load(reset = false) {
  const page = await getStaffNoticeInbox({
    cursor: reset ? undefined : nextCursor.value,
    limit: 20,
  });
  records.value = reset ? page.records : [...records.value, ...page.records];
  nextCursor.value = page.nextCursor;
  hasMore.value = page.hasMore;
}

async function read(item: NoticeInboxItem) {
  if (item.readStatus === '0') {
    await markStaffNoticeRead(item.messageId);
    item.readStatus = '1';
  }
}

async function hide(item: NoticeInboxItem) {
  await ElMessageBox.confirm(
    '隐藏只影响你的收件箱，不会删除通知历史。',
    '隐藏通知',
  );
  await hideStaffNotice(item.messageId);
  records.value = records.value.filter(
    (row) => row.messageId !== item.messageId,
  );
}

async function readAll() {
  await markAllStaffNoticesRead();
  records.value.forEach((item) => (item.readStatus = '1'));
}

onMounted(() => load(true));
</script>

<template>
  <main class="inbox-page p-5">
    <header class="inbox-header">
      <div>
        <p class="eyebrow">STAFF INBOX</p>
        <h1>工作人员收件箱</h1>
        <p>通知只属于当前登录身份，已读与隐藏操作不会影响其他人。</p>
      </div>
      <ElButton type="primary" plain @click="readAll">全部已读</ElButton>
    </header>
    <section v-if="records.length > 0" class="notice-stack">
      <article
        v-for="item in records"
        :key="item.recipientRecordId"
        class="notice-card"
        :class="{ unread: item.readStatus === '0' }"
        @click="read(item)"
      >
        <div class="notice-marker"></div>
        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2">
            <h2>{{ item.title }}</h2>
            <ElTag size="small" effect="plain">{{ item.category }}</ElTag>
          </div>
          <p>{{ item.summary || item.content }}</p>
          <div class="meta">
            <span>{{ item.senderName || '系统' }}</span
            ><span>{{ item.publishTime }}</span>
          </div>
        </div>
        <ElButton link type="info" @click.stop="hide(item)">隐藏</ElButton>
      </article>
      <ElButton v-if="hasMore" class="w-full" @click="load(false)">
        加载更早通知
      </ElButton>
    </section>
    <ElEmpty v-else description="收件箱很安静" />
  </main>
</template>

<style scoped>
.inbox-page {
  min-height: 100%;
  background: linear-gradient(180deg, #eef6f4, #f7f8fa 280px);
}

.inbox-header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  max-width: 980px;
  padding: 28px 0 14px;
  margin: 0 auto 24px;
}

.inbox-header h1 {
  margin: 5px 0;
  font-family: 'Noto Serif SC', serif;
  font-size: 30px;
  color: #16324f;
}

.inbox-header p {
  color: #6b8296;
}

.eyebrow {
  font-size: 11px;
  font-weight: 800;
  color: #0f766e !important;
  letter-spacing: 0.2em;
}

.notice-stack {
  max-width: 980px;
  margin: auto;
}

.notice-card {
  position: relative;
  display: flex;
  gap: 20px;
  align-items: start;
  padding: 22px 24px;
  margin-bottom: 12px;
  overflow: hidden;
  background: rgb(255 255 255 / 90%);
  border: 1px solid #dbe7e4;
  border-radius: 14px;
  transition:
    transform 0.2s,
    box-shadow 0.2s;
}

.notice-card:hover {
  box-shadow: 0 15px 34px rgb(41 71 93 / 10%);
  transform: translateY(-2px);
}

.notice-card.unread {
  border-color: rgb(15 118 110 / 35%);
}

.notice-marker {
  align-self: stretch;
  width: 4px;
  background: #cbd5e1;
  border-radius: 4px;
}

.unread .notice-marker {
  background: #0f766e;
}

.notice-card h2 {
  font-size: 16px;
  font-weight: 700;
  color: #193b56;
}

.notice-card p {
  margin: 9px 0 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #647b8e;
  white-space: nowrap;
}

.meta {
  display: flex;
  gap: 18px;
  font-size: 12px;
  color: #9aabb8;
}
</style>
