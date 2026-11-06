<script setup lang="ts">
import type { AgentCandidate } from '#/api/message/agent';
import type { AgentPresenceStatus, AgentRosterItem } from '#/api/message/types';

import { computed, ref } from 'vue';

import { useAccess } from '@vben/access';

import { ElButton, ElDialog, ElEmpty, ElInput, ElTag } from 'element-plus';

import { getAgentCandidates, getAgentRoster } from '#/api/message/agent';

/**
 * 转交 / 强制分配 / 主动联系共用的「可搜索人员选择器」。
 *
 * <p>原实现是 `ElMessageBox.prompt` 手输 staffId：坐席要先去别处查 ID，输错只能
 * 吃一个后端错误。P2-A 起优先读 `GET /message/staff/agent/roster`（含在线状态与
 * 负载），roster 失败/为空时回退 `GET /message/staff/agent/candidates`（原逻辑）；
 * 两份数据按 staffId 合并去重，保留对 roster 外合格人员的搜索能力。</p>
 *
 * <p>普通转交只允许选择「在线且可接」的坐席（离线/忙碌/满员给出不可选原因）；
 * 主管强制分配（`mode='assign'`）允许选择离线/忙碌/满员坐席，后端不校验在线，
 * 前端只负责展示状态与负载。禁用坐席两种模式都不可选。</p>
 */
export interface TransferDialogOptions {
  confirmText?: string;
  hint?: string;
  /**
   * 选择模式：
   * - `transfer`（缺省）：只允许可接坐席，离线/禁用给出提示；
   * - `assign`：强制分配，允许选离线/忙碌/满员坐席，仍展示状态与负载。
   */
  mode?: 'assign' | 'transfer';
  title?: string;
}

/** 弹窗内统一行模型：roster 为主，candidates 兜底合并（按 staffId 去重）。 */
interface TransferOption {
  available?: boolean;
  avatar?: string;
  currentActiveCount?: number;
  disabledReason?: string;
  enabled?: '0' | '1';
  fromRoster: boolean;
  id: string;
  maxActiveCount?: number;
  name: string;
  presenceStatus?: AgentPresenceStatus;
  selectable: boolean;
}

/** 合并阶段尚未判定可选性，由 `decorateSelection` 补齐。 */
type TransferOptionInput = Omit<TransferOption, 'selectable'>;

const emit = defineEmits<{ confirm: [staffId: string] }>();

const PRESENCE_META: Record<
  AgentPresenceStatus,
  { label: string; tone: string }
> = {
  BUSY: { label: '忙碌', tone: 'busy' },
  OFFLINE: { label: '离线', tone: 'offline' },
  ONLINE: { label: '在线', tone: 'online' },
  PAUSED: { label: '暂停接待', tone: 'paused' },
};

const { hasAccessByCodes } = useAccess();

const visible = ref(false);
const loading = ref(false);
const failed = ref(false);
const keyword = ref('');
const selectedId = ref('');
const roster = ref<AgentRosterItem[]>([]);
const candidates = ref<AgentCandidate[]>([]);
const fallbackHint = ref('');
const options = ref<TransferDialogOptions>({});

const isAssignMode = computed(() => options.value.mode === 'assign');
/** candidates 接口需要 `message:service:agent`；只有 supervisor 权限时不要请求，避免无谓 403 提示。 */
const canReadCandidates = computed(() =>
  hasAccessByCodes(['message:service:agent']),
);

const transferOptions = computed<TransferOption[]>(() => {
  const merged = new Map<string, TransferOptionInput>();
  for (const item of roster.value) {
    merged.set(item.staffId, {
      available: item.available,
      avatar: item.staffAvatar,
      currentActiveCount: item.currentActiveCount,
      enabled: item.enabled,
      fromRoster: true,
      id: item.staffId,
      maxActiveCount: item.maxActiveCount,
      name: item.staffName || item.staffId,
      presenceStatus: item.presenceStatus,
    });
  }
  for (const candidate of candidates.value) {
    if (merged.has(candidate.id)) continue;
    merged.set(candidate.id, {
      avatar: candidate.avatar,
      fromRoster: false,
      id: candidate.id,
      name: candidate.nickname || candidate.id,
    });
  }
  return [...merged.values()]
    .map((option) => decorateSelection(option))
    .sort(compareTransferOptions);
});

const filtered = computed(() => {
  const keywordValue = keyword.value.trim().toLowerCase();
  const rows = transferOptions.value;
  if (!keywordValue) return rows;
  return rows.filter((option) => {
    return (
      option.name.toLowerCase().includes(keywordValue) ||
      option.id.toLowerCase().includes(keywordValue)
    );
  });
});

const selected = computed(() =>
  transferOptions.value.find(
    (option) => option.id === selectedId.value && option.selectable,
  ),
);

