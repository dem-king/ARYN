<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';
import type { Recordable } from '@vben/types';

import { computed, ref, watch } from 'vue';

import { AuthenticationCodeLogin, z } from '@vben/common-ui';
import { $t } from '@vben/locales';

import { ElMessage } from 'element-plus';

import Verify from '#/components/verifition/index.vue';
import { useAuthStore } from '#/store';

defineOptions({ name: 'CodeLogin' });

const CODE_LENGTH = 4;
const authStore = useAuthStore();
const verifyRef = ref();
// 1）滑动拼图 blockPuzzle 2）文字点选 clickWord
const captchaType = ref('blockPuzzle');

interface PendingSendCode {
  phone: string;
  reject: (error: unknown) => void;
  resolve: () => void;
}

/**
 * 等待图形验证码结果的「待发送短信」请求。
 *
 * 倒计时时机：@vben PinInput 的实现是 `await handleSendCode()` 之后才开始 60s 倒计时
 * （见 packages/@core/ui-kit/shadcn-ui/src/components/pin-input/input.vue），
 * 所以 handleSendCode 在这里返回一个挂起 Promise，把真正发送短信的时机推迟到
 * 「图形验证成功」之后：
 * - 用户关闭弹窗/未完成图形验证：Promise 不会 resolve，倒计时不启动、短信不发送；
 * - 图形验证成功但短信接口失败：Promise reject，倒计时同样不启动；
 * - 图形验证成功且短信发送成功：Promise resolve，PinInput 才开始倒计时。
 */
let pendingSendCode: null | PendingSendCode = null;
// 图形验证成功、正在请求短信接口：此时再次点击「获取验证码」直接拒绝，
// 避免并发下发短信，也避免 PinInput 同时启动多条倒计时链。
let sendingSms = false;

function rejectPendingSendCode(error: unknown) {
  const pending = pendingSendCode;
  pendingSendCode = null;
  pending?.reject(error);
}

/**
 * 拉起图形验证码，并返回「验证成功且短信发送成功」后才 resolve 的 Promise。
 */
function requestCaptchaVerification(phone: string) {
  if (sendingSms) {
    const message = '短信正在发送，请稍候';
    ElMessage.warning(message);
    return Promise.reject(new Error(message));
  }
  // 上一次弹窗被取消后可能还挂着旧请求，先取消，避免旧手机号重复发送或重复启动倒计时。
  rejectPendingSendCode(new Error('已重新发起图形验证，旧的发送请求已取消'));

  const pending: PendingSendCode = {
    phone,
    reject: () => {},
    resolve: () => {},
  };
  const waitSend = new Promise<void>((resolve, reject) => {
    pending.reject = reject;
    pending.resolve = resolve;
  });
  pendingSendCode = pending;

  const verify = verifyRef.value;
  if (typeof verify?.show !== 'function') {
    const message = '图形验证码组件未就绪，请刷新页面后重试';
    rejectPendingSendCode(new Error(message));
    ElMessage.error(message);
    return waitSend;
  }
  verify.show();
  return waitSend;
}

/**
 * 图形验证码通过后（参数含 captchaVerification）才真正发送短信，
 * 成功后才 resolve 挂起的 Promise，从而启动发送按钮倒计时。
 */
async function verifySuccess(params: Recordable<any>) {
  const pending = pendingSendCode;
  if (!pending) {
    // 已经取消/重复触发的成功事件，忽略
    return;
  }
  pendingSendCode = null;
  const captchaVerification = `${params?.captchaVerification ?? ''}`.trim();
  if (!captchaVerification) {
    pending.reject(new Error('图形验证码校验失败，请重试'));
    return;
  }
  try {
    sendingSms = true;
    await authStore.sendSmsCode('login', pending.phone, captchaVerification);
    ElMessage.success('发送成功');
    pending.resolve();
  } catch (error) {
    console.error(error);
    // 短信发送失败：reject 挂起 Promise，PinInput 不会开始倒计时
    pending.reject(error);
  } finally {
    sendingSms = false;
  }
}

// 关闭图形验证码弹窗（取消）不会触发 success 事件，只能通过弹窗可见性变化感知：
// 此时 reject 挂起 Promise，PinInput 的 await 抛错，不会启动 60s 倒计时也不会发短信。
watch(
  () => verifyRef.value?.clickShow,
  (visible, previousVisible) => {
    if (previousVisible === true && visible === false && pendingSendCode) {
      rejectPendingSendCode(new Error('已取消图形验证，短信未发送'));
    }
  },
);

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
            // 先完成图形验证，验证成功且短信发送成功后才 resolve，PinInput 才会开始倒计时
            await requestCaptchaVerification(phoneNumber);
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

  <teleport to="body">
    <Verify
      ref="verifyRef"
      :captcha-type="captchaType"
      :img-size="{ width: '300px', height: '150px' }"
      mode="pop"
      @success="verifySuccess"
    />
  </teleport>
</template>
