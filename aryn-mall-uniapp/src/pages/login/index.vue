<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { flushPendingDistributionShareBinding } from '@/composables/useDistributionShare'
import { useLocaleSwitch } from '@/composables/useLocaleSwitch'
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
    // 页头是品牌渐变，状态栏前景必须转白，否则黑字压在深色渐变上看不清
    navigationBarTextStyle: 'white',
  },
})
const globalLoading = useGlobalLoading()
const authStore = useAuthStore()
const { show } = useGlobalToast()
const { t } = useI18n()
// 登录页右上角语言快捷键：外国朋友在还看不懂界面时也能一键切英文
const { switchLabel, toggleLanguage } = useLocaleSwitch()
const mallThemeStore = useMallThemeStore()

/** 页头渐变与导航栏同源，二者取值必须一致，否则顶部会露出一道色差 */
const BRAND_COLOR = 'var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237))'

/**
 * 主题色是租户可配的，阴影/浅底不能写死品牌红。
 * 在页面根节点下放两份带透明度的品牌色，表单组件按变量取用。
 */
const themeStyle = computed(() => {
  const hex = /^#([0-9a-f]{6})$/i.exec((mallThemeStore.primaryColor || '').trim())?.[1]
    ?? '#FF2237'
  const value = Number.parseInt(hex, 16)
  const r = (value >> 16) & 0xFF
  const g = (value >> 8) & 0xFF
  const b = value & 0xFF
  return {
    '--login-primary-soft': `rgba(${r}, ${g}, ${b}, 0.1)`,
    '--login-primary-glow': `rgba(${r}, ${g}, ${b}, 0.36)`,
  }
})

