<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { flushPendingDistributionShareBinding } from '@/composables/useDistributionShare'
import { Local } from '@/utils/storage'
// 引入组件
import MobileLogin from './components/MobileLogin.vue'
import PasswordLogin from './components/PasswordLogin.vue'
import QuickLogin from './components/QuickLogin.vue'

definePage({
  name: 'login',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '登录',
  },
})
const globalLoading = useGlobalLoading()
const authStore = useAuthStore()
const { show } = useGlobalToast()

// 定义变量
const copyright = ref<any>({
  logoUrl: '/static/logo.png',
  name: '悦航购',
  copyrightDesc: '天启雨数版权所有',
})
const router = useRouter()
const state = reactive<{ loginType: string, read: boolean }>({
  loginType: '1', // 1./微信授权手机号快速登录；2.密码登录；3.手机号验证码登录
  read: false,
})
onLoad(async () => {
  // 非小程序直接设置 loginType
  // #ifndef MP
  state.loginType = '3'
  // #endif
})
// 小程序登录处理
async function handleMpLogin() {
  globalLoading.loading('加载中...')
  try {
    const res = await new Promise<UniApp.LoginRes>((resolve, reject) => {
      uni.login({
        success: resolve,
        fail: reject,
      })
    })

    if (!res.code)
      throw new Error('登录失败: code 未获取')

    const response = await authStore.wxLogin({ jsCode: res.code })
    globalLoading.close()

    if (response.tokenValue) {
      await autoRedirect(700)
    }
  }
  catch (err: any) {
    globalLoading.close()
    show(err?.msg || '登录失败')
  }
}
// 自动登录跳转
async function autoRedirect(delay = 1000) {
  globalLoading.loading('自动登录中...')
  await new Promise(resolve => setTimeout(resolve, delay))
  const redirectPath = Local.get('redirectPath') || '/pages/home/index'
  Local.remove('redirectPath')
  globalLoading.close()
  await flushPendingDistributionShareBinding()
  router.replaceAll({ path: redirectPath })
}
function loginSuccess() {
  flushPendingDistributionShareBinding()
  const redirectPath = Local.get('redirectPath')
  if (redirectPath) {
    Local.remove('redirectPath')
  }
  router.replaceAll({ path: redirectPath || '/pages/home/index' })
}
</script>

