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

/* 深色模式：与 theme.json 的 dark.tabBgColor 同族，不把夜间底栏刷成白玻璃 */
@media (prefers-color-scheme: dark) {
  uni-tabbar .uni-tabbar {
    background-color: rgba(26, 26, 26, 0.86) !important;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.46);
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
