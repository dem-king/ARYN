<script setup lang="ts">
import { useAuthStore } from '@/store/authStore'
import { sendSmsCode } from '@/api/upms/sms'

interface Props {
  read: boolean
}

interface Emits {
  (e: 'update:read', value: boolean): void
  (e: 'login-success'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const { success: showSuccess, error: showError } = useGlobalToast()
const authStore = useAuthStore()
const { confirm } = useGlobalMessage()

// 表单数据
const formData = ref({
  phone: '',
  verifyCode: '',
})

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
    showError('手机号不能为空')
    return
  }
  if (!validatePhone(phone)) {
    showError('请输入正确的手机号码')
    return
  }
  if (isDisabled.value) {
    showError('请勿重复发送')
    return
  }

  try {
    // 这里调用发送验证码的API
    await sendSmsCode('login', phone)
    showSuccess('验证码已发送')
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
    showError('验证码发送失败')
  }
}

// 登录逻辑
async function handleLogin() {
  if (!formRef.value)
    return

  const { valid } = await formRef.value.validate()
  if (!valid)
    return

  const phone = formData.value.phone
  if (!validatePhone(phone)) {
    showError('请输入正确的手机号码')
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
      <wd-cell-group border>
        <wd-input
          v-model="formData.phone"
          label="手机号"
          label-width="60px"
          prop="phone"
          type="number"
          center
          placeholder="请输入手机号"
          :rules="[{ required: true, message: '请填写手机号' }]"
        />
        <wd-input
          v-model="formData.verifyCode"
          label="验证码"
          label-width="60px"
          prop="verifyCode"
          type="number"
          center
          placeholder="请输入验证码"
          :rules="[{ required: true, message: '请填写验证码' }]"
        >
          <template #suffix>
            <wd-button
              type="text"
              size="small"
              block
              :disabled="isDisabled"
              @click="getCode"
            >
              {{ isDisabled ? `${countdown}秒后重新发送` : '发送验证码' }}
            </wd-button>
          </template>
        </wd-input>
        <wd-cell />
      </wd-cell-group>

      <view class="footer">
        <wd-button
          type="primary"
          block
          size="large"
          :loading="loading"
          @click="handleLogin"
        >
          登录
        </wd-button>
      </view>
    </wd-form>
  </view>
</template>

<style scoped lang="scss">
.mobile-login {
  .footer {
    padding-top: 30rpx;
  }
}
</style>
