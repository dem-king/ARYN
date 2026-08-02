<script setup lang="ts">
import { reactive, ref } from 'vue'
import { deliveryLogin } from '@/api/delivery'
import { Local } from '@/utils/storage'

definePage({
  name: 'delivery-login',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '配送员登录',
  },
})

const globalLoading = useGlobalLoading()
const { show: showToast } = useGlobalToast()
const router = useRouter()

const submitting = ref(false)
const form = reactive({
  phone: '',
  password: '',
})

/** 配送员登录 */
async function handleLogin() {
  if (submitting.value)
    return
  // 表单校验
  if (!form.phone) {
    return showToast('请输入手机号')
  }
  if (!/^1\d{10}$/.test(form.phone)) {
    return showToast('请输入正确的手机号')
  }
  if (!form.password) {
    return showToast('请输入密码')
  }

  submitting.value = true
  globalLoading.loading('登录中...')
  try {
    const response: any = await deliveryLogin({ phone: form.phone, password: form.password }).send()
    // 存储配送员token（使用独立的存储key，与普通用户token隔离）
    const token = response?.tokenValue || response?.data?.tokenValue
    if (token) {
      Local.set('deliveryToken', token)
      Local.set('deliveryStaffInfo', response?.staffInfo || response?.data?.staffInfo || {})
      showToast('登录成功')
      // 跳转到配送工作台
      router.replaceAll({ name: 'delivery-index' })
    }
    else {
      showToast(response?.msg || '登录失败')
    }
  }
  catch (error: any) {
    showToast(error?.msg || '登录失败')
  }
  finally {
    submitting.value = false
    globalLoading.close()
  }
}

/** 返回普通用户登录 */
function toUserLogin() {
  router.replaceAll({ name: 'login' })
}
</script>

<template>
  <view class="h-[100vh] flex flex-col overflow-hidden bg-white">
    <hr-navbar title="配送员登录" />
    <view class="flex-1 px-40rpx pt-80rpx">
      <!-- Logo -->
      <view class="mb-60rpx flex flex-col items-center">
        <text class="i-carbon:delivery-truck text-100rpx text-primary" />
        <text class="mt-20rpx text-36rpx font-bold">
          悦航购配送
        </text>
        <text class="mt-10rpx text-24rpx text-gray-400">
          配送员工作台
        </text>
      </view>

      <!-- 登录表单 -->
      <view class="login-form">
        <view class="form-item">
          <text class="i-carbon:phone mr-20rpx text-36rpx text-gray-400" />
          <input
            v-model="form.phone"
            type="number"
            :maxlength="11"
            placeholder="请输入手机号"
            class="flex-1 text-28rpx"
          />
        </view>
        <view class="form-item">
          <text class="i-carbon:locked mr-20rpx text-36rpx text-gray-400" />
          <input
            v-model="form.password"
            :password="true"
            placeholder="请输入密码"
            class="flex-1 text-28rpx"
          />
        </view>

        <wd-button
          type="primary"
          block
          custom-class="mt-60rpx"
          :loading="submitting"
          @click="handleLogin"
        >
          登录
        </wd-button>

        <view class="mt-40rpx text-center" @click="toUserLogin">
          <text class="text-26rpx text-gray-500">
            普通用户登录
          </text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.login-form {
  .form-item {
    display: flex;
    align-items: center;
    padding: 30rpx 20rpx;
    margin-bottom: 20rpx;
    border-bottom: 1px solid #e5e6eb;

    input {
      flex: 1;
      font-size: 28rpx;
    }
  }
}
</style>