// 定义变量
const copyright = ref<any>({
  logoUrl: '/static/logo.png',
  name: '',
})
const brandName = computed(() => copyright.value.name || t('common.appName'))
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
  globalLoading.loading(t('common.loading'))
  try {
    const res = await new Promise<UniApp.LoginRes>((resolve, reject) => {
      uni.login({
        success: resolve,
        fail: reject,
      })
    })

    if (!res.code)
      throw new Error(t('login.loginCodeMissing'))

    const response = await authStore.wxLogin({ jsCode: res.code })
    globalLoading.close()

    if (response.tokenValue) {
      await autoRedirect(700)
    }
  }
  catch (err: any) {
    globalLoading.close()
    show(err?.msg || t('login.loginFailed'))
  }
}
// 自动登录跳转
async function autoRedirect(delay = 1000) {
  globalLoading.loading(t('common.autoLoggingIn'))
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
  <view class="login-page" :style="themeStyle">
    <hr-navbar
      :title="''"
      :bordered="false"
      :background-color="BRAND_COLOR"
      text-color="#ffffff"
    >
      <template #right>
        <view
          class="hero-lang"
          hover-class="hero-lang--pressed"
          :hover-stay-time="80"
          :aria-label="switchLabel"
          aria-role="button"
          @click="toggleLanguage"
        >
          <text class="i-carbon:translate hero-lang__icon" />
          <text class="hero-lang__label">
            {{ switchLabel }}
          </text>
        </view>
      </template>
    </hr-navbar>

    <view class="login-hero">
      <view class="hero-glow hero-glow--a" />
      <view class="hero-glow hero-glow--b" />
      <view class="hero-brand">
        <view class="hero-brand__logo">
          <image :src="copyright.logoUrl" mode="aspectFit" />
        </view>
        <text class="hero-brand__name">
          {{ brandName }}
        </text>
        <text class="hero-brand__slogan">
          {{ t('login.brandSlogan') }}
        </text>
      </view>
    </view>

    <view class="login-sheet">
      <view class="sheet-inner">
        <view class="sheet-head">
          <text class="sheet-head__title">
            {{ t('login.welcome') }}
          </text>
          <text class="sheet-head__desc">
            {{ t('login.welcomeDesc') }}
          </text>
        </view>

        <!-- 非小程序：验证码 / 密码两种登录方式用分段控件切换 -->
        <!-- #ifndef MP -->
        <view class="method-switch">
          <view class="method-switch__thumb" :class="{ 'is-right': state.loginType === '2' }" />
          <view
            class="method-switch__item"
            :class="{ 'is-active': state.loginType === '3' }"
            @click="state.loginType = '3'"
          >
            {{ t('login.codeLogin') }}
          </view>
          <view
            class="method-switch__item"
            :class="{ 'is-active': state.loginType === '2' }"
            @click="state.loginType = '2'"
          >
            {{ t('login.passwordLogin') }}
          </view>
        </view>
        <!-- #endif -->

        <view :key="state.loginType" class="login-form">
          <!-- 微信快速登录 -->
          <!-- #ifdef MP -->
          <QuickLogin v-if="state.loginType === '1'" v-model:read="state.read" @login-success="loginSuccess" @wx-login="handleMpLogin" />
          <!-- #endif -->
          <!-- 手机号密码登录 -->
          <PasswordLogin v-if="state.loginType === '2'" v-model:read="state.read" @login-success="loginSuccess" />
          <!-- 手机号验证码登录 -->
          <MobileLogin v-if="state.loginType === '3'" v-model:read="state.read" @login-success="loginSuccess" />
        </view>

        <view
          class="agreement"
          aria-role="checkbox"
          :aria-checked="state.read"
          @click="state.read = !state.read"
        >
          <view class="agreement__box" :class="{ 'is-checked': state.read }">
            <text v-if="state.read" class="i-carbon:checkmark agreement__box-icon" />
          </view>
          <text class="agreement__text">{{ t('login.agreementPrefix') }}<text class="agreement__link">{{ t('login.userAgreement') }}</text>{{ t('login.agreementAnd') }}<text class="agreement__link">{{ t('login.privacyPolicy') }}</text></text>
        </view>

        <!-- 小程序：其他登录方式 -->
        <!-- #ifdef MP -->
        <view class="alt-login">
          <view class="alt-login__divider">
            <view class="alt-login__line" />
            <text class="alt-login__divider-text">
              {{ t('login.otherMethods') }}
            </text>
            <view class="alt-login__line alt-login__line--right" />
          </view>
          <view class="alt-login__list">
            <view
              v-if="state.loginType !== '1'"
              class="alt-login__item"
              hover-class="alt-login__item--pressed"
              @click="state.loginType = '1'"
            >
              <view class="alt-login__icon">
                <image class="alt-login__wx" src="/static/wx2.svg" mode="aspectFit" />
              </view>
              <text class="alt-login__label">
                {{ t('login.wechatLogin') }}
              </text>
            </view>
            <view
              v-if="state.loginType !== '3'"
              class="alt-login__item"
              hover-class="alt-login__item--pressed"
              @click="state.loginType = '3'"
            >
              <view class="alt-login__icon">
                <text class="i-carbon:mobile alt-login__glyph" />
              </view>
              <text class="alt-login__label">
                {{ t('login.codeLogin') }}
              </text>
            </view>
            <view
              v-if="state.loginType !== '2'"
              class="alt-login__item"
              hover-class="alt-login__item--pressed"
              @click="state.loginType = '2'"
            >
              <view class="alt-login__icon">
                <text class="i-carbon:password alt-login__glyph" />
              </view>
              <text class="alt-login__label">
                {{ t('login.passwordLogin') }}
              </text>
            </view>
          </view>
        </view>
        <!-- #endif -->

        <view class="copyright">
          {{ t('login.copyright') }}
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 文字色阶沿用全站约定：标题 #1f2329 / 正文 #4e5969 / 辅助 #9ca3af */
$login-title: #1f2329;
$login-muted: #9ca3af;

.login-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: #fff;
}

/* ===================== 页头 ===================== */

.login-hero {
  position: relative;
  overflow: hidden;
  padding: 8rpx 48rpx 120rpx;
  /* 与导航栏同一条渐变起色，顶部衔接处不露色差 */
  background: linear-gradient(
    180deg,
    var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237)) 0%,
    var(--theme-color-secondary, var(--wot-color-theme-secondary, #FF6B7A)) 100%
  );
}

/* 柔光球用径向渐变模拟模糊，避免部分安卓内核对 filter: blur 支持不稳 */
.hero-glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.hero-glow--a {
  top: -180rpx;
  right: -140rpx;
  width: 520rpx;
  height: 520rpx;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.34) 0%, rgba(255, 255, 255, 0) 68%);
  animation: glow-drift-a 14s ease-in-out infinite alternate;
}

