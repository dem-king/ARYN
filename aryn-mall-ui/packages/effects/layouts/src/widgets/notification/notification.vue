<script lang="ts" setup>
import type { Component } from 'vue';

import type { NoticeType } from '@vben/utils';

import type { NotificationItem } from './types';

import { computed, ref } from 'vue';

import {
  Bell,
  ChevronRight,
  CircleX,
  Info,
  MailCheck,
  MessageSquare,
  RotateCw,
  Settings,
  Ship,
  ShoppingCart,
  Truck,
  Wallet,
} from '@vben/icons';
import { $t } from '@vben/locales';
import {
  formatNoticeDateTime,
  formatNoticeTime,
  noticeCategoryOf,
  noticeTypeOf,
} from '@vben/utils';

import {
  Switch,
  VbenButton,
  VbenIconButton,
  VbenPopover,
  VbenScrollbar,
} from '@vben-core/shadcn-ui';

import { useToggle } from '@vueuse/core';

interface Props {
  /**
   * 桌面通知是否开启（未传则不渲染开关）
   */
  desktopEnabled?: boolean;
  /**
   * 显示圆点
   */
  dot?: boolean;
  /**
   * 消息列表
   */
  notifications?: NotificationItem[];
  /**
   * 声音提醒是否开启（未传则不渲染开关）
   */
  soundEnabled?: boolean;
  /**
   * 未读总数。大于 0 时用数字角标替代圆点，便于客服这类需要知道条数的提醒。
   */
  total?: number;
  /**
   * 语音播报是否开启（未传则不渲染开关）
   */
  voiceEnabled?: boolean;
}

defineOptions({ name: 'NotificationPopup' });

const props = withDefaults(defineProps<Props>(), {
  desktopEnabled: undefined,
  dot: false,
  notifications: () => [],
  soundEnabled: undefined,
  total: 0,
  voiceEnabled: undefined,
});

const emit = defineEmits<{
  makeAll: [];
  read: [NotificationItem];
  toggleDesktop: [boolean];
  toggleSound: [boolean];
  toggleVoice: [boolean];
  viewAll: [];
}>();

type FilterKey = 'all' | 'order' | 'refund' | 'service' | 'system' | 'unread';

/**
 * 消息类型元信息：图标 + 色板 + 文案按「新订单 / 已取消 / 退款审核 / 待收款 /
 * 绑定申请 / 配送任务 / 客服 / 系统」区分，同一分类下也能一眼分辨。
 * 颜色只做辅助，类型标签文字保证色盲用户同样可读。
 */
const NOTICE_TYPE_META: Record<
  NoticeType,
  { chip: string; icon: Component; labelKey: string; tile: string }
> = {
  'cod-pay-remind': {
    chip: 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300',
    icon: Wallet,
    labelKey: 'ui.widgets.typeCodPayRemind',
    tile: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-300',
  },
  'delivery-task': {
    chip: 'bg-violet-500/10 text-violet-700 dark:text-violet-300',
    icon: Truck,
    labelKey: 'ui.widgets.typeDeliveryTask',
    tile: 'bg-violet-500/10 text-violet-600 dark:text-violet-300',
  },
  'order-cancelled': {
    chip: 'bg-rose-500/10 text-rose-700 dark:text-rose-300',
    icon: CircleX,
    labelKey: 'ui.widgets.typeOrderCancelled',
    tile: 'bg-rose-500/10 text-rose-600 dark:text-rose-300',
  },
  'order-created': {
    chip: 'bg-blue-500/10 text-blue-700 dark:text-blue-300',
    icon: ShoppingCart,
    labelKey: 'ui.widgets.typeOrderCreated',
    tile: 'bg-blue-500/10 text-blue-600 dark:text-blue-300',
  },
  'order-refund': {
    chip: 'bg-amber-500/10 text-amber-700 dark:text-amber-300',
    icon: RotateCw,
    labelKey: 'ui.widgets.typeOrderRefund',
    tile: 'bg-amber-500/10 text-amber-600 dark:text-amber-300',
  },
  'vessel-bind': {
    chip: 'bg-cyan-500/10 text-cyan-700 dark:text-cyan-300',
    icon: Ship,
    labelKey: 'ui.widgets.typeVesselBind',
    tile: 'bg-cyan-500/10 text-cyan-600 dark:text-cyan-300',
  },
  conversation: {
    chip: 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300',
    icon: MessageSquare,
    labelKey: 'ui.widgets.typeConversation',
    tile: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-300',
  },
  system: {
    chip: 'bg-muted text-muted-foreground',
    icon: Info,
    labelKey: 'ui.widgets.typeSystem',
    tile: 'bg-muted text-muted-foreground',
  },
};

