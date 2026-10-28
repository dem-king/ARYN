<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import { computed, reactive, ref } from 'vue';

import { ArrowRight, InfoFilled, Lock, User } from '@element-plus/icons-vue';
import { ElButton, ElForm, ElFormItem, ElIcon, ElInput } from 'element-plus';

import Verify from '#/components/verifition/index.vue';
import { $t } from '#/locales';
import { useAuthStore } from '#/store';

defineOptions({ name: 'Login' });

const authStore = useAuthStore();
const formRef = ref<FormInstance>();
const verifyRef = ref();
const captchaType = ref('blockPuzzle'); // 1）滑动拼图 blockPuzzle 2）文字点选 clickWord
const loginUser = ref({
  username: '',
  password: '',
  code: '',
  randomStr: 'blockPuzzle',
});

const loginForm = reactive({
  username: '',
  password: '',
});

const rules = computed((): FormRules => ({
  username: [
    {
      message: $t('authentication.usernameTip'),
      required: true,
      trigger: 'change',
    },
  ],
  password: [
    {
      message: $t('authentication.passwordTip'),
      required: true,
      trigger: 'change',
    },
  ],
}));

const handleSubmit = () => {
  formRef.value?.validate((valid) => {
    if (!valid) {
      return;
    }
    loginUser.value.username = loginForm.username;
    loginUser.value.password = loginForm.password;
    verifyRef.value.show();
  });
};

const verifySuccess = async (params: any) => {
  const { captchaVerification } = params;
  loginUser.value.code = captchaVerification;
  try {
    await authStore.authLogin(loginUser.value);
  } catch (error) {
    console.error(error);
  }
};
</script>

<template>
  <div class="auth-login">
    <header class="auth-login__head">
      <p class="auth-login__eyebrow">
        <span aria-hidden="true" class="auth-login__eyebrow-dot"></span>
        {{ $t('page.auth.loginEyebrow') }}
      </p>
      <h1 class="auth-login__title">
        {{ $t('page.auth.loginWelcome') }}
      </h1>
      <p class="auth-login__desc">
        {{ $t('page.auth.loginWelcomeDesc') }}
      </p>
    </header>

    <ElForm
      ref="formRef"
      class="auth-login__form"
      hide-required-asterisk
      label-position="top"
      :model="loginForm"
      :rules="rules"
      :validate-on-rule-change="false"
      size="large"
      @keyup.enter="handleSubmit"
    >
      <ElFormItem :label="$t('page.auth.loginAccountLabel')" prop="username">
        <ElInput
          v-model="loginForm.username"
          :placeholder="$t('page.auth.loginAccountPlaceholder')"
          :prefix-icon="User"
          autocomplete="username"
          autofocus
          name="username"
          @input="formRef?.clearValidate('username')"
        />
      </ElFormItem>
      <ElFormItem :label="$t('page.auth.loginPasswordLabel')" prop="password">
        <ElInput
          v-model="loginForm.password"
          :placeholder="$t('page.auth.loginPasswordPlaceholder')"
          :prefix-icon="Lock"
          autocomplete="current-password"
          name="password"
          show-password
          type="password"
          @input="formRef?.clearValidate('password')"
        />
      </ElFormItem>
      <ElFormItem class="auth-login__actions">
        <ElButton
          class="auth-login__submit w-full"
          :loading="authStore.loginLoading"
          type="primary"
          @click="handleSubmit"
        >
          <span>{{ $t('page.auth.loginSubmit') }}</span>
          <ElIcon
            v-if="!authStore.loginLoading"
            class="auth-login__submit-arrow"
          >
            <ArrowRight />
          </ElIcon>
        </ElButton>
      </ElFormItem>
    </ElForm>

    <p class="auth-login__help">
      <ElIcon :size="14" class="auth-login__help-icon">
        <InfoFilled />
      </ElIcon>
      <span>{{ $t('page.auth.loginHelp') }}</span>
    </p>

    <teleport to="body">
      <Verify
        mode="pop"
        :captcha-type="captchaType"
        :img-size="{ width: '300px', height: '150px' }"
        ref="verifyRef"
        @success="verifySuccess"
      />
    </teleport>
  </div>
</template>