/** 补「是否可选 + 不可选原因」：普通转交只允许可接，强制分配允许离线/忙碌/满员。 */
function decorateSelection(option: TransferOptionInput): TransferOption {
  if (!option.fromRoster) {
    // candidates 兜底没有 presence 快照：保留原行为可选，后端做最终校验。
    return { ...option, selectable: true };
  }
  if (option.enabled === '0') {
    return { ...option, disabledReason: '坐席已禁用', selectable: false };
  }
  if (isAssignMode.value || option.available) {
    return { ...option, selectable: true };
  }
  return {
    ...option,
    disabledReason: unavailableReason(option),
    selectable: false,
  };
}

function unavailableReason(option: TransferOptionInput) {
  if (option.presenceStatus === 'OFFLINE') return '离线，需主管强制分配';
  if (option.presenceStatus === 'BUSY') return '忙碌中，暂不可接';
  if (option.presenceStatus === 'PAUSED') return '已暂停接待';
  if (
    typeof option.currentActiveCount === 'number' &&
    typeof option.maxActiveCount === 'number' &&
    option.currentActiveCount >= option.maxActiveCount
  ) {
    return '已达接待上限';
  }
  return '当前不可接新会话';
}

/**
 * 排序（契约 §4.2-6 的本地稳定镜像）：可接置顶 → 在线但不可接 → 状态未知
 * （candidates 兜底）→ 离线/禁用；同档按在办数升序、staffId 升序。
 */
function compareTransferOptions(a: TransferOption, b: TransferOption) {
  const rankDiff = optionRank(a) - optionRank(b);
  if (rankDiff !== 0) return rankDiff;
  const loadDiff =
    (a.currentActiveCount ?? Number.MAX_SAFE_INTEGER) -
    (b.currentActiveCount ?? Number.MAX_SAFE_INTEGER);
  if (loadDiff !== 0) return loadDiff;
  return a.id.localeCompare(b.id);
}

function optionRank(option: TransferOption) {
  if (option.available) return 0;
  if (
    option.fromRoster &&
    option.presenceStatus &&
    option.presenceStatus !== 'OFFLINE'
  ) {
    return 1;
  }
  if (!option.fromRoster) return 2;
  return 3;
}

function presenceLabel(option: TransferOption) {
  if (!option.fromRoster) return '状态未知';
  return option.presenceStatus
    ? PRESENCE_META[option.presenceStatus].label
    : '状态未知';
}

function presenceTone(option: TransferOption) {
  if (!option.fromRoster) return 'unknown';
  return option.presenceStatus
    ? PRESENCE_META[option.presenceStatus].tone
    : 'unknown';
}

function loadLabel(option: TransferOption) {
  if (
    !option.fromRoster ||
    typeof option.currentActiveCount !== 'number' ||
    typeof option.maxActiveCount !== 'number'
  ) {
    return '';
  }
  return `在办 ${option.currentActiveCount}/${option.maxActiveCount}`;
}

async function fetchCandidates() {
  const page = await getAgentCandidates();
  return page.records ?? [];
}

/**
 * 拉取选人数据：roster 优先；失败/为空时回退 candidates（原逻辑）。
 * 有 agent 权限时即使 roster 正常也合并 candidates，保留对 roster 外人员的搜索。
 */
async function loadOptions() {
  loading.value = true;
  failed.value = false;
  fallbackHint.value = '';
  roster.value = [];
  candidates.value = [];

  let rosterFailed = false;
  try {
    roster.value = (await getAgentRoster()) ?? [];
  } catch {
    // 403/网络错误由请求层提示，这里只负责切回 candidates 兜底
    rosterFailed = true;
    roster.value = [];
  }

  // candidates 接口需要 message:service:agent 权限；只有 supervisor 权限时不请求，
  // 避免必然 403 的全局错误提示。roster 正常时合并 candidates，保留搜索全量人员的能力。
  if (canReadCandidates.value) {
    try {
      candidates.value = await fetchCandidates();
    } catch {
      candidates.value = [];
      // 两个数据源都失败才进入错误态；roster 正常时 candidates 失败不影响使用
      if (rosterFailed) failed.value = true;
    }
  } else if (rosterFailed) {
    failed.value = true;
  }

  if (!failed.value) {
    if (rosterFailed) {
      fallbackHint.value =
        candidates.value.length > 0
          ? '在线坐席名单加载失败，已回退到具备客服资格人员列表（不含在线状态）。'
          : '在线坐席名单加载失败，请重试。';
    } else if (roster.value.length === 0) {
      fallbackHint.value =
        candidates.value.length > 0
          ? '暂无已配置坐席，已回退到具备客服资格人员列表（不含在线状态）。'
          : '暂无已配置坐席。';
    }
  }

  loading.value = false;
}

/** 打开选择器；每次打开重新拉取，避免坐席在线状态/负载过期。 */
async function open(nextOptions: TransferDialogOptions = {}) {
  options.value = { ...nextOptions };
  visible.value = true;
  keyword.value = '';
  selectedId.value = '';
  await loadOptions();
}

function select(optionId: string) {
  const option = transferOptions.value.find((item) => item.id === optionId);
  if (!option?.selectable) return;
  selectedId.value = optionId;
}

function confirm() {
  const target = selected.value;
  if (!target) return;
  emit('confirm', target.id);
  visible.value = false;
}

