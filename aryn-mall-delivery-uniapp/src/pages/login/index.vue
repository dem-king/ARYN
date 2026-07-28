<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useAuthStore } from '@/store/auth'

const authStore = useAuthStore()
const submitting = ref(false)
const form = reactive({ username: '', password: '' })

async function submit() {
  if (!form.username.trim() || !form.password) {
    uni.showToast({ title: '请输入账号和密码', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await authStore.loginAndVerify(form.username.trim(), form.password)
    uni.reLaunch({ url: '/pages/workbench/index' })
  }
  catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '登录失败', icon: 'none' })
  }
  finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="login-page">
    <view class="brand-mark">航</view>
    <text class="eyebrow">AETHERYN DELIVERY</text>
    <text class="title">悦航配送</text>
    <text class="subtitle">员工专用履约工作台</text>

    <view class="login-panel">
      <text class="panel-title">员工登录</text>
      <input v-model="form.username" class="field" placeholder="员工账号" autocomplete="username">
      <input v-model="form.password" class="field" password placeholder="登录密码" autocomplete="current-password">
      <button class="submit" :loading="submitting" :disabled="submitting" @click="submit">
        登录并进入工作台
      </button>
      <text class="hint">仅拥有商城配送权限的员工可登录</text>
    </view>
  </view>
</template>

<style scoped lang="scss">
.login-page {
  box-sizing: border-box;
  min-height: 100vh;
  padding: 132rpx 48rpx 64rpx;
  background:
    radial-gradient(circle at 85% 4%, rgb(54 129 255 / 18%), transparent 34%),
    linear-gradient(180deg, #f8fbff 0%, #edf3fb 100%);
}

.brand-mark {
  display: grid;
  width: 104rpx;
  height: 104rpx;
  margin-bottom: 28rpx;
  color: #fff;
  font-size: 48rpx;
  font-weight: 700;
  background: #2563eb;
  border-radius: 30rpx;
  box-shadow: 0 20rpx 48rpx rgb(37 99 235 / 24%);
  place-items: center;
}

.eyebrow, .title, .subtitle, .panel-title, .hint { display: block; }
.eyebrow { color: #2563eb; font-size: 22rpx; font-weight: 700; letter-spacing: 4rpx; }
.title { margin-top: 12rpx; font-size: 60rpx; font-weight: 760; }
.subtitle { margin-top: 12rpx; color: #64748b; font-size: 28rpx; }

.login-panel {
  margin-top: 72rpx;
  padding: 44rpx 36rpx 36rpx;
  background: rgb(255 255 255 / 92%);
  border: 1px solid rgb(255 255 255 / 80%);
  border-radius: 32rpx;
  box-shadow: 0 24rpx 80rpx rgb(44 67 107 / 10%);
}

.panel-title { margin-bottom: 30rpx; font-size: 34rpx; font-weight: 700; }
.field { box-sizing: border-box; height: 96rpx; margin-top: 20rpx; padding: 0 28rpx; background: #f5f7fb; border: 2rpx solid transparent; border-radius: 20rpx; }
.field:focus { border-color: #2563eb; }
.submit { height: 96rpx; margin-top: 36rpx; color: #fff; font-size: 30rpx; font-weight: 650; line-height: 96rpx; background: #2563eb; border-radius: 20rpx; }
.submit[disabled] { opacity: .7; }
.hint { margin-top: 24rpx; color: #94a3b8; font-size: 24rpx; text-align: center; }
</style>
