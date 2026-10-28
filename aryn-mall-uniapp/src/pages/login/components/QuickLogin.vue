<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/store/authStore'

interface Props {
  read: boolean
}

interface Emits {
  (e: 'update:read', value: boolean): void
  (e: 'login-success'): void
  (e: 'wx-login'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const { t } = useI18n()
const { confirm } = useGlobalMessage()
const { success: showSuccess, error: showError } = useGlobalToast()
const authStore = useAuthStore()
const loading = ref(false)
// 微信登录
function handleWxLogin() {
  if (loading.value)
    return

  if (!props.read) {
    confirm({
      title: t('login.agreementTitle'),
      msg: t('login.agreementMessage'),
      closeOnClickModal: false,
      confirmButtonText: t('login.agree'),
      cancelButtonText: t('common.cancel'),
      success: (res) => {
        if (res.action === 'confirm') {
          emit('update:read', true)
          emit('wx-login')
        }
      },
    })
    return
  }

  emit('wx-login')
}
// 获取手机号回调（微信小程序）：原生 button 事件带一层 detail
function onGetPhoneNumber(event: any) {
  getPhoneNumber(event?.detail)
}
async function getPhoneNumber(detail: any) {
  const { errMsg, code } = detail || {}
  if (errMsg !== 'getPhoneNumber:ok' || !code) {
    showError(t('login.getPhoneFailed'))
    return
  }

  if (!props.read) {
    confirm({
      title: t('login.agreementTitle'),
      msg: t('login.agreementMessage'),
      closeOnClickModal: false,
      confirmButtonText: t('login.agree'),
      cancelButtonText: t('common.cancel'),
      success: (res) => {
        if (res.action === 'confirm') {
          // 用户确认阅读并同意
          emit('update:read', true)
          loginHandler(code)
        }
      },
    })
  }
  else {
    loginHandler(code)
  }
}
async function loginHandler(code: string) {
  if (loading.value)
    return

  loading.value = true
  try {
    await authStore.quickLogin({ code })
    showSuccess(t('login.loginSuccess'))
    emit('login-success')
  }
  catch (error: any) {
    showError(error.message || t('login.loginFailed'))
  }
  finally {
    loading.value = false
  }
}
// 当获取手机号失败时
function onGetPhoneNumberError(event: any) {
  showError(event?.detail?.errMsg || t('login.getPhoneFailed'))
}
</script>

<template>
  <view class="quick-login">
    <button
      class="login-submit login-submit--wechat"
      hover-class="login-submit--pressed"
      :hover-stay-time="80"
      @click="handleWxLogin"
    >
      <text class="login-submit__label">
        {{ t('login.wechatLogin') }}
      </text>
    </button>
    <button
      class="login-submit login-submit--ghost login-submit--stacked"
      :class="{ 'is-loading': loading }"
      hover-class="login-submit--pressed"
      :hover-stay-time="80"
      open-type="getPhoneNumber"
      @getphonenumber="onGetPhoneNumber"
      @error="onGetPhoneNumberError"
    >
      <view v-if="loading" class="login-spinner" />
      <text v-else class="i-carbon:mobile login-submit__icon" />
      <text class="login-submit__label">
        {{ t('login.phoneQuickLogin') }}
      </text>
    </button>
  </view>
</template>

<style scoped lang="scss">
@import './login-controls.scss';

.quick-login .login-submit {
  margin-top: 0;
}

/* 两枚按钮间距不用 flex gap：部分安卓内核不支持，改为显式外边距 */
.quick-login .login-submit--stacked {
  margin-top: 24rpx;
}
</style>