defineExpose({ open });
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="options.title || '选择客服'"
    width="540px"
    append-to-body
    destroy-on-close
  >
    <div class="transfer-toolbar">
      <ElInput
        v-model="keyword"
        clearable
        placeholder="搜索客服昵称或工作人员 ID"
      />
      <ElTag v-if="isAssignMode" size="small" type="warning" effect="plain">
        强制分配：可选离线坐席
      </ElTag>
    </div>
    <p v-if="fallbackHint" class="transfer-fallback">{{ fallbackHint }}</p>
    <div v-loading="loading" class="transfer-list">
      <div v-if="failed" class="transfer-error">
        <p>客服列表加载失败，请检查权限或网络后重试</p>
        <ElButton size="small" @click="loadOptions">重新加载</ElButton>
      </div>
      <ElEmpty
        v-else-if="!loading && filtered.length === 0"
        :description="keyword.trim() ? '没有匹配的客服' : '暂无可选客服'"
        :image-size="60"
      />
      <button
        v-for="option in filtered"
        :key="option.id"
        type="button"
        class="transfer-item"
        :class="{
          active: selectedId === option.id,
          disabled: !option.selectable,
        }"
        :aria-disabled="!option.selectable"
        :aria-pressed="selectedId === option.id"
        :disabled="!option.selectable"
        @click="select(option.id)"
      >
        <span class="transfer-avatar">
          <img v-if="option.avatar" :src="option.avatar" alt="" />
          <template v-else>{{ (option.name || '客').slice(0, 1) }}</template>
        </span>
        <span class="transfer-copy">
          <span class="transfer-name-row">
            <strong>{{ option.name }}</strong>
            <span
              class="transfer-presence"
              :class="presenceTone(option)"
              :title="`在线状态：${presenceLabel(option)}`"
            >
              {{ presenceLabel(option) }}
            </span>
            <ElTag
              v-if="option.fromRoster && option.available"
              size="small"
              type="success"
              effect="plain"
            >
              可接
            </ElTag>
          </span>
          <small>
            {{ option.id }}
            <span v-if="loadLabel(option)"> · {{ loadLabel(option) }}</span>
          </small>
        </span>
        <span v-if="option.disabledReason" class="transfer-reason">
          {{ option.disabledReason }}
        </span>
        <ElTag v-else-if="selectedId === option.id" size="small" type="success">
          已选
        </ElTag>
      </button>
    </div>
    <p v-if="options.hint" class="transfer-hint">{{ options.hint }}</p>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" :disabled="!selected" @click="confirm">
        {{ options.confirmText || '确认' }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.transfer-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}

.transfer-toolbar :deep(.el-input) {
  flex: 1;
}

.transfer-toolbar :deep(.el-tag) {
  flex: none;
}

.transfer-fallback {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--desk-muted);
}

.transfer-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 340px;
  margin-top: 12px;
  overflow: auto;
}

.transfer-error {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  padding: 24px 0;
  text-align: center;
}

.transfer-error p {
  font-size: 12px;
  color: var(--desk-warning);
}

.transfer-item {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
  padding: 9px 10px;
  text-align: left;
  border-radius: 10px;
}

.transfer-item:not(:disabled):hover {
  background: var(--desk-hover);
}

.transfer-item.active {
  background: var(--desk-accent-soft);
  box-shadow: inset 0 0 0 1px var(--desk-accent-line);
}

.transfer-item:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.transfer-avatar {
  display: grid;
  flex: none;
  place-items: center;
  width: 32px;
  height: 32px;
  overflow: hidden;
  font-size: 13px;
  color: var(--desk-accent-strong);
  background: var(--desk-avatar-bg);
  border: 1px solid var(--desk-avatar-line);
  border-radius: 9px;
}

.transfer-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.transfer-copy {
  flex: 1;
  min-width: 0;
}

.transfer-name-row {
  display: flex;
  gap: 6px;
  align-items: center;
  min-width: 0;
}

.transfer-copy strong {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  color: var(--desk-text);
  white-space: nowrap;
}

.transfer-copy small {
  display: block;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  color: var(--desk-dim);
  white-space: nowrap;
}

.transfer-presence {
  flex: none;
  padding: 0 6px;
  font-size: 11px;
  line-height: 18px;
  color: var(--desk-muted);
  white-space: nowrap;
  border: 1px solid var(--desk-line);
  border-radius: 999px;
}

.transfer-presence.online {
  color: var(--desk-accent-strong);
  background: var(--desk-accent-soft);
  border-color: var(--desk-accent-line);
}

.transfer-presence.busy {
  color: var(--desk-warning);
  background: var(--desk-warning-soft);
  border-color: var(--desk-warning-line);
}

.transfer-presence.paused {
  color: var(--desk-segment-text);
  border-color: var(--desk-line-soft);
}

.transfer-presence.offline {
  color: var(--desk-faint);
  border-color: var(--desk-line-soft);
}

.transfer-presence.unknown {
  color: var(--desk-faint);
  border-style: dashed;
}

.transfer-reason {
  flex: none;
  max-width: 132px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--desk-faint);
  text-align: right;
}

.transfer-hint {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--desk-muted);
}
</style>
