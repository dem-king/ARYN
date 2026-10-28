import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 中英切换入口契约。
 *
 * 语言切换不是「设置页里才有」的功能：外国朋友第一次打开小程序时界面是中文，
 * 必须能在**登录前**就看到切换入口，否则永远找不到设置页。这个契约钉住三处入口
 * 和运行时生效链路，避免后续重构把入口收没或把 tabBar/请求头同步删掉。
 */
const projectRoot = fileURLToPath(new URL('../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

describe('language switch entrypoints', () => {
  it('login page exposes a one-tap switch before sign-in', () => {
    const login = source('src/pages/login/index.vue')
    expect(login).toContain('useLocaleSwitch')
    expect(login).toContain('toggleLanguage')
    // 入口放在自定义导航栏右上角，登录表单渲染前就可见
    expect(login).toContain('<template #right>')
  })

  it('user center and user setting both expose the switch', () => {
    const userCenter = source('src/pages/user/user-center/index.vue')
    expect(userCenter).toContain('useLocaleSwitch')
    expect(userCenter).toContain('toggleLanguage')

    const userSetting = source('src/sub-pages/user/user-setting/index.vue')
    expect(userSetting).toContain('useLocaleSwitch')
    expect(userSetting).toContain('chooseLanguage')
  })

  it('locale store persists the choice and syncs tabBar text', () => {
    const store = source('src/store/localeStore.ts')
    expect(store).toContain('setStoredLocale')
    expect(store).toContain('syncTabBar')
    expect(store).toContain('uni.setTabBarItem')

    // 冷启动即初始化（i18n 实例本身已按存储/系统语言解析，这里同步 tabBar）
    const app = source('src/App.vue')
    expect(app).toContain('useLocaleStore().init()')

    // tab 页 onShow 兜底（非 tab 页调用 setTabBarItem 可能受基础库限制）
    const tabBarLocale = source('src/composables/useTabBarLocale.ts')
    expect(tabBarLocale).toContain('localeStore.init()')
  })

  it('requests carry Accept-Language so backend can follow the UI language', () => {
    const instance = source('src/api/core/instance.ts')
    expect(instance).toContain('Accept-Language')
    expect(instance).toContain('getLocale()')
  })
})
