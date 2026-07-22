<script lang="ts" setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { AuthenticationLoginExpiredModal } from '@vben/common-ui';
import { ARYN_PLATFORM_TENANT_ID } from '@vben/constants';
import { useWatermark } from '@vben/hooks';
import {
  BasicLayout,
  LockScreen,
  Notification,
  UserDropdown,
} from '@vben/layouts';
import { preferences } from '@vben/preferences';
import { useAccessStore, useUserStore } from '@vben/stores';

import { ElOption, ElSelect, ElTour, ElTourStep } from 'element-plus';

import { getList } from '#/api/upms/tenant';
import { useAuthStore } from '#/store';
import { useMessageStore } from '#/store/message';
import LoginForm from '#/views/_core/authentication/login.vue';

const userStore = useUserStore();
const authStore = useAuthStore();
const accessStore = useAccessStore();
const messageStore = useMessageStore();
const router = useRouter();
const { destroyWatermark, updateWatermark } = useWatermark();

const switchTenantId = ref(localStorage.getItem('switch-tenant-id') ?? '');
const tenantList = ref();
const tenantRef = ref();
const openTour = ref(false);

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
function handleTenant(val: string) {
  localStorage.setItem('switch-tenant-id', val);
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
if (userStore.userInfo?.tenantId === ARYN_PLATFORM_TENANT_ID) {
  getTenantList();
  if (localStorage.getItem('switch-tenant-id') === null) {
    openTour.value = true;
  }
}

onMounted(() => {
  void messageStore.refreshNotifications();
  messageStore.connect();
});

function handleNoticeRead(item: any) {
  void messageStore.markRead(item);
}

function handleViewAllNotices() {
  void router.push('/message/inbox');
}
</script>

<template>
  <BasicLayout @clear-preferences-and-logout="handleLogout">
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
          v-if="
            userStore.userInfo?.tenantId === ARYN_PLATFORM_TENANT_ID &&
            tenantList
          "
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
        :text="userStore.userInfo?.username"
        :description="userStore.userInfo?.email"
        tag-text="Pro"
        @logout="handleLogout"
      />
    </template>
    <template #notification>
      <Notification
        :dot="messageStore.showDot"
        :notifications="messageStore.notifications"
        @clear="messageStore.markAllRead"
        @make-all="messageStore.markAllRead"
        @read="handleNoticeRead"
        @view-all="handleViewAllNotices"
      />
    </template>
    <template #extra>
      <AuthenticationLoginExpiredModal
        v-model:open="accessStore.loginExpired"
        :avatar
      >
        <LoginForm />
      </AuthenticationLoginExpiredModal>
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
