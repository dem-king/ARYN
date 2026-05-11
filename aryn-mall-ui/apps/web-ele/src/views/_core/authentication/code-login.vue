<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';
import type { Recordable } from '@vben/types';

import { computed } from 'vue';

import { AuthenticationCodeLogin, z } from '@vben/common-ui';
import { $t } from '@vben/locales';

import { ElMessage } from 'element-plus';

import { useAuthStore } from '#/store';

defineOptions({ name: 'CodeLogin' });

const CODE_LENGTH = 4;
const authStore = useAuthStore();
const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      component: 'VbenInput',
      componentProps: {
        placeholder: $t('authentication.mobile'),
      },
      fieldName: 'phoneNumber',
      label: $t('authentication.mobile'),
      rules: z
        .string()
        .min(1, { message: $t('authentication.mobileTip') })
        .refine((v) => /^\d{11}$/.test(v), {
          message: $t('authentication.mobileErrortip'),
        }),
    },
    {
      component: 'VbenPinInput',
      componentProps: (values) => {
        return {
          codeLength: CODE_LENGTH,
          createText: (countdown: number) => {
            const text =
              countdown > 0
                ? $t('authentication.sendText', [countdown])
                : $t('authentication.sendCode');
            return text;
          },
          handleSendCode: async () => {
            const phoneNumber = `${values.phoneNumber ?? ''}`.trim();
            if (!phoneNumber) {
              const message = $t('authentication.mobileTip');
              ElMessage.warning(message);
              throw new Error(message);
            }
            if (!/^\d{11}$/.test(phoneNumber)) {
              const message = $t('authentication.mobileErrortip');
              ElMessage.warning(message);
              throw new Error(message);
            }
            try {
              await authStore.sendSmsCode('login', phoneNumber);
              ElMessage.success('发送成功');
            } catch (error) {
              console.error(error);
              throw error;
            }
          },
          placeholder: $t('authentication.code'),
        };
      },
      fieldName: 'code',
      label: $t('authentication.code'),
      rules: z.string().length(CODE_LENGTH, {
        message: $t('authentication.codeTip', [CODE_LENGTH]),
      }),
    },
  ];
});
/**
 * 异步处理登录操作
 * Asynchronously handle the login process
 * @param values 登录表单数据
 */
async function handleLogin(values: Recordable<any>) {
  try {
    await authStore.authMobileLogin({
      phone: values.phoneNumber,
      code: values.code,
    });
  } catch (error) {
    console.error(error);
  }
}
</script>

<template>
  <AuthenticationCodeLogin
    :form-schema="formSchema"
    :loading="authStore.loginLoading"
    @submit="handleLogin"
  />
</template>