<template>
  <view class="h-[100vh] flex flex-col overflow-hidden bg-white">
    <hr-navbar title="登录" />
    <view class="login-container flex-1 bg-white">
      <view class="view-login1 bg-primary" />
      <view class="view-login2 bg-primary" />
      <view class="view-login3 bg-primary" />
      <view class="view-login4 bg-primary" />
      <view class="login-wrap">
        <view class="login-top">
          <image :src="copyright.logoUrl" />
        </view>
        <view class="title">
          <wd-text color="inherit" bold size="40rpx" :text="copyright.name" />
        </view>
        <view class="login-content">
          <!-- 微信快速登录 -->
          <!-- #ifdef MP -->
          <view v-if="state.loginType === '1'">
            <QuickLogin v-model:read="state.read" @login-success="loginSuccess" @wx-login="handleMpLogin" />
          </view>
          <!-- #endif -->
          <!-- 手机号密码登录 -->
          <view v-if="state.loginType === '2'">
            <PasswordLogin v-model:read="state.read" @login-success="loginSuccess" />
          </view>
          <!-- 手机号验证码登录 -->
          <view v-if="state.loginType === '3'">
            <MobileLogin v-model:read="state.read" @login-success="loginSuccess" />
          </view>
        </view>

        <!-- 优化后的其他登录方式分割线 -->
        <view class="other-login-divider">
          <view class="divider-line" />
          <view class="divider-content">
            <text class="divider-text">
              其他登录方式
            </text>
          </view>
          <view class="divider-line" />
        </view>
        <view class="loginType-warp">
          <!-- #ifdef MP -->
          <view class="item" :class="{ hidden: state.loginType === '1' }" @click="state.loginType = '1'">
            <image src="/static/wx.svg" style="width: 100%;height: 100%;" mode="scaleToFill" />
          </view>
          <!-- #endif -->
          <view class="item" :class="{ hidden: state.loginType === '2' }" @click="state.loginType = '2'">
            <image src="/static/pwd.svg" style="width: 100%;height: 100%;" mode="scaleToFill" />
          </view>

          <view class="item" :class="{ hidden: state.loginType === '3' }" @click="state.loginType = '3'">
            <image src="/static/mobile.svg" style="width: 100%;height: 100%;" mode="scaleToFill" />
          </view>
        </view>
      </view>

      <!-- 底部固定区域 - 适配苹果安全区 -->
      <view
        class="fixed bottom-0 left-0 right-0 flex flex-col items-center border-t border-gray-100 bg-white backdrop-blur-md"
        style="padding-bottom: env(safe-area-inset-bottom, 20rpx);"
      >
        <view class="mx-4 mb-2 mt-3 flex items-center justify-center text-center text-xs text-gray-500">
          <wd-checkbox v-model="state.read">
            我已阅读并同意
            <text class="text-primary">
              《用户协议》
            </text>
            <text class="text-primary">
              《隐私政策》
            </text>
          </wd-checkbox>
        </view>
        <view class="pb-3 text-center text-xs text-gray-400">
          {{ copyright.copyrightDesc }}
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.login-container {
  padding: 20rpx;
  box-sizing: border-box;
  border-radius: 10rpx;
  overflow: hidden; // 添加水平方向溢出隐藏
  // #ifdef MP
  height: calc(100vh - var(--window-top) - 44px -  84px - env(safe-area-inset-bottom) - 20rpx);
  // #endif
  // #ifdef H5
  height: calc(100vh - var(--window-top) - 84px - env(safe-area-inset-bottom) - 20rpx);
  // #endif
  position: relative;

  .login-wrap {
    position: absolute;
    left: 0;
    right: 0;
    font-size: 28rpx;
    /* #ifdef H5 */
    top: 80px;
    /* #endif */
    /* #ifdef MP */
    top: 90px;
    /* #endif */
    // 优化后的其他登录方式分割线样式
    .other-login-divider {
      display: flex;
      align-items: center;
      margin: 30rpx 40rpx;
      position: relative;

      .divider-line {
        flex: 1;
        height: 2rpx;
        background: linear-gradient(90deg, transparent 0%, rgba(77, 128, 240, 0.3) 50%, transparent 100%);
      }

      .divider-content {
        display: flex;
        align-items: center;
        gap: 12rpx;
        padding: 0 24rpx;
        backdrop-filter: blur(10rpx);
        position: relative;

        &::before {
          content: '';
          position: absolute;
          top: -2rpx;
          left: -2rpx;
          right: -2rpx;
          bottom: -2rpx;
          border-radius: 50rpx;
          z-index: -1;
        }

        .divider-text {
          font-size: 26rpx;
          color: var(--wot-color-theme);
          font-weight: 500;
          letter-spacing: 1rpx;
        }
      }
    }

    .login-top {
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 10rpx;
      margin-top: 28rpx;

      image {
        width: 180rpx;
        flex: 0 0 180rpx;
        height: 180rpx;
        border-radius: 8rpx;
      }
    }

    .title {
      text-align: center;
      margin-bottom: 40rpx;
    }

    .login-content {
      margin: 10rpx;
      padding: 40rpx;
      border-radius: 20rpx;

      input {
        text-align: left;
        margin-bottom: 10rpx;
        padding-bottom: 6rpx;
      }

      .tips {
        color: #909399;
        margin-bottom: 60rpx;
        margin-top: 8rpx;
      }
    }

    .loginType-warp {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 30rpx;
      margin: 30rpx 0;

      .item {
        width: 36px;
        height: 36px;
        transition: all 0.3s ease;

        &.hidden {
          visibility: hidden; // 保留位置但不显示
        }
      }

    }
  }

  .view-login1 {
    position: absolute;
    /* #ifdef H5 */
    top: -10px;
    /* #endif */
    /* #ifdef MP */
    top: 40px;
    /* #endif */
    left: -18px;
    // background-color: rgba(77, 128, 240, 0.2);
    border-radius: 50%;
    width: 50px;
    height: 50px;
    opacity: 0.6;
  }

  .view-login2 {
    position: absolute;
    /* #ifdef H5 */
    top: 40px;
    /* #endif */
    /* #ifdef MP */
    top: 60px;
    /* #endif */
    right: 10px;
    border-radius: 50%;
    width: 100px;
    height: 100px;
    opacity: 0.1;
    // animation: breathe 3s ease-in-out infinite;
  }

  .view-login3 {
    position: absolute;
    /* #ifdef H5 */
    top: 30px;
    /* #endif */
    /* #ifdef MP */
    top: 50px;
    /* #endif */
    right: -28px;
    border-radius: 50%;
    width: 80px;
    opacity: 0.2;
    height: 80px;
    animation: breathe 3.5s ease-in-out infinite;
  }

  .view-login4 {
    position: absolute;
    /* #ifdef H5 */
    top: 20px;
    /* #endif */
    /* #ifdef MP */
    top: 40px;
    /* #endif */
    right: 37px;
    opacity: 0.2;
    border-radius: 50%;
    width: 40px;
    height: 40px;
    animation: breathe 2s ease-in-out infinite;
  }

  @keyframes breathe {
    0% {
      transform: scale(1);
    }

    50% {
      transform: scale(1.1);
    }

    100% {
      transform: scale(1);
    }
  }
}
</style>
