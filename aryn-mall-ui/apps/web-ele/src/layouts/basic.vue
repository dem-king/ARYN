<script lang="ts" setup>
import type { NotificationItem } from '@vben/layouts';

import type { TenantShopInfo } from '#/api/upms/tenant';

import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { useVbenModal } from '@vben/common-ui';
import { useWatermark } from '@vben/hooks';
import {
  BasicLayout,
  LockScreen,
  Notification,
  UserDropdown,
} from '@vben/layouts';
import { preferences } from '@vben/preferences';
import { useUserStore } from '@vben/stores';

import { Monitor, User } from '@element-plus/icons-vue';
import { ElOption, ElSelect, ElTour, ElTourStep } from 'element-plus';

import { auditTenantSwitch, getList, getShopInfo } from '#/api/upms/tenant';
import { $t } from '#/locales';
import { useAuthStore } from '#/store';
import { useMessageStore } from '#/store/message';

import LoginDevicesModalContent from './login-devices-modal.vue';
import SessionInvalidModal from './session-invalid-modal.vue';
import { resolveAdminBrand } from './tenant-brand';

const userStore = useUserStore();
const authStore = useAuthStore();
const messageStore = useMessageStore();
const router = useRouter();
const { destroyWatermark, updateWatermark } = useWatermark();

/**
 * 登录设备弹窗（三期）：用户下拉菜单入口 + app 级弹窗内容组件。
 * 会话失效弹窗与本弹窗互不影响；打开时才拉取设备/登录记录数据。
 */
const [LoginDevicesModal, loginDevicesModalApi] = useVbenModal({
  connectedComponent: LoginDevicesModalContent,
  destroyOnClose: true,
});

const userMenus = computed(() => [
  {
    handler: () => void router.push('/profile'),
    icon: User,
    text: $t('page.profile.menu'),
  },
  {
    handler: () => loginDevicesModalApi.open(),
    icon: Monitor,
    text: $t('page.loginDevices.menu'),
  },
]);

const switchTenantId = ref(localStorage.getItem('switch-tenant-id') ?? '');
const tenantList = ref();
const tenantRef = ref();
const openTour = ref(false);
const tenantShopInfo = ref<null | TenantShopInfo>(null);

// 平台管理员是用户级身份：由后端按 sys_user.platform_admin + 平台租户类型计算后下发，
// 平台租户里的租户管理员不是平台管理员（不显示租户切换与平台管理菜单）
const isPlatformAdmin = computed(
  () => userStore.userInfo?.platformAdmin === true,
);

// 平台管理员切换到租户后，userInfo 仍是平台租户，需以本地切换值为准
const currentTenantId = computed(() => {
  if (isPlatformAdmin.value) {
    return localStorage.getItem('switch-tenant-id') || '';
  }
  return userStore.userInfo?.tenantId || '';
});

const brand = computed(() =>
  resolveAdminBrand(currentTenantId.value, tenantShopInfo.value),
);

// 平台租户未切换时 currentTenantId 为空，继续使用平台品牌；其余场景按当前租户取品牌
if (currentTenantId.value) {
  getShopInfo()
    .then((info) => {
      tenantShopInfo.value = info;
    })
    .catch(() => {
      tenantShopInfo.value = null;
    });
}

const avatar = computed(() => {
  return userStore.userInfo?.avatar?.trim() || undefined;
});

async function handleLogout() {
  await authStore.logout(false);
}

function getTenantList() {
  getList({}).then((res) => {
    tenantList.value = res;
  });
}
async function handleTenant(val: string) {
  localStorage.setItem('switch-tenant-id', val);
  if (val) {
    try {
      await auditTenantSwitch(val);
    } catch {
      // 审计失败不阻断切换
    }
  }
  window.location.reload();
}
watch(
  () => preferences.app.watermark,
  async (enable) => {
    if (enable) {
      await updateWatermark({
        content: `${userStore.userInfo?.username} - ${userStore.userInfo?.realName}`,
      });
    } else {
      destroyWatermark();
    }
  },
  {
    immediate: true,
  },
);
if (isPlatformAdmin.value) {
  getTenantList();
  if (localStorage.getItem('switch-tenant-id') === null) {
    openTour.value = true;
  }
}

