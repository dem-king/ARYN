<script setup lang="ts">
import { useAuthStore } from '@/store/authStore'

const props = defineProps<Props>()

const emit = defineEmits<Emits>()

const { confirm } = useGlobalMessage()

interface Props {
  read: boolean
}

interface Emits {
  (e: 'update:read', value: boolean): void
  (e: 'login-success'): void
  (e: 'wx-login'): void
}

const { success: showSuccess, error: showError } = useGlobalToast()
const authStore = useAuthStore()
const loading = ref(false)
// 微信登录
function handleWxLogin() {
  if (!props.read) {
    confirm({
      title: '请阅读并同意用户协议',
      msg: '在继续使用本服务前，请您认真阅读并同意《用户协议》和《隐私政策》。我们将严格保护您的隐私信息，只有在必要的情况下才会收集和使用。点击“我已阅读并同意”即表示您接受相关内容并继续使用服务。',
      closeOnClickModal: false,
      confirmButtonText: '我已阅读并同意',
      cancelButtonText: '取消',
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
// 获取手机号回调（微信小程序）
async function getPhoneNumber(detail: any) {
  const { errMsg, code } = detail || {}
  if (errMsg !== 'getPhoneNumber:ok' || !code) {
    showError('获取手机号失败')
    return
  }

  if (!props.read) {
    confirm({
      title: '请阅读并同意用户协议',
      msg: '在继续使用本服务前，请您认真阅读并同意《用户协议》和《隐私政策》。我们将严格保护您的隐私信息，只有在必要的情况下才会收集和使用。点击“我已阅读并同意”即表示您接受相关内容并继续使用服务。',
      closeOnClickModal: false,
      confirmButtonText: '我已阅读并同意',
      cancelButtonText: '取消',
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
  loading.value = true
  try {
    await authStore.quickLogin({ code })
    showSuccess('登录成功！')
    emit('login-success')
  }
  catch (error: any) {
    showError(error.message || '登录失败')
  }
  finally {
    loading.value = false
  }
}
// 当获取手机号失败时
function onGetPhoneNumberError(event: any) {
  showError(event?.detail?.errMsg || '获取手机号失败')
}

// 支付宝小程序获取手机号回调
async function onAliGetPhoneNumber() {
  // #ifdef MP-ALIPAY
  try {
    const res: any = await new Promise((resolve, reject) => {
      my.getPhoneNumber({
        success: resolve,
        fail: reject,
      })
    })
    const responseStr = res.response
    if (!responseStr) {
      showError('获取手机号失败')
      return
    }
    if (!props.read) {
      confirm({
        title: '请阅读并同意用户协议',
        msg: '在继续使用本服务前，请您认真阅读并同意《用户协议》和《隐私政策》。我们将严格保护您的隐私信息，只有在必要的情况下才会收集和使用。点击"我已阅读并同意"即表示您接受相关内容并继续使用服务。',
        closeOnClickModal: false,
        confirmButtonText: '我已阅读并同意',
        cancelButtonText: '取消',
        success: (res: any) => {
          if (res.action === 'confirm') {
            emit('update:read', true)
            loginHandler(responseStr)
          }
        },
      })
    }
    else {
      loginHandler(responseStr)
    }
  }
  catch (error: any) {
    showError(error?.message || '获取手机号失败')
  }
  // #endif
}
</script>

<template>
  <view class="align-center flex flex-col justify-center">
    <!-- #ifdef MP-WEIXIN -->
    <view class="mb-2 flex justify-center">
      <wd-button type="success" custom-style="width: 80%;" size="large" block @click="handleWxLogin">
        微信登录
      </wd-button>
    </view>
    <!-- #endif -->
    <!-- #ifdef MP-ALIPAY -->
    <view class="mb-2 flex justify-center">
      <wd-button type="primary" custom-style="width: 80%;" size="large" block @click="handleWxLogin">
        支付宝登录
      </wd-button>
    </view>
    <!-- #endif -->
    <!-- #ifdef MP-TOUTIAO -->
    <view class="mb-2 flex justify-center">
      <wd-button type="primary" custom-style="width: 80%;" size="large" block @click="handleWxLogin">
        抖音登录
      </wd-button>
    </view>
    <!-- #endif -->
    <!-- #ifdef MP-WEIXIN -->
    <view class="mb-2 flex justify-center">
      <wd-button
        type="primary" open-type="getPhoneNumber" block size="large" custom-style="width: 80%;"
        :loading="loading" @getphonenumber="getPhoneNumber" @error="onGetPhoneNumberError"
      >
        手机号快速登录
      </wd-button>
    </view>
    <!-- #endif -->
    <!-- #ifdef MP-ALIPAY -->
    <view class="mb-2 flex justify-center">
      <wd-button
        type="primary" open-type="getAuthorize" scope="phoneNumber" block size="large" custom-style="width: 80%;"
        :loading="loading" @getAuthorize="onAliGetPhoneNumber" @error="onGetPhoneNumberError"
      >
        手机号快速登录
      </wd-button>
    </view>
    <!-- #endif -->
  </view>
</template>