.hero-glow--b {
  bottom: -240rpx;
  left: -180rpx;
  width: 560rpx;
  height: 560rpx;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.22) 0%, rgba(255, 255, 255, 0) 70%);
  animation: glow-drift-b 18s ease-in-out infinite alternate;
}

.hero-brand {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 16rpx;

  &__logo {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 168rpx;
    height: 168rpx;
    padding: 10rpx;
    border-radius: 50%;
    background: #fff;
    box-shadow: 0 24rpx 48rpx -24rpx rgba(17, 24, 39, 0.55);
    box-sizing: border-box;
    animation: brand-pop 0.66s cubic-bezier(0.22, 0.61, 0.36, 1) both;

    image {
      width: 100%;
      height: 100%;
      border-radius: 50%;
    }
  }

  &__name {
    margin-top: 24rpx;
    color: #fff;
    font-size: 44rpx;
    font-weight: 700;
    letter-spacing: 2rpx;
    line-height: 1.2;
    animation: brand-rise 0.5s cubic-bezier(0.22, 0.61, 0.36, 1) 0.12s both;
  }

  &__slogan {
    margin-top: 10rpx;
    color: rgba(255, 255, 255, 0.8);
    font-size: 24rpx;
    letter-spacing: 1rpx;
    line-height: 1.4;
    animation: brand-rise 0.5s cubic-bezier(0.22, 0.61, 0.36, 1) 0.2s both;
  }
}

/* 导航栏右上角语言快捷键：玻璃胶囊，压在渐变上不抢品牌主视觉 */
.hero-lang {
  display: flex;
  align-items: center;
  height: 60rpx;
  /* 导航栏 right 插槽贴右缘定位，补一段留白避免胶囊被屏幕裁掉 */
  margin-right: 24rpx;
  padding: 0 22rpx 0 18rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.48);
  border-radius: 999rpx;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.3) 0%, rgba(255, 255, 255, 0.15) 100%);
  transition: transform 0.18s ease, background 0.18s ease;

  &__icon {
    margin-right: 6rpx;
    color: #fff;
    font-size: 22rpx;
  }

  &__label {
    color: #fff;
    font-size: 22rpx;
    font-weight: 600;
    line-height: 1;
    letter-spacing: 0.4rpx;
  }
}

.hero-lang--pressed {
  transform: scale(0.94);
  background: rgba(255, 255, 255, 0.36);
}

/* ===================== 内容面板 ===================== */

.login-sheet {
  position: relative;
  z-index: 2;
  display: flex;
  flex: 1;
  flex-direction: column;
  /* 上移压住页头收口，页面滚动时内容从品牌色里长出来 */
  margin-top: -56rpx;
  border-radius: 48rpx 48rpx 0 0;
  background: #fff;
  animation: sheet-rise 0.58s cubic-bezier(0.22, 0.61, 0.36, 1) both;
}

.sheet-inner {
  display: flex;
  flex: 1;
  flex-direction: column;
  width: 100%;
  max-width: 560px;
  margin: 0 auto;
  padding: 44rpx 40rpx calc(env(safe-area-inset-bottom) + 32rpx);
  box-sizing: border-box;
}

.sheet-head {
  &__title {
    display: block;
    color: $login-title;
    font-size: 40rpx;
    font-weight: 700;
    line-height: 1.3;
  }

  &__desc {
    display: block;
    margin-top: 8rpx;
    color: $login-muted;
    font-size: 24rpx;
    line-height: 1.4;
  }
}

/* 分段控件：白色滑块随选中项平移，替代原先隐藏图标式的切换 */
.method-switch {
  position: relative;
  display: flex;
  align-items: center;
  height: 84rpx;
  margin-top: 36rpx;
  padding: 8rpx;
  border-radius: 999rpx;
  background: #f3f4f8;
  box-sizing: border-box;

  &__thumb {
    position: absolute;
    top: 8rpx;
    left: 8rpx;
    width: calc(50% - 8rpx);
    height: calc(100% - 16rpx);
    border-radius: 999rpx;
    background: #fff;
    box-shadow: 0 6rpx 18rpx rgba(17, 24, 39, 0.08);
    transition: transform 0.32s cubic-bezier(0.22, 0.61, 0.36, 1);
  }

  &__thumb.is-right {
    transform: translateX(100%);
  }

  &__item {
    position: relative;
    z-index: 1;
    display: flex;
    flex: 1;
    align-items: center;
    justify-content: center;
    height: 100%;
    color: #8a90a0;
    font-size: 28rpx;
    transition: color 0.24s ease;
  }

  &__item.is-active {
    color: $login-title;
    font-weight: 600;
  }
}