onMounted(() => {
  // start() 一并挂上「首次任意手势解锁音频」：只靠开关解锁会让
  // 打开后台后没点过任何东西的管理员静默漏掉第一条新订单
  messageStore.start();
});

/**
 * 铃铛里的提醒分两类：
 * - 客服会话：登记待定位会话并跳到工作台，由工作台打开（待领取会话进“待领取”页签）；
 * - 站内信：先标记已读，再按后端下发的业务路由直达订单/退款/船舶审核等页面。
 *
 * 路由在 store 里已由 resolveNoticeLink 解析并做了安全白名单，这里只负责跳转；
 * 没有业务路由的系统通告保持“只标已读”，不制造点了没反应的假象。
 */
function handleNoticeRead(item: NotificationItem) {
  if (item.kind === 'conversation') {
    if (item.conversationId) {
      messageStore.requestConversation(item.conversationId);
    }
    void router.push(item.link || '/message/service');
    return;
  }
  void messageStore.markRead(item);
  if (item.link) {
    void router.push(item.link);
  }
}

function handleViewAllNotices() {
  void router.push('/message/inbox');
}
</script>

<template>
  <BasicLayout
    :app-name="brand.name"
    :logo-source="brand.logo"
    @clear-preferences-and-logout="handleLogout"
  >
    <template #tenant-select>
      <ElTour v-model="openTour">
        <ElTourStep
          :target="tenantRef?.$el"
          title="租户切换指引"
          description="您拥有平台管理员权限，可以切换租户进行管理"
        />
      </ElTour>
      <Transition name="tenant">
        <ElSelect
          v-if="isPlatformAdmin && tenantList"
          ref="tenantRef"
          v-model="switchTenantId"
          placeholder="请切换租户"
          style="width: 150px; margin-right: 10px"
          @change="handleTenant"
        >
          <ElOption
            v-for="item in tenantList"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </ElSelect>
      </Transition>
    </template>
    <template #user-dropdown>
      <UserDropdown
        :avatar
        :menus="userMenus"
        :text="userStore.userInfo?.username"
        :description="userStore.userInfo?.email"
        tag-text="Pro"
        @logout="handleLogout"
      />
    </template>
    <template #notification>
      <Notification
        :desktop-enabled="messageStore.desktopEnabled"
        :dot="messageStore.showDot"
        :notifications="messageStore.notifications"
        :sound-enabled="messageStore.soundEnabled"
        :total="messageStore.totalUnread"
        :voice-enabled="messageStore.voiceEnabled"
        @make-all="messageStore.markAllRead"
        @read="handleNoticeRead"
        @toggle-desktop="messageStore.setDesktopEnabled"
        @toggle-sound="messageStore.setSoundEnabled"
        @toggle-voice="messageStore.setVoiceEnabled"
        @view-all="handleViewAllNotices"
      />
    </template>
    <template #extra>
      <!-- 登录失效（被顶下线/过期/被踢）阻塞弹窗：不可关闭，原地重登或退出登录 -->
      <SessionInvalidModal />
      <!-- 登录设备：活跃会话 + 最近登录记录，非当前会话可下线 -->
      <LoginDevicesModal />
    </template>
    <template #lock-screen>
      <LockScreen :avatar @to-login="handleLogout" />
    </template>
  </BasicLayout>
</template>
<style lang="scss" scoped>
.tenant-enter-active,
.tenant-leave-active {
  transition: opacity 0.3s ease;
}

.tenant-enter-from,
.tenant-leave-to {
  opacity: 0;
}
</style>
