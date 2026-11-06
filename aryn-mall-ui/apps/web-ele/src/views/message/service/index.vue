<script setup lang="ts">
import { computed } from 'vue';

import { ElButton, ElMessage } from 'element-plus';

import { useAgentPresenceStore } from '#/store/agent-presence';
import { useMessageStore } from '#/store/message';
import {
  focusServiceDeskWindow,
  reclaimServiceDeskWindow,
  syncServiceDeskLiveness,
  useServiceDeskState,
} from '#/store/service-window';

import ServiceDesk from './service-desk.vue';

/**
 * 客服工作台（嵌入主窗口内容区）。
 *
 * <p>后端菜单 `component` 仍指向本文件，因此这里保留为薄壳：真正的工作台实现
 * 在 `service-desk.vue`，独立窗口壳（`views/message/service-window.vue`）复用同一实现。</p>
 *
 * <p>独立窗口打开后，主窗口的嵌入工作台切换为「占位态」（只保留聚焦/收回入口）：
 * 同一坐席不应在两个窗口同时操作同一批会话，否则会出现双份草稿、双份未读/已读
 * 入口与重复操作按钮；主窗口其它业务页面不受影响，仍可继续办公。</p>
 */
const messageStore = useMessageStore();
const presenceStore = useAgentPresenceStore();
const { deskAlive } = useServiceDeskState();

const presenceLabel = computed(() => {
  const labels: Record<string, string> = {
    BUSY: '忙碌',
    OFFLINE: '离线',
    ONLINE: '在线',
    PAUSED: '暂停',
  };
  return labels[presenceStore.presence] ?? '离线';
});

const presenceTone = computed(() => {
  if (presenceStore.presence === 'ONLINE') return 'online';
  if (presenceStore.presence === 'OFFLINE') return 'offline';
  return 'busy';
});

function focusDesk() {
  if (focusServiceDeskWindow()) return;
  // 独立窗口已经不可用：立即重新判定存活，让占位界面尽快恢复成可用工作台
  syncServiceDeskLiveness();
  ElMessage.warning('独立窗口已关闭，工作台即将恢复');
}

function reclaimDesk() {
  reclaimServiceDeskWindow();
  ElMessage.success('已收回工作台');
}
</script>

<template>
  <ServiceDesk v-if="!deskAlive" mode="embedded" />
  <div v-else class="desk-placeholder">
    <div class="placeholder-card">
      <span class="placeholder-dot" :class="presenceTone"></span>
      <h2>客服工作台已在独立窗口运行</h2>
      <p class="placeholder-desc">
        为避免同一坐席在两处重复操作，主窗口工作台暂时收起；主窗口的其它页面仍可正常使用。
      </p>
      <dl class="placeholder-meta">
        <div>
          <dt>接待状态</dt>
          <dd>{{ presenceLabel }}</dd>
        </div>
        <div>
          <dt>未读会话</dt>
          <dd>{{ messageStore.conversationUnread }}</dd>
        </div>
      </dl>
      <div class="placeholder-actions">
        <ElButton type="primary" @click="focusDesk">聚焦独立窗口</ElButton>
        <ElButton class="ghost-action" @click="reclaimDesk">
          收回工作台
        </ElButton>
      </div>
      <p class="placeholder-hint">
        收回后即可在本窗口继续接待；独立窗口关闭时本页面会自动恢复。
      </p>
    </div>
  </div>
</template>

<style scoped>
.desk-placeholder {
  /* 占位态不在 .service-desk 作用域内：补齐 Element Plus 令牌，按钮/标签跟随工作台深色 */
  --el-bg-color: var(--desk-surface);
  --el-bg-color-overlay: var(--desk-surface);
  --el-text-color-primary: var(--desk-text);
  --el-text-color-regular: var(--desk-muted);
  --el-border-color: var(--desk-line);
  --el-fill-color-blank: var(--desk-surface);
  --el-color-primary: var(--desk-accent);
  --el-color-primary-light-3: var(--desk-accent-strong);
  --el-color-primary-light-5: rgb(45 212 191 / 55%);
  --el-color-primary-light-7: rgb(45 212 191 / 35%);
  --el-color-primary-light-8: rgb(45 212 191 / 22%);
  --el-color-primary-light-9: var(--desk-accent-soft);

  display: grid;
  place-items: center;
  height: calc(100vh - 112px);
  min-height: 480px;
  padding: 24px;
  color: var(--desk-text);
  background: var(--desk-bg);
  border: 1px solid var(--desk-line);
  border-radius: 16px;
  box-shadow: var(--desk-shadow);
}

.placeholder-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  width: min(420px, 100%);
  padding: 32px 28px;
  text-align: center;
  background: var(--desk-surface);
  border: 1px solid var(--desk-line);
  border-radius: 16px;
}

.placeholder-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  box-shadow: 0 0 0 6px var(--desk-accent-soft);
}

.placeholder-dot.online {
  background: #34d399;
}

.placeholder-dot.busy {
  background: var(--desk-warning);
}

.placeholder-dot.offline {
  background: var(--desk-faint);
  box-shadow: none;
}

.placeholder-card h2 {
  margin: 6px 0 0;
  font-size: 17px;
  font-weight: 600;
  color: var(--desk-text);
}

.placeholder-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--desk-muted);
}

.placeholder-meta {
  display: flex;
  gap: 28px;
  justify-content: center;
  margin: 8px 0 2px;
}

.placeholder-meta div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.placeholder-meta dt {
  font-size: 11px;
  color: var(--desk-dim);
}

.placeholder-meta dd {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--desk-accent-strong);
}

.placeholder-actions {
  display: flex;
  gap: 10px;
  margin-top: 6px;
}

.placeholder-actions .el-button--primary {
  --el-button-text-color: #04201d;
  --el-button-hover-text-color: #04201d;
  --el-button-active-text-color: #04201d;
}

.ghost-action {
  color: var(--desk-accent-strong);
  background: var(--desk-accent-soft);
  border-color: var(--desk-accent-line);
}

.ghost-action:hover {
  color: var(--desk-accent-contrast);
  background: var(--desk-accent-soft-hover);
  border-color: var(--desk-accent-line-hover);
}

.placeholder-hint {
  margin: 4px 0 0;
  font-size: 11px;
  line-height: 1.6;
  color: var(--desk-dim);
}
</style>
