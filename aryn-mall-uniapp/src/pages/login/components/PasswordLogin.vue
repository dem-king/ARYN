<script setup lang="ts">
import { useI18n } from 'vue-i18n'
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
const { confirm } = useGlobalMessage()
const { success: showSuccess, error: showError } = useGlobalToast()
const authStore = useAuthStore()

// 表单数据
const formData = ref({
  phone: '',
  password: '',
})

const focusField = ref<'phone' | 'password' | ''>('')
const formRef = ref()
const loading = ref(false)

// 手机号校验方法
function validatePhone(phone: string): boolean {
  const regex = /^1[3-9]\d{9}$/
  return regex.test(phone)
}

// 登录逻辑
async function handleLogin() {
  if (loading.value || !formRef.value)
    return

  const { valid } = await formRef.value.validate()
  if (!valid) {
    return
  }
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
    await authStore.passwordLogin({
      phone: formData.value.phone,
      password: formData.value.password,
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
</script>

<template>
  <view class="password-login">
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
        <view class="login-field" :class="{ 'is-focus': focusField === 'password' }">
          <text class="i-carbon:locked login-field__icon" :class="{ 'is-active': focusField === 'password' }" />
          <view class="login-field__control">
            <wd-input
              v-model="formData.password"
              prop="password"
              show-password
              :maxlength="32"
              no-border
              custom-style="background: transparent;"
              placeholder-style="color: #a8adba"
              :placeholder="t('login.passwordPlaceholder')"
              :rules="[{ required: true, message: t('login.passwordRule') }]"
              @focus="focusField = 'password'"
              @blur="focusField = ''"
              @confirm="handleLogin"
            />
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
