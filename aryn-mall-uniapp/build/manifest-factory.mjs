/**
 * manifest 唯一对象工厂。
 *
 * 完整静态对象从原 manifest.config.ts 迁移而来；默认开发与租户构建共用这一个工厂，
 * 不允许出现第二份 manifest 配置源。工厂是纯模块：不 import uni-manifest、不读文件、
 * 不 watch、不依赖 process.env，输入全部显式传入，每次返回独立对象。
 *
 * src/manifest.json 是生成物（@uni-helper/vite-plugin-uni-manifest 回写）；
 * 租户构建在启动 uni CLI 之前用本工厂预写副本的 src/manifest.json，绕开 CLI 早读缓存。
 */

export const DEFAULT_WX_APP_ID = 'wx0a8242ea59f3e6b4'
export const DEFAULT_APP_NAME = 'aryn-mall-uniapp'

export function createOriginalManifest() {
  return {
    'name': 'aryn-mall-uniapp',
    'appid': '__UNI__2A0AEFA',
    'description': '',
    'versionName': '3.1.0',
    'versionCode': '310',
    'transformPx': false,
    /* 5+App特有相关 */
    'app-plus': {
      usingComponents: true,
      nvueStyleCompiler: 'uni-app',
      compilerVersion: 3,
      splashscreen: {
        alwaysShowBeforeRender: true,
        waiting: true,
        autoclose: true,
        delay: 0,
      },
      /* 模块配置 */
      modules: {},
      /* 应用发布信息 */
      distribute: {
        /* android打包配置 */
        android: {
          permissions: [
            '<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE"/>',
            '<uses-permission android:name="android.permission.MOUNT_UNMOUNT_FILESYSTEMS"/>',
            '<uses-permission android:name="android.permission.VIBRATE"/>',
            '<uses-permission android:name="android.permission.READ_LOGS"/>',
            '<uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>',
            '<uses-feature android:name="android.hardware.camera.autofocus"/>',
            '<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>',
            '<uses-permission android:name="android.permission.CAMERA"/>',
            '<uses-permission android:name="android.permission.GET_ACCOUNTS"/>',
            '<uses-permission android:name="android.permission.READ_PHONE_STATE"/>',
            '<uses-permission android:name="android.permission.CHANGE_WIFI_STATE"/>',
            '<uses-permission android:name="android.permission.WAKE_LOCK"/>',
            '<uses-permission android:name="android.permission.FLASHLIGHT"/>',
            '<uses-feature android:name="android.hardware.camera"/>',
            '<uses-permission android:name="android.permission.WRITE_SETTINGS"/>',
          ],
          minSdkVersion: 21,
        },
        /* ios打包配置 */
        ios: {},
        /* SDK配置 */
        sdkConfigs: {},
      },
    },
    /* 快应用特有相关 */
    'quickapp': {},
    /* 小程序特有相关 */
    'mp-weixin': {
      optimization: {
        subPackages: true,
      },
      appid: DEFAULT_WX_APP_ID,
      setting: {
        urlCheck: true,
        minified: true,
        minifyWXML: true,
        minifyWXSS: true,
      },
      // 组件按需注入
      lazyCodeLoading: 'requiredComponents',
      usingComponents: true,
      // 深色模式：原生导航/tabBar 颜色由 theme.json 的 dark 段跟随系统切换，
      // 页面内容层由 mallThemeStore.mode → App.ku.vue 的 config-provider theme 生效
      darkmode: true,
      themeLocation: 'theme.json',
      // 显式声明小程序根目录，uni 从 manifest 生成 project.config.json 时会原样带上；
      // fixProjectConfig 补丁只作为兼容层，权威校验在构建后的 verifier。
      miniprogramRoot: './',
    },
    'mp-alipay': {
      usingComponents: true,
      compileOptions: {
        globalObjectMode: 'enable',
        treeShaking: true,
      },
    },
    'mp-baidu': {
      usingComponents: true,
    },
    'mp-toutiao': {
      usingComponents: true,
    },
    'h5': {
      // darkmode: true,
      router: {
        mode: 'history',
      },
      // themeLocation: 'theme.json',
    },
    'app-harmony': {
      distribute: {
        bundleName: 'com.aryn.cloud',
      },
    },
    // helper 默认配置携带的平台段；工厂与之对齐，保证默认 dev（helper 合并默认值）
    // 与租户构建（禁用 helper、工厂直写）产物 manifest 结构一致
    'mp-harmony': {
      distribute: {},
    },
    'uniStatistics': {
      enable: false,
    },
    'vueVersion': '3',
  }
}

/**
 * @param {{ name: string, wxAppId: string }} identity
 */
export function createManifest(identity) {
  if (!identity || typeof identity.name !== 'string' || !identity.name.trim()) {
    throw new Error('manifest 工厂缺少应用名称')
  }
  if (typeof identity.wxAppId !== 'string' || !/^wx[0-9a-f]{16}$/.test(identity.wxAppId)) {
    throw new Error(`manifest 工厂收到非法微信 AppID: ${identity.wxAppId}`)
  }
  const manifest = createOriginalManifest()
  manifest.name = identity.name.trim()
  manifest['mp-weixin'].appid = identity.wxAppId
  return manifest
}
