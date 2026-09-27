<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import { computed, reactive, ref } from 'vue';

import { Lock, User } from '@element-plus/icons-vue';
import { ElButton, ElForm, ElFormItem, ElInput } from 'element-plus';

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
      trigger: 'blur',
    },
  ],
  password: [
    {
      message: $t('authentication.passwordTip'),
      required: true,
      trigger: 'blur',
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
  <div>
    <div class="mb-8">
      <h1 class="text-foreground text-2xl font-semibold">
        {{ $t('page.auth.loginWelcome') }}
      </h1>
      <p class="text-muted-foreground mt-2 text-sm">
        {{ $t('page.auth.loginWelcomeDesc') }}
      </p>
    </div>

    <ElForm
      ref="formRef"
      :model="loginForm"
      :rules="rules"
      size="large"
      @keyup.enter="handleSubmit"
    >
      <ElFormItem prop="username">
        <ElInput
          v-model="loginForm.username"
          :placeholder="$t('authentication.usernameTip')"
          :prefix-icon="User"
          autocomplete="username"
          name="username"
        />
      </ElFormItem>
      <ElFormItem prop="password">
        <ElInput
          v-model="loginForm.password"
          :placeholder="$t('authentication.passwordTip')"
          :prefix-icon="Lock"
          autocomplete="current-password"
          name="password"
          show-password
          type="password"
        />
      </ElFormItem>
      <ElFormItem class="pt-2">
        <ElButton
          class="w-full"
          :loading="authStore.loginLoading"
          type="primary"
          @click="handleSubmit"
        >
          {{ $t('common.login') }}
        </ElButton>
      </ElFormItem>
    </ElForm>

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
