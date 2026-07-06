<script lang="ts" setup>
import { computed, ref, watch } from 'vue';

import { AuthenticationLoginExpiredModal } from '@vben/common-ui';
import {
  ARYN_DOC_URL,
  ARYN_GITEE_URL,
  ARYN_PLATFORM_TENANT_ID,
} from '@vben/constants';
import { useWatermark } from '@vben/hooks';
import { BookOpenText, CircleHelp, MdiGithub } from '@vben/icons';
import { BasicLayout, LockScreen, UserDropdown } from '@vben/layouts';
import { preferences } from '@vben/preferences';
import { useAccessStore, useUserStore } from '@vben/stores';
import { openWindow } from '@vben/utils';

import { ElOption, ElSelect, ElTour, ElTourStep } from 'element-plus';

import { getList } from '#/api/upms/tenant';
import { $t } from '#/locales';
import { useAuthStore } from '#/store';
import LoginForm from '#/views/_core/authentication/login.vue';

const userStore = useUserStore();
const authStore = useAuthStore();
const accessStore = useAccessStore();
const { destroyWatermark, updateWatermark } = useWatermark();

const switchTenantId = ref(localStorage.getItem('switch-tenant-id') ?? '');
const tenantList = ref();
const tenantRef = ref();
const openTour = ref(false);
const menus = computed(() => [
  {
    handler: () => {
      openWindow(ARYN_DOC_URL, {
        target: '_blank',
      });
    },
    icon: BookOpenText,
    text: $t('ui.widgets.document'),
  },
  {
    handler: () => {
      openWindow(ARYN_GITEE_URL, {
        target: '_blank',
      });
    },
    icon: MdiGithub,
    text: 'GitHub',
  },
  {
    handler: () => {
      openWindow(`${ARYN_GITEE_URL}/issues`, {
        target: '_blank',
      });
    },
    icon: CircleHelp,
    text: $t('ui.widgets.qa'),
  },
]);

const avatar = computed(() => {
  return userStore.userInfo?.avatar ?? preferences.app.defaultAvatar;
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
        :menus
        :text="userStore.userInfo?.username"
        :description="userStore.userInfo?.email"
        tag-text="Pro"
        @logout="handleLogout"
      />
    </template>
    <!-- <template #notification>
      <Notification
        :dot="showDot"
        :notifications="notifications"
        @clear="handleNoticeClear"
        @make-all="handleMakeAll"
      />
    </template> -->
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
