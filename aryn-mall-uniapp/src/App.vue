<script setup lang="ts">
import { captureDistributionShareParams, flushPendingDistributionShareBinding } from '@/composables/useDistributionShare'
import { refreshBindingOnShow } from '@/api/core/tenant-identity'
import { translate } from '@/locales'
import { useAuthStore } from '@/store/authStore'
import { useLocaleStore } from '@/store/localeStore'
import { MALL_THEME_REFRESH_THROTTLE_MS, useMallThemeStore } from '@/store/mallThemeStore'
import { useUserStore } from '@/store/userStore'

/**
 * 预热用户信息。
 *
 * 路由守卫不再在跳转路径上 `await fetchUserInfo()`（见 router/index.ts）：把网络
 * 请求挡在导航路径上会制造「首次点击没反应」，用户再点一次就产生同 tick 两次路由，
 * 正是微信 `routeDone with a webviewId xxx is not found` 的高发成因。
 * 冷启动只恢复了 token 时由这里补一次；页面 onShow 的补拉继续作为兜底。
 */
function prefetchUserInfo() {
  if (!useAuthStore().isLoggedIn || useUserStore().getUserInfo) {
    return
  }
  useUserStore().fetchUserInfo().catch(() => {})
}

/**
 * 应用启动时的初始化逻辑
 */
onLaunch(async () => {
  useLocaleStore().init()
  // #ifdef MP
  updateManager()
  // #endif
})

onShow((options) => {
  captureDistributionShareParams(options as Record<string, any>)
  flushPendingDistributionShareBinding()
  prefetchUserInfo()
  // 租户构建：回前台按 5 分钟节流重查绑定映射（默认开发 generated=null 直接返回）
  void refreshBindingOnShow()
  // 管理端改色「下次打开生效」的另一半：冷启动由 App.ku.vue 的
  // useMallThemeSync 拉取，这里覆盖后台切回前台的场景（带节流，
  // 避免频繁 onShow 重复请求；失败/无变化静默保持当前配色）
  void useMallThemeStore().refresh({ maxAgeMs: MALL_THEME_REFRESH_THROTTLE_MS })
})

function updateManager() {
  const updateManager = uni.getUpdateManager()
  updateManager.onUpdateReady(() => {
    useGlobalMessage().confirm({
      title: translate('common.updateTitle'),
      msg: translate('common.updateMessage'),
      closeOnClickModal: false,
      confirmButtonText: translate('common.confirm'),
      cancelButtonText: translate('common.cancel'),
      success: (res) => {
        if (res.action === 'confirm') {
          // 新的版本已经下载好，调用 applyUpdate 应用新版本并重启
          updateManager.applyUpdate()
        }
      },
    })
  })
}
</script>

<style lang="scss">
/*  #ifdef  MP  */
@import '@/styles/wd-icon-font.scss';
/*  #endif  */
page, #page {
  background-color: #F8F8F8 !important;
  /**
   * 悬浮玻璃 tabBar 的占位高度：52px 胶囊 + 16px 底边距。
   *
   * 底边距取固定 16px（不叠加安全区）：胶囊仍然落在 iPhone home indicator 上方，
   * 但不会像「安全区 + 间距」那样悬在半空；tab 页的底部让位统一按这个值计算。
   */
  --app-tabbar-height: 68px;

  /**
   * 浅色语义变量：业务页面硬编码色（#fff 卡片、#333/#666/#999 文字等）的统一出处。
   * 深色值见下方 .wot-theme-dark 块；业务页一律引用变量，不再写死色值。
   */
  --app-bg: #f6f7f9;
  --app-card: #ffffff;
  --app-card-2: #f7f8fa;
  --app-card-pressed: #eceef1;
  --app-border: #ebeef0;
  --app-text: #333333;
  --app-text-2: #666666;
  --app-text-3: #999999;
  --app-mask: rgba(0, 0, 0, 0.6);
  --app-skeleton: #f2f3f5;
  --app-skeleton-band: #e9ebef;
}