.login-form {
  margin-top: 36rpx;
  /* 切换登录方式时组件重挂载，入场动画同步重放 */
  animation: form-rise 0.42s cubic-bezier(0.22, 0.61, 0.36, 1) both;
}

/* ===================== 协议与版权 ===================== */

.agreement {
  display: flex;
  align-items: flex-start;
  margin-top: 36rpx;

  &__box {
    display: flex;
    flex-shrink: 0;
    align-items: center;
    justify-content: center;
    width: 32rpx;
    height: 32rpx;
    margin-top: 4rpx;
    border: 2rpx solid #d3d7df;
    border-radius: 50%;
    background: #fff;
    transition: background-color 0.2s ease, border-color 0.2s ease;
  }

  &__box.is-checked {
    border-color: var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237));
    background: var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237));
  }

  &__box-icon {
    color: #fff;
    font-size: 22rpx;
    animation: check-pop 0.26s cubic-bezier(0.22, 0.61, 0.36, 1) both;
  }

  &__text {
    margin-left: 14rpx;
    color: #8a90a0;
    font-size: 23rpx;
    line-height: 1.55;
  }

  &__link {
    color: var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237));
  }
}

.alt-login {
  margin-top: 44rpx;

  &__divider {
    display: flex;
    align-items: center;
  }

  &__line {
    flex: 1;
    height: 2rpx;
    background: linear-gradient(90deg, rgba(31, 35, 41, 0) 0%, rgba(31, 35, 41, 0.09) 100%);
  }

  &__line--right {
    background: linear-gradient(90deg, rgba(31, 35, 41, 0.09) 0%, rgba(31, 35, 41, 0) 100%);
  }

  &__divider-text {
    margin: 0 20rpx;
    color: #b0b5bf;
    font-size: 22rpx;
    line-height: 1.4;
  }

  &__list {
    display: flex;
    justify-content: center;
    gap: 64rpx;
    margin-top: 28rpx;
  }

  &__item {
    display: flex;
    flex-direction: column;
    align-items: center;
    transition: transform 0.16s ease, opacity 0.16s ease;
  }

  &__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 88rpx;
    height: 88rpx;
    border-radius: 50%;
    background: #f6f7fa;
  }

  &__wx {
    width: 56rpx;
    height: 56rpx;
  }

  &__glyph {
    color: #4e5969;
    font-size: 38rpx;
  }

  &__label {
    margin-top: 12rpx;
    color: #8a90a0;
    font-size: 22rpx;
    line-height: 1.3;
  }
}

.alt-login__item--pressed {
  transform: scale(0.94);
  opacity: 0.8;
}

.copyright {
  margin-top: auto;
  padding-top: 56rpx;
  color: #c2c6cf;
  font-size: 20rpx;
  letter-spacing: 0.5rpx;
  text-align: center;
}

/* ===================== 动效 ===================== */

@keyframes brand-pop {
  0% {
    opacity: 0;
    transform: scale(0.7);
  }

  60% {
    opacity: 1;
    transform: scale(1.05);
  }

  100% {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes brand-rise {
  from {
    opacity: 0;
    transform: translateY(20rpx);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes sheet-rise {
  from {
    opacity: 0;
    transform: translateY(56rpx);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes form-rise {
  from {
    opacity: 0;
    transform: translateY(24rpx);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes check-pop {
  0% {
    opacity: 0;
    transform: scale(0.4);
  }

  100% {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes glow-drift-a {
  from {
    transform: translate3d(0, 0, 0) scale(1);
  }

  to {
    transform: translate3d(-40rpx, 32rpx, 0) scale(1.08);
  }
}

@keyframes glow-drift-b {
  from {
    transform: translate3d(0, 0, 0) scale(1);
  }

  to {
    transform: translate3d(36rpx, -28rpx, 0) scale(1.06);
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero-glow,
  .hero-brand__logo,
  .hero-brand__name,
  .hero-brand__slogan,
  .login-sheet,
  .login-form {
    animation: none;
  }
}
</style>
