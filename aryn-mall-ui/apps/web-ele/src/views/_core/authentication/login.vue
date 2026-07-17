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
ElNotification({
  title: '登录账号',
  dangerouslyUseHTMLString: true,
  message:
    '<p>平台管理员账号：system/123456</p><p>租户1管理员账号：admin/123456</p>',
  duration: 0,
});
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
      dependencies: {
        trigger(_values, form) {
          form.setValues({
            password: '123456',
            username: 'system',
          });
        },
        triggerFields: ['selectAccount'],
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
