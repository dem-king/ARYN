<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { sendSmsCode } from '@/api/upms/sms'
import { useAuthStore } from '@/store/authStore'

interface Props {
  read: boolean
}

interface Emits {
  (e: 'update:read', value: boolean): void
  (e: 'login-success'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const { t } = useI18n()
const { success: showSuccess, error: showError } = useGlobalToast()
const authStore = useAuthStore()
const { confirm } = useGlobalMessage()

// 表单数据
const formData = ref({
  phone: '',
  verifyCode: '',
})

// 当前聚焦的字段：只驱动外框描边与图标变品牌色
const focusField = ref<'phone' | 'code' | ''>('')

// 验证码相关状态
const countdown = ref(60)
const isDisabled = ref(false)
const timer = ref<NodeJS.Timeout>()
const formRef = ref()
const loading = ref(false)

// 手机号校验方法
function validatePhone(phone: string): boolean {
  const regex = /^1[3-9]\d{9}$/
  return regex.test(phone)
}

// 获取验证码
async function getCode() {
  const phone = formData.value.phone
  if (!phone) {
    showError(t('login.phoneRequired'))
    return
  }
  if (!validatePhone(phone)) {
    showError(t('login.phoneInvalid'))
    return
  }
  if (isDisabled.value) {
    showError(t('login.sendingTooOften'))
    return
  }

  try {
    // 这里调用发送验证码的API
    await sendSmsCode('login', phone)
    showSuccess(t('login.codeSent'))
    isDisabled.value = true

    timer.value = setInterval(() => {
      countdown.value--
      if (countdown.value === 0) {
        clearInterval(timer.value)
        isDisabled.value = false
        countdown.value = 60
      }
    }, 1000)
  }
  catch {
    showError(t('login.codeSendFailed'))
  }
}

// 登录逻辑
async function handleLogin() {
  if (loading.value || !formRef.value)
    return

  const { valid } = await formRef.value.validate()
  if (!valid)
    return

  const phone = formData.value.phone
  if (!validatePhone(phone)) {
    showError(t('login.phoneInvalid'))
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
          loginHandler()
        }
      },
    })
  }
  else {
    loginHandler()
  }
}
async function loginHandler() {
  loading.value = true
  try {
    await authStore.phoneLogin({
      phone: formData.value.phone,
      code: formData.value.verifyCode,
    })
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
// 清除定时器
onUnmounted(() => {
  if (timer.value) {
    clearInterval(timer.value)
  }
})
</script>

<template>
  <view class="mobile-login">
    <wd-form ref="formRef" :model="formData">
      <view class="login-fields">
        <view class="login-field" :class="{ 'is-focus': focusField === 'phone' }">
          <text class="i-carbon:mobile login-field__icon" :class="{ 'is-active': focusField === 'phone' }" />
          <view class="login-field__control">
            <wd-input
              v-model="formData.phone"
              prop="phone"
              type="number"
              :maxlength="11"
              no-border
              clearable
              custom-style="background: transparent;"
              placeholder-style="color: #a8adba"
              :placeholder="t('login.phonePlaceholder')"
              :rules="[{ required: true, message: t('login.phoneRule') }]"
              @focus="focusField = 'phone'"
              @blur="focusField = ''"
            />
          </view>
        </view>
        <view class="login-field" :class="{ 'is-focus': focusField === 'code' }">
          <text class="i-carbon:two-factor-authentication login-field__icon" :class="{ 'is-active': focusField === 'code' }" />
          <view class="login-field__control">
            <wd-input
              v-model="formData.verifyCode"
              prop="verifyCode"
              type="number"
              :maxlength="6"
              no-border
              custom-style="background: transparent;"
              placeholder-style="color: #a8adba"
              :placeholder="t('login.codePlaceholder')"
              :rules="[{ required: true, message: t('login.codeRule') }]"
              @focus="focusField = 'code'"
              @blur="focusField = ''"
              @confirm="handleLogin"
            />
          </view>
          <view class="login-field__divider" />
          <view
            class="login-code-btn"
            :class="{ 'is-disabled': isDisabled }"
            hover-class="login-code-btn--pressed"
            :hover-stay-time="80"
            @click="getCode"
          >
            <text class="login-code-btn__text">
              {{ isDisabled ? t('login.resendAfter', { seconds: countdown }) : t('login.sendCode') }}
            </text>
          </view>
        </view>
      </view>

      <button
        class="login-submit"
        :class="{ 'is-loading': loading }"
        hover-class="login-submit--pressed"
        :hover-stay-time="80"
        @click="handleLogin"
      >
        <view v-if="loading" class="login-spinner" />
        <text class="login-submit__label">
          {{ t('login.submit') }}
        </text>
      </button>
    </wd-form>
  </view>
</template>

<style scoped lang="scss">
@import './login-controls.scss';
</style>