/**
 * 深色模式 page 根背景：manifest 开启 darkmode 后，theme.json 已把原生窗口
 * 背景切为黑色，但上面的 !important 灰底会盖掉它（回弹/短内容页露灰），
 * 必须在深色媒体查询里跟手。仅小程序开启 darkmode，H5 保持浅色。
 */
/* #ifdef MP-WEIXIN */
@media (prefers-color-scheme: dark) {
  page, #page {
    background-color: #131313 !important;
  }
}
/* #endif */

/**
 * 折叠屏 / 平板展开态全局 token（仅微信小程序，断点 600px 取微信《大屏适配指南》口径）。
 *
 * 为什么需要：rpx 以「窗口宽 = 750rpx」为基准，同一台折叠屏合上 ≈390px 时 1rpx≈0.52px，
 * 展开 ≈700px 时 1rpx≈0.93px——只要铺满展开态窗口，页面里所有 rpx 尺寸都会等比放大
 * ~1.8 倍（正文 28rpx 从 14.6px 变成 26px），这就是「展开像老年模式」的根因。
 * 因此宽屏版式不能用 rpx 拼，统一用平板密度 px + 下列 token；本段只放变量不放业务样式，
 * 各页面的宽屏规则在自身 <style> 的 MP-WEIXIN 段里引用（一律带兜底值）。
 *
 * 条件编译保证 H5 / App 产物不含本段，手机端（<600px）行为零变化。
 */
/* #ifdef MP-WEIXIN */
@media (min-width: 600px) {
  page, #page {
    /* 两栏/多列间距；也是折叠屏折痕安全区，禁止用 rpx（24px 跨折痕不压字） */
    --wide-gutter: 24px;
    /* 宽屏页面左右内边距 */
    --wide-pad: 16px;
    /* 商详顶栏内容高度（state bar 另计），吸顶元素让位用 */
    --wide-nav: 44px;
    /* 宽屏卡片圆角（rpx 圆角在展开态会变成 20px+ 的夸张大圆角） */
    --wide-radius: 12px;
    /* 平板密度字号：卡片标题 13px / 列表价格 15px / 正文 14px / 辅助 12px */
    --wide-card-font: 13px;
    --wide-price-font: 15px;
    --wide-body-font: 14px;
    --wide-meta-font: 12px;
  }
}
/* #endif */

/**
 * 深色语义变量：App.ku.vue 在系统深色时给根节点追加 .dark（激活 UnoCSS 的
 * dark: 工具类）与 wot 自带的 .wot-theme-dark（wot 组件深色态），变量沿
 * 节点树下发到全部业务内容。色板与 wot 深色组件的内置值同灰阶：
 * 页面底 #131313（同 wd-navbar）、卡片 #1b1b1b（同 --wot-dark-background2）。
 */
.wot-theme-dark {
  --app-bg: #131313;
  --app-card: #1b1b1b;
  --app-card-2: #262628;
  --app-card-pressed: #2a2a2e;
  --app-border: #2e2e30;
  --app-text: #f2f2f2;
  --app-text-2: rgba(255, 255, 255, 0.68);
  --app-text-3: rgba(255, 255, 255, 0.42);
  --app-mask: rgba(0, 0, 0, 0.75);
  --app-skeleton: #232326;
  --app-skeleton-band: #2c2c30;
}

/**
 * 悬浮玻璃底栏的等高占位：底栏是 fixed 覆盖层，页面滚动到最底时
 * 最后一行内容会永久压在玻璃下方，补一条等高空白让它能滚到栏上方。
 */
.tabbar-bottom-space {
  height: var(--app-tabbar-height, 0px);
  flex: none;
}

/**
 * H5 内置 tabBar：与顶栏同一套磨砂玻璃材质，整体做成悬浮胶囊。
 *
 * uni-app 把底色与 `backdrop-filter: none` 写在元素的行内样式里，必须 !important
 * 覆盖；0.5px 的原生分隔线由本层的阴影替代，否则玻璃上会多一道深灰线。
 * 52 + 4 = 56px 与 `--app-tabbar-height` 同口径，页面的底部让位按它计算。
 * 小程序端是原生 tabBar（不支持模糊），由自绘组件 glass-tabbar 接管。
 */
