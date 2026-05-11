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
}

const { success: showSuccess, error: showError } = useGlobalToast()
const authStore = useAuthStore()

// 表单数据
const formData = ref({
  phone: '',
  password: '',
})

const formRef = ref()
const loading = ref(false)

// 手机号校验方法
function validatePhone(phone: string): boolean {
  const regex = /^1[3-9]\d{9}$/
  return regex.test(phone)
}

// 登录逻辑
async function handleLogin() {
  const { valid } = await formRef.value.validate()
  if (!valid) {
    return
  }
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
    await authStore.passwordLogin({
      phone: formData.value.phone,
      password: formData.value.password,
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
</script>

<template>
  <view class="password-login">
    <wd-form ref="formRef" :model="formData">
      <wd-cell-group border>
        <wd-input
          v-model="formData.phone" label="手机号" label-width="60px" prop="phone" type="number" center
          placeholder="请输入手机号" :rules="[{ required: true, message: '请填写手机号' }]"
        />
        <wd-input
          v-model="formData.password" label="密码" label-width="60px" prop="password" show-password center
          placeholder="请输入密码" :rules="[{ required: true, message: '请填写密码' }]"
        />
        <wd-cell />
      </wd-cell-group>

      <view class="footer">
        <wd-button type="primary" block :loading="loading" size="large" @click="handleLogin">
          登录
        </wd-button>
      </view>
    </wd-form>
  </view>
</template>

<style scoped lang="scss">
.password-login {
  .footer {
    padding-top: 30rpx;
  }
}
</style>
