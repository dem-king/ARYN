<script lang="ts" setup>
import type { SessionInvalidView } from './session-invalid-content';

import { computed, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { useAccessStore } from '@vben/stores';

import { WarningFilled } from '@element-plus/icons-vue';
import { ElButton, ElIcon } from 'element-plus';

import { SESSION_INVALID_MODAL_Z_INDEX } from '#/api/session-invalid';
import { useAuthStore, useSessionStore } from '#/store';
import LoginForm from '#/views/_core/authentication/login.vue';

import { resolveSessionInvalidView } from './session-invalid-content';

defineOptions({ name: 'SessionInvalidModal' });

/**
 * 会话失效（被顶下线 / 登录过期 / 被踢下线）阻塞弹窗。
 *
 * - 不可关闭：无右上角关闭、点遮罩/ESC 无效、无 footer，唯一出口是「重新登录」或「退出登录」；
 * - 原地重登：直接复用登录页的 LoginForm（含账号/密码/图形验证码），不新写登录逻辑；
 * - 重登成功后由 `authStore.authLogin` 收尾（关闭弹窗、重置失效态、停留当前路由）。
 * - 二期按钮规则：`canRelogin=false`（账号停用/删除、租户停用）时**不渲染**「重新登录」，
 *   只给一个主按钮（「返回登录」/「退出」），点击=退出且不带回跳参数，避免用户白试重登。
 *
 * <p>z-index 固定低于图形验证码遮罩（1001）：否则弹窗内登录的滑块/点选验证会被挡住。</p>
 */
const accessStore = useAccessStore();
const sessionStore = useSessionStore();
const authStore = useAuthStore();

const [Modal, modalApi] = useVbenModal();

watch(
  () => accessStore.loginExpired,
  (val) => {
    modalApi.setState({ isOpen: val });
  },
  { immediate: true },
);

const view = computed<SessionInvalidView>(() =>
  resolveSessionInvalidView({
    detectedAt: sessionStore.detectedAt,
    device: sessionStore.device,
    invalidAt: sessionStore.invalidAt,
    reason: sessionStore.reason,
    subReason: sessionStore.subReason,
  }),
);

async function handleLogout() {
  try {
    // logout() 内部已处理：会话已失效时跳过服务端退出，只做本地清理 + 回登录页
    await authStore.logout();
  } catch (error) {
    console.warn(
      '[session-invalid-modal] 退出登录失败：',
      (error as Error)?.message,
    );
  }
}

/**
 * 不可重新登录时的单按钮动作（返回登录/退出）：清凭证 + 回登录页，
 * **不留回跳**（重登必然失败，回到原页面没有意义）。
 */
async function handleForcedExit() {
  try {
    await authStore.logout(false);
  } catch (error) {
    console.warn(
      '[session-invalid-modal] 返回登录失败：',
      (error as Error)?.message,
    );
  }
}
</script>

<template>
  <div data-session-invalid-modal-host>
    <Modal
      :closable="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :footer="false"
      :fullscreen-button="false"
      :header="false"
      :z-index="SESSION_INVALID_MODAL_Z_INDEX"
      class="border-none px-10 py-6 text-center shadow-xl sm:w-[600px] sm:rounded-2xl md:h-[unset]"
    >
      <div class="session-invalid-modal">
        <ElIcon class="session-invalid-modal__icon text-warning" :size="42">
          <WarningFilled />
        </ElIcon>
        <h2 class="session-invalid-modal__title">
          {{ view.title }}
        </h2>
        <p class="session-invalid-modal__body">
          {{ view.body }}
        </p>
        <template v-if="view.canRelogin">
          <div class="session-invalid-modal__form text-left">
            <LoginForm :submit-text="$t('sessionInvalid.relogin')" />
          </div>
          <ElButton
            class="session-invalid-modal__exit"
            text
            type="primary"
            @click="handleLogout"
          >
            {{ $t('sessionInvalid.exit') }}
          </ElButton>
        </template>
        <template v-else>
          <!-- 重登必然失败：只渲染一个退出按钮，不留回跳 -->
          <ElButton
            class="session-invalid-modal__single-action"
            type="primary"
            @click="handleForcedExit"
          >
            {{ view.singleActionText || $t('sessionInvalid.exitShort') }}
          </ElButton>
        </template>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.session-invalid-modal {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.session-invalid-modal__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
}

.session-invalid-modal__title {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.35;
  color: hsl(var(--foreground));
}

.session-invalid-modal__body {
  padding: 0 8px;
  margin-top: 12px;
  font-size: 14px;
  line-height: 1.75;
  color: hsl(var(--muted-foreground));
  white-space: pre-line;
}

.session-invalid-modal__form {
  width: 100%;
  margin-top: 20px;
}

.session-invalid-modal__form :deep(.auth-login__head) {
  display: none;
}

.session-invalid-modal__form :deep(.auth-login__help) {
  display: none;
}

.session-invalid-modal__exit {
  margin-top: 8px;
  color: hsl(var(--muted-foreground));
}

.session-invalid-modal__exit:hover {
  color: hsl(var(--foreground));
}

.session-invalid-modal__single-action {
  width: 100%;
  height: 44px;
  margin-top: 24px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 10px;
}
</style>
