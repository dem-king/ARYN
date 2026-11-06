<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { useAccessStore } from '@vben/stores';

import { ElButton, ElMessage, ElTag } from 'element-plus';

import SessionInvalidModal from '#/layouts/session-invalid-modal.vue';
import { useAgentPresenceStore } from '#/store/agent-presence';
import { useMessageStore } from '#/store/message';
import {
  SERVICE_DESK_WINDOW_NAME,
  startDeskServiceDeskRole,
  subscribeServiceDesk,
} from '#/store/service-window';

import ServiceDesk from './service/service-desk.vue';

import './service/desk-tokens.css';

/**
 * 客服工作台「独立窗口」宿主。
 *
 * <p>本页由 `noBasicLayout` 路由挂载，是独立浏览器窗口的入口：没有管理端
 * 侧边栏/顶栏/tabbar，只有一条工作台顶栏 + 全屏工作台。</p>
 *
 * 职责：
 * 1. 启动实时通道（`messageStore.start()`）与坐席心跳；
 * 2. 启动跨窗协同（握手/心跳/租约/重复窗口检测），消费 locate/reclaim/logout/lock/tenant；
 * 3. 挂载会话失效弹窗（`sessionInvalidModal` meta 让 401 走页内原地重登）；
 * 4. 处理锁屏遮罩、登出关闭、租户切换重载。
 */
const router = useRouter();
const accessStore = useAccessStore();
const messageStore = useMessageStore();
const presenceStore = useAgentPresenceStore();

const locked = ref(accessStore.isLockScreen);
const duplicate = ref(false);
const presenceLabel = computed(() => {
  const labels: Record<string, string> = {
    BUSY: '忙碌',
    OFFLINE: '离线',
    ONLINE: '在线',
    PAUSED: '暂停',
  };
  return labels[presenceStore.presence] ?? '离线';
});
const presenceTone = computed<'info' | 'success' | 'warning'>(() => {
  if (presenceStore.presence === 'ONLINE') return 'success';
  if (presenceStore.presence === 'OFFLINE') return 'info';
  return 'warning';
});

let stopCleanup: (() => void) | undefined;
let stopSync: (() => void) | undefined;

/** 收回主窗口：聚焦 opener 后自关；手动输入 URL 打开的窗口无法自关，降级为嵌入页。 */
function reclaimToMain() {
  try {
    window.opener?.focus();
  } catch {
    // opener 可能不可用（浏览器策略/手动打开），忽略
  }
  window.close();
  // 非脚本打开的窗口 window.close() 会静默失败：降级为在本窗口内打开嵌入工作台
  window.setTimeout(() => {
    if (!window.closed) {
      ElMessage.info('当前窗口无法自动关闭，已切换为页面内工作台');
      void router.replace('/message/service');
    }
  }, 400);
}

function handleLogout() {
  window.close();
  window.setTimeout(() => {
    if (!window.closed) {
      void router.replace(LOGIN_PATH);
    }
  }, 300);
}

function handleLock(lockedValue: boolean) {
  locked.value = lockedValue;
}

function handleTenantChange() {
  window.location.reload();
}

onMounted(() => {
  // 独立窗口没有 BasicLayout：实时通道与音频解锁由 start() 统一挂载（幂等）。
  messageStore.start();
  presenceStore.activate();
  if (typeof window !== 'undefined') {
    window.name = SERVICE_DESK_WINDOW_NAME;
  }
  // 重复打开时（浏览器竞态/双开按钮）自行关闭，避免同一坐席两个工作台互相打扰
  stopCleanup = startDeskServiceDeskRole({
    onDuplicate: () => {
      duplicate.value = true;
      window.close();
      window.setTimeout(() => {
        if (!window.closed) duplicate.value = false;
      }, 600);
    },
  });
  stopSync = subscribeServiceDesk((message) => {
    if (message.type === 'reclaim') {
      reclaimToMain();
      return;
    }
    if (message.type === 'logout') {
      handleLogout();
      return;
    }
    if (message.type === 'lock') {
      handleLock(message.locked === true);
      return;
    }
    if (message.type === 'tenant') {
      handleTenantChange();
      return;
    }
    if (message.type === 'focus') {
      try {
        window.focus();
      } catch {
        // 浏览器可能忽略后台窗口 focus，静默降级
      }
    }
  });
  // 主窗口锁屏/切租户时，本窗口可能未收到广播（例如主窗口直接刷新）：
  // 监听共享 localStorage 里的租户切换键，保持双窗租户一致。
  window.addEventListener('storage', handleStorageChange);
});