<style scoped>
.auth-login {
  display: flex;
  flex-direction: column;
}

.auth-login__head {
  animation: auth-login-rise 0.62s cubic-bezier(0.22, 1, 0.36, 1) backwards;
  animation-delay: 0.08s;
}

.auth-login__eyebrow {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  font-weight: 500;
  color: hsl(var(--muted-foreground));
  letter-spacing: 0.08em;
}

.auth-login__eyebrow-dot {
  width: 6px;
  height: 6px;
  background: hsl(var(--primary));
  border-radius: 9999px;
  box-shadow: 0 0 0 4px hsl(var(--primary) / 12%);
}

.auth-login__title {
  margin-top: 18px;
  font-size: 30px;
  font-weight: 600;
  line-height: 1.25;
  color: hsl(var(--foreground));
  letter-spacing: -0.01em;
}

.auth-login__desc {
  margin-top: 10px;
  font-size: 14px;
  line-height: 1.7;
  color: hsl(var(--muted-foreground));
}

.auth-login__form {
  margin-top: 32px;
  animation: auth-login-rise 0.62s cubic-bezier(0.22, 1, 0.36, 1) backwards;
  animation-delay: 0.16s;
}

.auth-login__form :deep(.el-form-item) {
  margin-bottom: 24px;
}

.auth-login__form :deep(.el-form-item__label) {
  padding-bottom: 6px;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.4;
  color: hsl(var(--foreground) / 82%);
}

.auth-login__form :deep(.el-input__wrapper) {
  height: 46px;
  padding: 0 14px;
  background: var(--el-fill-color-blank);
  border-radius: 10px;
  box-shadow: 0 0 0 1px var(--el-border-color) inset;
  transition:
    box-shadow 0.2s ease,
    background-color 0.2s ease;
}

.auth-login__form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--el-border-color-hover) inset;
}

.auth-login__form :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1.5px hsl(var(--primary)) inset,
    0 0 0 3px hsl(var(--primary) / 10%);
}

.auth-login__form :deep(.el-input__prefix) {
  margin-right: 2px;
  color: hsl(var(--muted-foreground));
}

.auth-login__form :deep(.el-input__inner) {
  font-size: 14.5px;
}

.auth-login__form :deep(.el-form-item__error) {
  padding-top: 3px;
  font-size: 12px;
}

.auth-login__actions {
  margin-top: 6px;
  margin-bottom: 0 !important;
}

.auth-login__submit {
  --el-button-hover-bg-color: var(--el-color-primary-dark-2);
  --el-button-hover-border-color: var(--el-color-primary-dark-2);
  --el-button-active-bg-color: var(--el-color-primary-dark-2);
  --el-button-active-border-color: var(--el-color-primary-dark-2);

  height: 46px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.02em;
  border-radius: 10px;
  box-shadow: 0 6px 14px -12px hsl(var(--primary) / 55%);
  transition:
    box-shadow 0.22s ease,
    transform 0.22s ease;
}

.auth-login__submit:not(.is-disabled):hover {
  box-shadow: 0 10px 20px -14px hsl(var(--primary) / 55%);
  transform: translateY(-1px);
}

.auth-login__submit:not(.is-disabled):active {
  box-shadow: 0 4px 10px -10px hsl(var(--primary) / 50%);
  transform: translateY(0);
}

.auth-login__submit-arrow {
  margin-left: 2px;
  transition: transform 0.22s ease;
}

.auth-login__submit:hover .auth-login__submit-arrow {
  transform: translateX(3px);
}

.auth-login__help {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  margin-top: 20px;
  font-size: 12.5px;
  line-height: 1.6;
  color: hsl(var(--muted-foreground));
  animation: auth-login-rise 0.62s cubic-bezier(0.22, 1, 0.36, 1) backwards;
  animation-delay: 0.26s;
}

.auth-login__help-icon {
  flex-shrink: 0;
  margin-top: 3px;
}

@keyframes auth-login-rise {
  from {
    opacity: 0;
    transform: translate3d(0, 14px, 0);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .auth-login__head,
  .auth-login__form,
  .auth-login__help {
    animation: none;
  }

  .auth-login__submit,
  .auth-login__submit-arrow {
    transition: none;
  }
}
</style>