const [open, toggle] = useToggle();
const activeFilter = ref<FilterKey>('all');
const settingsOpen = ref(false);

/** 有具体条数时用数字角标，没有条数才回落到圆点，两者不叠加。 */
const badge = computed(() => (props.total > 99 ? '99+' : String(props.total)));

/** 本地可点掉的未读（含待领取会话），未读页签按这个口径展示。 */
const unreadLocal = computed(
  () => props.notifications.filter((item) => isUnread(item)).length,
);

/** 面板头部未读数：优先用后端未读总数（含客服会话），没有则用列表本地统计。 */
const unreadBadge = computed(() =>
  props.total > 0 ? props.total : unreadLocal.value,
);

/** 只有传了对应开关的页面才显示（消息提醒是本应用层的产品能力，不污染通用组件默认外观）。 */
const showAlerts = computed(
  () =>
    props.soundEnabled !== undefined ||
    props.voiceEnabled !== undefined ||
    props.desktopEnabled !== undefined,
);

/** “全部已读”只作用于站内信；客服会话未读由工作台处理，不能在这里假装读掉。 */
const hasUnreadNotice = computed(() =>
  props.notifications.some(
    (item) => item.kind !== 'conversation' && item.isRead !== true,
  ),
);

const filters = computed<Array<{ key: FilterKey; label: string }>>(() => {
  const tabs: Array<{ key: FilterKey; label: string }> = [
    { key: 'all', label: $t('ui.widgets.filterAll') },
    { key: 'unread', label: $t('ui.widgets.filterUnread') },
    { key: 'order', label: $t('ui.widgets.filterOrder') },
    { key: 'refund', label: $t('ui.widgets.filterRefund') },
  ];
  const hasConversation = props.notifications.some(
    (item) => categoryOf(item) === 'conversation',
  );
  if (hasConversation) {
    tabs.push({ key: 'service', label: $t('ui.widgets.filterService') });
  }
  tabs.push({ key: 'system', label: $t('ui.widgets.filterSystem') });
  return tabs;
});

const visibleItems = computed(() =>
  props.notifications.filter((item) => matchFilter(item)),
);

const triggerLabel = computed(() =>
  unreadBadge.value > 0
    ? $t('ui.widgets.notificationsWithUnread', { count: unreadBadge.value })
    : $t('ui.widgets.notificationCenter'),
);

const recentLabel = computed(() =>
  $t('ui.widgets.recentMessages', { count: props.notifications.length }),
);

function categoryOf(item: NotificationItem) {
  return noticeCategoryOf(item);
}

function typeOf(item: NotificationItem): NoticeType {
  return noticeTypeOf(item);
}

function typeMetaOf(item: NotificationItem) {
  return NOTICE_TYPE_META[typeOf(item)];
}

function isUnread(item: NotificationItem) {
  return item.isRead !== true || Boolean(item.tag);
}

function isHighPriority(item: NotificationItem) {
  return item.priority === 'HIGH';
}

function matchFilter(item: NotificationItem) {
  switch (activeFilter.value) {
    case 'all': {
      return true;
    }
    case 'unread': {
      return isUnread(item);
    }
    default: {
      return categoryOf(item) === activeFilter.value;
    }
  }
}

function itemTime(item: NotificationItem) {
  return formatNoticeTime(item.date);
}

/** 列表 key：会话 ID / 站内信 ID / 标题+时间，三者必有其一，避免重复 key 抖动。 */
function itemKey(item: NotificationItem) {
  return item.conversationId || item.messageId || `${item.title}-${item.date}`;
}

function itemFullTime(item: NotificationItem) {
  return formatNoticeDateTime(item.date);
}

function close() {
  open.value = false;
}

function handleViewAll() {
  emit('viewAll');
  close();
}

function handleMakeAll() {
  emit('makeAll');
}