function handleStorageChange(event: StorageEvent) {
  if (event.key === 'switch-tenant-id') {
    window.location.reload();
  }
}

// 任务栏/标题显示未读数，坐席切到其它应用也能看到「有几个会话在等」
watch(
  () => messageStore.totalUnread,
  (total) => {
    document.title =
      total > 0 ? `(${total}) 客服工作台 · 悦航购` : '客服工作台 · 悦航购';
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  stopSync?.();
  stopCleanup?.();
  window.removeEventListener('storage', handleStorageChange);
  document.title = '悦航购';
});
</script>

<template>
  <div class="service-window-shell">
    <header class="desk-window-topbar">
      <div class="desk-window-brand">
        <span class="desk-window-dot"></span>
        <strong>客服工作台</strong>
        <ElTag :type="presenceTone" effect="dark" round size="small">
          {{ presenceLabel }}
        </ElTag>
      </div>
      <div class="desk-window-actions">
        <span class="desk-window-unread">
          未读会话
          <strong>{{ messageStore.conversationUnread }}</strong>
        </span>
        <ElButton
          size="small"
          class="desk-window-reclaim"
          @click="reclaimToMain"
        >
          收回主窗口
        </ElButton>
      </div>
    </header>
    <main class="desk-window-body">
      <ServiceDesk mode="window" />
    </main>
    <div v-if="locked" class="desk-window-lock">
      <strong>工作台已锁定</strong>
      <p>主窗口已进入锁屏状态，请先在主窗口解锁后再继续接待。</p>
    </div>
    <div v-if="duplicate" class="desk-window-lock">
      <strong>已在另一个独立窗口打开</strong>
      <p>请使用已打开的工作台窗口。</p>
    </div>
    <SessionInvalidModal />
  </div>
</template>

<style scoped>
.service-window-shell {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  color: var(--desk-text);
  background: var(--desk-bg);
}

.desk-window-topbar {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: space-between;
  height: 52px;
  padding: 0 16px;
  background: var(--desk-surface);
  border-bottom: 1px solid var(--desk-line);
}

.desk-window-brand {
  display: flex;
  gap: 10px;
  align-items: center;
  font-size: 14px;
}

.desk-window-dot {
  width: 9px;
  height: 9px;
  background: var(--desk-accent);
  border-radius: 50%;
  box-shadow: 0 0 12px var(--desk-accent);
}

.desk-window-actions {
  display: flex;
  gap: 14px;
  align-items: center;
}

.desk-window-unread {
  font-size: 12px;
  color: var(--desk-muted);
}

.desk-window-unread strong {
  margin-left: 4px;
  font-size: 14px;
  color: var(--desk-accent-strong);
}

.desk-window-reclaim {
  color: var(--desk-accent-strong);
  background: var(--desk-accent-soft);
  border-color: var(--desk-accent-line);
}

.desk-window-reclaim:hover {
  color: var(--desk-accent-contrast);
  background: var(--desk-accent-soft-hover);
  border-color: var(--desk-accent-line-hover);
}

.desk-window-body {
  flex: 1;
  min-height: 0;
}

.desk-window-lock {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: grid;
  gap: 8px;
  place-content: center;
  padding: 24px;
  text-align: center;
  background: rgb(4 12 18 / 88%);
}

.desk-window-lock strong {
  font-size: 18px;
  color: var(--desk-text);
}

.desk-window-lock p {
  font-size: 13px;
  color: var(--desk-muted);
}
</style>
