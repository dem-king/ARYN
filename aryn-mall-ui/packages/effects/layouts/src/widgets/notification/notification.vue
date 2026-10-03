<script lang="ts" setup>
import type { NotificationItem } from './types';

import { computed } from 'vue';

import { Bell, MailCheck } from '@vben/icons';
import { $t } from '@vben/locales';

import {
  VbenButton,
  VbenIconButton,
  VbenPopover,
  VbenScrollbar,
} from '@vben-core/shadcn-ui';

import { useToggle } from '@vueuse/core';

interface Props {
  /**
   * 显示圆点
   */
  dot?: boolean;
  /**
   * 消息列表
   */
  notifications?: NotificationItem[];
  /**
   * 未读总数。大于 0 时用数字角标替代圆点，便于客服这类需要知道条数的提醒。
   */
  total?: number;
}

defineOptions({ name: 'NotificationPopup' });

const props = withDefaults(defineProps<Props>(), {
  dot: false,
  notifications: () => [],
  total: 0,
});

const emit = defineEmits<{
  clear: [];
  makeAll: [];
  read: [NotificationItem];
  viewAll: [];
}>();

const [open, toggle] = useToggle();

/** 角标超过两位数就收敛为 99+，避免撑破按钮。 */
const badge = computed(() => (props.total > 99 ? '99+' : String(props.total)));

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

function handleClear() {
  emit('clear');
}

function handleClick(item: NotificationItem) {
  emit('read', item);
}
</script>
<template>
  <VbenPopover
    v-model:open="open"
    content-class="relative right-2 w-[360px] p-0"
  >
    <template #trigger>
      <div class="flex-center mr-2 h-full" @click.stop="toggle()">
        <VbenIconButton class="bell-button text-foreground relative">
          <!-- 有具体条数时用数字角标，没有条数才回落到圆点，两者不叠加 -->
          <span
            v-if="dot && total <= 0"
            class="bg-primary absolute right-0.5 top-0.5 h-2 w-2 rounded"
          ></span>
          <span
            v-if="total > 0"
            class="bg-destructive text-destructive-foreground absolute -right-1.5 -top-1 z-10 flex h-4 min-w-4 items-center justify-center rounded-full px-1 text-[10px] font-medium leading-none"
          >
            {{ badge }}
          </span>
          <Bell class="size-4" />
        </VbenIconButton>
      </div>
    </template>

    <div class="relative">
      <div class="flex items-center justify-between p-4 py-3">
        <div class="text-foreground">{{ $t('ui.widgets.notifications') }}</div>
        <VbenIconButton
          :disabled="notifications.length <= 0"
          :tooltip="$t('ui.widgets.markAllAsRead')"
          @click="handleMakeAll"
        >
          <MailCheck class="size-4" />
        </VbenIconButton>
      </div>
      <VbenScrollbar v-if="notifications.length > 0">
        <ul class="!flex max-h-[360px] w-full flex-col">
          <template
            v-for="item in notifications"
            :key="item.conversationId || item.messageId || item.title"
          >
            <li
              class="hover:bg-accent border-border relative flex w-full cursor-pointer items-start gap-5 border-t px-3 py-3"
              @click="handleClick(item)"
            >
              <span
                v-if="!item.isRead"
                class="bg-primary absolute right-2 top-2 h-2 w-2 rounded"
              ></span>

              <span
                class="relative flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-full"
                :class="
                  item.avatarText
                    ? 'bg-primary/10 text-primary text-sm font-semibold'
                    : ''
                "
              >
                <template v-if="item.avatarText">{{
                  item.avatarText
                }}</template>
                <img
                  v-else
                  :src="item.avatar"
                  class="aspect-square h-full w-full object-cover"
                  role="img"
                />
              </span>
              <div class="flex min-w-0 flex-1 flex-col gap-1 leading-none">
                <div class="flex items-center gap-2">
                  <p class="font-semibold">{{ item.title }}</p>
                  <span
                    v-if="item.tag"
                    class="bg-warning/15 text-warning rounded px-1.5 py-0.5 text-[10px] font-medium leading-none"
                  >
                    {{ item.tag }}
                  </span>
                  <span
                    v-if="item.unreadCount"
                    class="bg-destructive text-destructive-foreground ml-auto flex h-4 min-w-4 items-center justify-center rounded-full px-1 text-[10px] font-medium leading-none"
                  >
                    {{ item.unreadCount > 99 ? '99+' : item.unreadCount }}
                  </span>
                </div>
                <p class="text-muted-foreground my-1 line-clamp-2 text-xs">
                  {{ item.message }}
                </p>
                <p class="text-muted-foreground line-clamp-2 text-xs">
                  {{ item.date }}
                </p>
              </div>
            </li>
          </template>
        </ul>
      </VbenScrollbar>

      <template v-else>
        <div class="flex-center text-muted-foreground min-h-[150px] w-full">
          {{ $t('common.noData') }}
        </div>
      </template>

      <div
        class="border-border flex items-center justify-between border-t px-4 py-3"
      >
        <VbenButton
          :disabled="notifications.length <= 0"
          size="sm"
          variant="ghost"
          @click="handleClear"
        >
          {{ $t('ui.widgets.clearNotifications') }}
        </VbenButton>
        <VbenButton size="sm" @click="handleViewAll">
          {{ $t('ui.widgets.viewAll') }}
        </VbenButton>
      </div>
    </div>
  </VbenPopover>
</template>

<style scoped>
:deep(.bell-button) {
  &:hover {
    svg {
      animation: bell-ring 1s both;
    }
  }
}

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
</style>