/* #ifdef H5 */
uni-tabbar .uni-tabbar {
  left: 50% !important;
  right: auto !important;
  transform: translateX(-50%);
  width: calc(100% - 40px) !important;
  max-width: 480px !important;
  bottom: 16px !important;
  height: 52px !important;
  overflow: hidden;
  border-radius: 26px !important;
  /* 与顶栏磨砂白同一数值（86%），上下两块玻璃看起来才是一套材质 */
  background-color: rgba(255, 255, 255, 0.86) !important;
  /* 大写 WEBKIT 避开 autoprefixer 的过时前缀清理（iOS 17- 的 WKWebView 只认带前缀的写法） */
  -WEBKIT-backdrop-filter: blur(18px) saturate(160%);
  backdrop-filter: blur(18px) saturate(160%) !important;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.16);
}

/* 原生 tab 项按 50px 定高、图标 24px / 文案 10px，统一收到胶囊比例 */
uni-tabbar .uni-tabbar__item,
uni-tabbar .uni-tabbar__bd {
  height: 52px !important;
}

uni-tabbar .uni-tabbar__icon {
  width: 26px !important;
  height: 26px !important;
}

uni-tabbar .uni-tabbar__label {
  font-size: 11px !important;
}

uni-tabbar .uni-tabbar-border {
  display: none !important;
}

/**
 * 底部渐进模糊蒙层：胶囊是悬浮的，两侧与下方会透出滚动内容，用渐隐蒙层兜住。
 * 高度 68px（贴底 16 + 高 52）在胶囊上沿收干净；层级 890 在胶囊(998)之下、
 * 页面弹层(900+)之上。掩码同时作用于背景与 backdrop-filter，模糊由下往上渐弱。
 */
uni-tabbar::before {
  content: '';
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  height: 68px;
  z-index: 890;
  pointer-events: none;
  background-color: rgba(255, 255, 255, 0.42);
  /* 大写 WEBKIT 避开 autoprefixer 的过时前缀清理（iOS 17- 的 WKWebView 只认带前缀的写法） */
  -WEBKIT-backdrop-filter: blur(18px) saturate(160%);
  backdrop-filter: blur(18px) saturate(160%);
  -WEBKIT-mask-image: linear-gradient(to top, rgba(0, 0, 0, 1) 0%, rgba(0, 0, 0, 0.6) 45%, rgba(0, 0, 0, 0) 100%);
  mask-image: linear-gradient(to top, rgba(0, 0, 0, 1) 0%, rgba(0, 0, 0, 0.6) 45%, rgba(0, 0, 0, 0) 100%);
}

/* 深色模式：与 theme.json 的 dark.tabBgColor 同族，不把夜间底栏刷成白玻璃 */
@media (prefers-color-scheme: dark) {
  uni-tabbar .uni-tabbar {
    background-color: rgba(26, 26, 26, 0.86) !important;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.46);
  }

  uni-tabbar::before {
    background-color: rgba(18, 18, 18, 0.5);
  }
}
/* #endif */

/**
 * 自绘玻璃底栏的底部占位（仅小程序）：底栏是 fixed 覆盖层，页面滚动到最底时
 * 最后一行内容会永久压在玻璃下方，补一条等高空白让它能滚到栏上方。
 */
/* #ifdef MP-WEIXIN */
.tabbar-bottom-space {
  height: var(--app-tabbar-height, 0px);
  flex: none;
}
/* #endif */
.page-wrapper {
  min-height: calc(100vh - var(--window-top));
  box-sizing: border-box;
  /* 外壳背景跟随商城默认主题：变量由同节点的 wd-config-provider 下发，
     拿不到时（老基础库/极端时序）回落原灰色。page 根节点取不到子节点
     的 CSS 变量，保持原值——可视区已被本节点盖满 */
  background: var(--wot-color-theme-background, #f2f3f4);
  color: var(--app-text, #333);
}

/* 深色页面壳：装修页内容自带浅色底色仍会盖住此处，业务页则直接继承 */
.wot-theme-dark.page-wrapper {
  background: var(--app-bg, #131313);
  color: var(--app-text, #f2f2f2);
}
</style>