/** 点击一条提醒：先标记已读（父层负责），有业务路由时同时关闭弹层去跳转。 */
function handleSelect(item: NotificationItem) {
  emit('read', item);
  if (item.link) {
    close();
  }
}
</script>
<template>
  <VbenPopover
    v-model:open="open"
    content-class="relative right-2 w-[calc(100vw-16px)] overflow-hidden rounded-xl p-0 shadow-xl sm:w-[420px]"
  >
    <template #trigger>
      <div class="flex-center mr-2 h-full" @click.stop="toggle()">
        <VbenIconButton
          class="bell-button text-foreground relative"
          :aria-label="triggerLabel"
          :tooltip="triggerLabel"
        >
          <!-- 有具体条数时用数字角标，没有条数才回落到圆点，两者不叠加 -->
          <span
            v-if="dot && total <= 0"
            class="bg-primary absolute right-0.5 top-0.5 h-2 w-2 rounded"
          ></span>
          <span
            v-if="total > 0"
            class="bg-destructive text-destructive-foreground ring-background absolute -right-1.5 -top-1 z-10 flex h-4 min-w-4 items-center justify-center rounded-full px-1 text-[10px] font-semibold leading-none ring-2"
          >
            {{ badge }}
          </span>
          <Bell class="size-4" />
        </VbenIconButton>
      </div>
    </template>

    <div class="flex max-h-[min(78vh,640px)] flex-col">
      <!-- 头部：标题 + 未读摘要 + 快捷操作 + 分类页签 -->
      <header class="shrink-0 px-4 pb-2 pt-4">
        <div class="flex items-start justify-between gap-2">
          <div class="min-w-0">
            <div class="flex items-center gap-2">
              <h3 class="text-foreground truncate text-sm font-semibold">
                {{ $t('ui.widgets.notificationCenter') }}
              </h3>
              <span
                v-if="unreadBadge > 0"
                class="bg-destructive/10 text-destructive inline-flex h-5 shrink-0 items-center rounded-full px-2 text-[11px] font-medium"
              >
                {{ $t('ui.widgets.unreadCount', { count: unreadBadge }) }}
              </span>
            </div>
            <p
              class="text-muted-foreground mt-0.5 truncate text-[11px] leading-4"
            >
              {{ $t('ui.widgets.notificationSubtitle') }}
            </p>
          </div>
          <div class="flex shrink-0 items-center gap-0.5">
            <VbenIconButton
              :disabled="!hasUnreadNotice"
              :tooltip="$t('ui.widgets.markAllAsRead')"
              @click="handleMakeAll"
            >
              <MailCheck class="size-4" />
            </VbenIconButton>
            <VbenIconButton
              :tooltip="$t('ui.widgets.alertSettings')"
              @click="settingsOpen = !settingsOpen"
            >
              <Settings
                class="size-4 transition-colors"
                :class="settingsOpen ? 'text-primary' : ''"
              />
            </VbenIconButton>
          </div>
        </div>

        <div
          class="mt-2.5 flex items-center gap-1 overflow-x-auto pb-0.5"
          role="tablist"
          :aria-label="$t('ui.widgets.notificationCenter')"
        >
          <button
            v-for="filter in filters"
            :key="filter.key"
            :aria-selected="activeFilter === filter.key"
            class="focus-visible:ring-primary/40 shrink-0 rounded-full px-2.5 py-1 text-[11.5px] font-medium transition-colors focus-visible:outline-none focus-visible:ring-2"
            role="tab"
            type="button"
            :class="
              activeFilter === filter.key
                ? 'bg-primary text-primary-foreground shadow-sm'
                : 'text-muted-foreground hover:bg-accent hover:text-foreground'
            "
            @click="activeFilter = filter.key"
          >
            {{ filter.label }}
            <span
              v-if="filter.key === 'unread' && unreadLocal > 0"
              class="ml-1 inline-flex min-w-4 items-center justify-center rounded-full px-1 text-[10px] font-semibold"
              :class="
                activeFilter === filter.key
                  ? 'bg-primary-foreground/20'
                  : 'bg-muted text-muted-foreground'
              "
            >
              {{ unreadLocal > 99 ? '99+' : unreadLocal }}
            </span>
          </button>
        </div>
      </header>

      <!-- 列表：整行可点，未读用左侧色条 + 浅色底 + 圆点三重标记 -->
      <VbenScrollbar v-if="visibleItems.length > 0" class="min-h-0">
        <ul class="max-h-[min(52vh,400px)] w-full">
          <li v-for="item in visibleItems" :key="itemKey(item)">
            <button
              class="border-border/60 focus-visible:bg-accent/60 hover:bg-accent/60 group relative flex w-full cursor-pointer items-start gap-3 border-t px-4 py-3 text-left transition-colors focus-visible:outline-none"
              :class="isUnread(item) ? 'bg-primary/[0.035]' : ''"
              type="button"
              @click="handleSelect(item)"
            >
              <span
                v-if="isUnread(item)"
                class="bg-primary absolute inset-y-2 left-0 w-[3px] rounded-r-full"
              ></span>

              <span
                class="relative flex h-9 w-9 shrink-0 items-center justify-center rounded-xl"
                :class="
                  categoryOf(item) === 'conversation'
                    ? 'bg-emerald-500/10 text-sm font-semibold text-emerald-600 dark:text-emerald-300'
                    : typeMetaOf(item).tile
                "
              >
                <template v-if="categoryOf(item) === 'conversation'">
                  {{ item.avatarText || '客' }}
                </template>
                <component :is="typeMetaOf(item).icon" v-else class="size-4" />
                <span
                  v-if="isUnread(item)"
                  class="bg-primary ring-popover absolute -right-0.5 -top-0.5 h-2 w-2 rounded-full ring-2"
                ></span>
              </span>

              <span class="flex min-w-0 flex-1 flex-col">
                <span class="flex min-w-0 items-center gap-1.5">
                  <span
                    class="text-foreground truncate text-[13px] leading-5"
                    :class="isUnread(item) ? 'font-semibold' : 'font-medium'"
                  >
                    {{ item.title }}
                  </span>
                  <span
                    v-if="item.tag"
                    class="bg-warning/15 text-warning shrink-0 rounded px-1.5 py-0.5 text-[10px] font-medium leading-none"
                  >
                    {{ item.tag }}
                  </span>
                  <span
                    v-if="isHighPriority(item)"
                    class="bg-destructive/10 text-destructive shrink-0 rounded px-1.5 py-0.5 text-[10px] font-medium leading-none"
                  >
                    {{ $t('ui.widgets.priorityHigh') }}
                  </span>
                  <span
                    class="text-muted-foreground ml-auto shrink-0 pl-2 text-[11px] tabular-nums"
                    :title="itemFullTime(item)"
                  >
                    {{ itemTime(item) }}
                  </span>
                </span>

                <span
                  class="text-muted-foreground mt-1 line-clamp-2 text-xs leading-[18px]"
                >
                  {{ item.message }}
                </span>

                <span class="mt-1.5 flex min-w-0 items-center gap-1.5">
                  <span
                    v-if="item.amount"
                    class="inline-flex items-center rounded-md border px-1.5 py-0.5 text-[11px] font-medium tabular-nums"
                    :class="
                      typeOf(item) === 'order-cancelled'
                        ? 'border-rose-500/25 bg-rose-500/[0.06] text-rose-600 dark:text-rose-300'
                        : 'text-foreground/90 border-border/70 bg-background'
                    "
                  >
                    ¥ {{ item.amount }}
                  </span>
                  <span
                    v-if="categoryOf(item) !== 'conversation'"
                    class="inline-flex items-center rounded px-1.5 py-0.5 text-[10px] font-medium leading-none"
                    :class="typeMetaOf(item).chip"
                  >
                    {{ $t(typeMetaOf(item).labelKey) }}
                  </span>
                  <span
                    v-if="item.unreadCount"
                    class="bg-destructive text-destructive-foreground inline-flex h-4 min-w-4 items-center justify-center rounded-full px-1 text-[10px] font-medium leading-none"
                  >
                    {{ item.unreadCount > 99 ? '99+' : item.unreadCount }}
                  </span>
                  <span
                    v-if="item.link"
                    class="text-primary ml-auto inline-flex shrink-0 items-center gap-0.5 text-[11px] font-medium"
                  >
                    {{ $t('ui.widgets.openDetail') }}
                    <ChevronRight class="size-3" />
                  </span>
                </span>
              </span>
            </button>
          </li>
        </ul>
      </VbenScrollbar>

      <!-- 空状态：区分「没有任何消息」和「当前筛选为空」 -->
      <div
        v-else
        class="border-border/60 flex min-h-[196px] flex-col items-center justify-center gap-1.5 border-t px-6 py-8 text-center"
      >
        <span
          class="bg-muted text-muted-foreground mb-1 flex h-11 w-11 items-center justify-center rounded-full"
        >
          <Bell class="size-5" />
        </span>
        <p class="text-foreground text-[13px] font-medium">
          {{
            activeFilter === 'unread'
              ? $t('ui.widgets.noUnreadMessages')
              : $t('ui.widgets.noMessages')
          }}
        </p>
        <p class="text-muted-foreground text-[11px] leading-4">
          {{
            activeFilter === 'unread'
              ? $t('ui.widgets.noUnreadMessagesHint')
              : $t('ui.widgets.noMessagesHint')
          }}
        </p>
      </div>

      <!-- 新消息强提醒开关：只在调用方传入开关值时出现，默认收起避免挤占列表 -->
      <div
        v-if="showAlerts && settingsOpen"
        class="border-border bg-muted/30 shrink-0 border-t px-4 py-3"
      >
        <div class="mb-2 flex items-center justify-between gap-2">
          <span class="text-foreground text-xs font-medium">
            {{ $t('ui.widgets.alertSettings') }}
          </span>
          <span class="text-muted-foreground text-[10px]">
            {{ $t('ui.widgets.alertSettingsHint') }}
          </span>
        </div>
        <div class="flex flex-col gap-2">
          <div
            v-if="soundEnabled !== undefined"
            class="flex items-center justify-between gap-3"
          >
            <div class="min-w-0">
              <p class="text-foreground text-xs leading-4">
                {{ $t('ui.widgets.alertSound') }}
              </p>
              <p class="text-muted-foreground text-[10px] leading-4">
                {{ $t('ui.widgets.alertSoundHint') }}
              </p>
            </div>
            <Switch
              :checked="soundEnabled"
              @update:checked="emit('toggleSound', $event)"
            />
          </div>
          <div
            v-if="voiceEnabled !== undefined"
            class="flex items-center justify-between gap-3"
          >
            <div class="min-w-0">
              <p class="text-foreground text-xs leading-4">
                {{ $t('ui.widgets.alertVoice') }}
              </p>
              <p class="text-muted-foreground text-[10px] leading-4">
                {{ $t('ui.widgets.alertVoiceHint') }}
              </p>
            </div>
            <Switch
              :checked="voiceEnabled"
              @update:checked="emit('toggleVoice', $event)"
            />
          </div>
          <div
            v-if="desktopEnabled !== undefined"
            class="flex items-center justify-between gap-3"
          >
            <div class="min-w-0">
              <p class="text-foreground text-xs leading-4">
                {{ $t('ui.widgets.alertDesktop') }}
              </p>
              <p class="text-muted-foreground text-[10px] leading-4">
                {{ $t('ui.widgets.alertDesktopHint') }}
              </p>
            </div>
            <Switch
              :checked="desktopEnabled"
              @update:checked="emit('toggleDesktop', $event)"
            />
          </div>
        </div>
      </div>

      <!-- 底部：消息规模提示 + 查看全部 -->
      <div class="border-border shrink-0 border-t px-4 py-2.5">
        <div class="flex items-center justify-between gap-2">
          <span class="text-muted-foreground text-[11px]">
            {{ recentLabel }}
          </span>
          <VbenButton size="sm" @click="handleViewAll">
            {{ $t('ui.widgets.viewAll') }}
            <ChevronRight class="ml-0.5 size-3.5" />
          </VbenButton>
        </div>
      </div>
    </div>
  </VbenPopover>
</template>

<style scoped>
@keyframes bell-ring {
  0%,
  100% {
    transform-origin: top;
  }

  15% {
    transform: rotateZ(10deg);
  }

  30% {
    transform: rotateZ(-10deg);
  }

  45% {
    transform: rotateZ(5deg);
  }

  60% {
    transform: rotateZ(-5deg);
  }

  75% {
    transform: rotateZ(2deg);
  }
}

/* 尊重系统「减少动态效果」：铃铛摇摆关掉（!important 压过 hover 动画，避免被后置规则覆盖） */
@media (prefers-reduced-motion: reduce) {
  :deep(.bell-button) svg {
    animation: none !important;
  }
}

:deep(.bell-button) {
  &:hover {
    svg {
      animation: bell-ring 1s both;
    }
  }
}
</style>
