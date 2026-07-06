<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import { computed, ref } from 'vue';

import { AuthenticationLogin, z } from '@vben/common-ui';
import { $t } from '@vben/locales';

import { ElNotification } from 'element-plus';

import Verify from '#/components/verifition/index.vue';
import { useAuthStore } from '#/store';

defineOptions({ name: 'Login' });
const verifyRef = ref();
const captchaType = ref('blockPuzzle'); // 1）滑动拼图 blockPuzzle 2）文字点选 clickWord

// 仅在开发环境显示测试账号提示（生产环境严禁暴露凭据）
if (import.meta.env.DEV) {
  ElNotification({
    title: '测试账号（仅开发环境）',
    dangerouslyUseHTMLString: true,
    message:
      '<p>测试账号请在 .env.development 中配置，请勿在生产环境使用默认密码</p>',
    duration: 5000,
  });
}

const authStore = useAuthStore();
const loginUser = ref({
  username: '',
  password: '',
  code: '',
  randomStr: 'blockPuzzle',
});

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      component: 'VbenInput',
      componentProps: {
        placeholder: $t('authentication.usernameTip'),
      },
      fieldName: 'username',
      label: $t('authentication.username'),
      rules: z.string().min(1, { message: $t('authentication.usernameTip') }),
    },
    {
      component: 'VbenInputPassword',
      componentProps: {
        placeholder: $t('authentication.password'),
      },
      fieldName: 'password',
      label: $t('authentication.password'),
      rules: z.string().min(1, { message: $t('authentication.passwordTip') }),
    },
  ];
});
const handleSubmit = (val: any) => {
  loginUser.value.username = val.username;
  loginUser.value.password = val.password;
  verifyRef.value.show();
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
    <AuthenticationLogin
      :form-schema="formSchema"
      :loading="authStore.loginLoading"
      :show-register="false"
      @submit="handleSubmit"
    />
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
