# 主题与布局配置系统 — 完整实现文档

> 本文档提供 `aryn-mall-ui` 项目中主题切换与布局配置功能的**完整源码级实现细节**，可直接用于在其他项目中复刻一模一样的功能。

---

## 目录

1. [系统架构总览](#一系统架构总览)
2. [类型系统](#二类型系统)
3. [核心状态管理](#三核心状态管理)
4. [CSS 设计令牌](#四css-设计令牌)
5. [颜色工具系统](#五颜色工具系统)
6. [布局组件系统](#六布局组件系统)
7. [UI 交互组件](#七ui-交互组件)
8. [初始化与使用](#八初始化与使用)
9. [完整文件清单](#九完整文件清单)

---

## 一、系统架构总览

### 1.1 模块依赖图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              应用层 (Apps)                               │
│                         App.vue / 页面组件                                │
│                              ↓ 使用                                       │
├─────────────────────────────────────────────────────────────────────────┤
│                          布局效果层 (Effects)                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────┐  │
│  │  layout.vue     │  │  header.vue     │  │  preferences-drawer.vue │  │
│  │  (布局总装)      │  │  (顶部栏)        │  │  (偏好设置抽屉)          │  │
│  └────────┬────────┘  └─────────────────┘  └─────────────────────────┘  │
│           ↓ 依赖                                                          │
├─────────────────────────────────────────────────────────────────────────┤
│                          UI 组件层 (UI Kit)                               │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────┐  │
│  │  VbenLayout     │  │  LayoutSidebar  │  │  LayoutHeader           │  │
│  │  (布局容器)      │  │  (侧边栏)        │  │  (头部容器)              │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────┘  │
│           ↑ 依赖                                                          │
├─────────────────────────────────────────────────────────────────────────┤
│                         偏好设置层 (Preferences)                          │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │  PreferenceManager (Singleton)                                      │ │
│  │  ├── reactive state                                                 │ │
│  │  ├── localStorage 持久化                                            │ │
│  │  ├── 系统主题监听 (prefers-color-scheme)                            │ │
│  │  └── CSS 变量同步 (updateCSSVariables)                              │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│           ↑ 依赖                                                          │
├─────────────────────────────────────────────────────────────────────────┤
│                          基础工具层 (Base)                                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐ │
│  │ StorageMgr  │  │ Color Utils │  │ Merge/Diff  │  │ CSS Var Updater │ │
│  │ (存储管理)   │  │ (颜色生成)   │  │ (对象工具)   │  │ (CSS变量更新)    │ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.2 数据流

```
用户操作 → updatePreferences() → PreferenceManager.state (reactive)
                                          ↓
                                    ┌─────────────┐
                                    │ 1. 合并更新   │
                                    │ 2. 触发 watch │
                                    │ 3. 保存缓存   │
                                    └──────┬──────┘
                                           ↓
                              ┌────────────────────────┐
                              │ updateCSSVariables()   │
                              │ ├─ html.classList      │
                              │ ├─ html.dataset.theme  │
                              │ └─ document.style.set  │
                              └───────────┬────────────┘
                                          ↓
                              CSS Variables 自动生效
                              所有使用 var(--xxx) 的组件响应
```

---

## 二、类型系统

### 2.1 核心类型定义

**文件：`packages/@core/base/typings/src/app.d.ts`**

```typescript
type BuiltinThemeType =
  | 'custom'
  | 'deep-blue'
  | 'deep-green'
  | 'default'
  | 'gray'
  | 'green'
  | 'neutral'
  | 'orange'
  | 'pink'
  | 'rose'
  | 'sky-blue'
  | 'slate'
  | 'violet'
  | 'yellow'
  | 'zinc';

type ContentCompactType = 'compact' | 'wide';

type LayoutHeaderModeType = 'auto' | 'auto-scroll' | 'fixed' | 'static';

type LayoutType =
  | 'full-content'
  | 'header-mixed-nav'
  | 'header-nav'
  | 'header-sidebar-nav'
  | 'mixed-nav'
  | 'sidebar-mixed-nav'
  | 'sidebar-nav';

type NavigationStyleType = 'plain' | 'rounded';

type ThemeModeType = 'auto' | 'dark' | 'light';
```

### 2.2 偏好设置类型

**文件：`packages/@core/preferences/src/types.ts`**

```typescript
import type {
  AppPreferences,
  BreadcrumbPreferences,
  FooterPreferences,
  HeaderPreferences,
  LogoPreferences,
  NavigationPreferences,
  Preferences,
  SidebarPreferences,
  TabbarPreferences,
  ThemePreferences,
  TransitionPreferences,
  WidgetPreferences,
} from '@vben-core/typings';

export type { Preferences };

export type { AppPreferences };
export type { BreadcrumbPreferences };
export type { FooterPreferences };
export type { HeaderPreferences };
export type { LogoPreferences };
export type { NavigationPreferences };
export type { SidebarPreferences };
export type { TabbarPreferences };
export type { ThemePreferences };
export type { TransitionPreferences };
export type { WidgetPreferences };

export type DeepPartial<T> = {
  [P in keyof T]?: T[P] extends object ? DeepPartial<T[P]> : T[P];
};

export interface InitialOptions {
  namespace: string;
  overrides?: DeepPartial<Preferences>;
}
```

### 2.3 完整 Preferences 接口

```typescript
interface Preferences {
  app: AppPreferences;
  breadcrumb: BreadcrumbPreferences;
  copyright: any;
  footer: FooterPreferences;
  header: HeaderPreferences;
  logo: LogoPreferences;
  navigation: NavigationPreferences;
  shortcutKeys: any;
  sidebar: SidebarPreferences;
  tabbar: TabbarPreferences;
  theme: ThemePreferences;
  transition: TransitionPreferences;
  widget: WidgetPreferences;
}
```

---

## 三、核心状态管理

### 3.1 偏好设置管理器 (PreferenceManager)

**文件：`packages/@core/preferences/src/preferences.ts`**

这是整个系统的**核心中枢**，采用单例模式实现：

```typescript
import type { DeepPartial, InitialOptions, Preferences } from '@vben-core/typings';

import { markRaw, reactive, readonly, watch } from 'vue';
import { useBreakpoints, useDebounceFn } from '@vueuse/core';

import { defaultPreferences } from './config';
import { StorageManager } from '@vben-core/shared/cache';
import { diff, merge } from '@vben-core/shared/utils';
import { updateCSSVariables } from './update-css-variables';

const STORAGE_KEY = 'preferences';
const STORAGE_KEY_LOCALE = `${STORAGE_KEY}-locale`;
const STORAGE_KEY_THEME = `${STORAGE_KEY}-theme`;

class PreferenceManager {
  private cache: null | StorageManager = null;
  private initialPreferences: Preferences = defaultPreferences;
  private isInitialized: boolean = false;
  private savePreferences: (preference: Preferences) => void;
  private state: Preferences = reactive<Preferences>({
    ...this.loadPreferences(),
  });

  constructor() {
    this.cache = new StorageManager();
    this.savePreferences = useDebounceFn(
      (preference: Preferences) => this._savePreferences(preference),
      150,
    );
  }

  clearCache() {
    [STORAGE_KEY, STORAGE_KEY_LOCALE, STORAGE_KEY_THEME].forEach((key) => {
      this.cache?.removeItem(key);
    });
  }

  getInitialPreferences() {
    return this.initialPreferences;
  }

  getPreferences() {
    return readonly(this.state);
  }

  async initPreferences({ namespace, overrides }: InitialOptions) {
    if (this.isInitialized) return;

    this.cache = new StorageManager({ prefix: namespace });
    this.initialPreferences = merge({}, overrides, defaultPreferences);

    const mergedPreference = merge(
      {},
      this.loadCachedPreferences() || {},
      this.initialPreferences,
    );

    this.updatePreferences(mergedPreference);
    this.setupWatcher();
    this.initPlatform();
    this.isInitialized = true;
  }

  resetPreferences() {
    Object.assign(this.state, this.initialPreferences);
    this.savePreferences(this.state);
    [STORAGE_KEY, STORAGE_KEY_THEME, STORAGE_KEY_LOCALE].forEach((key) => {
      this.cache?.removeItem(key);
    });
    this.updatePreferences(this.state);
  }

  updatePreferences(updates: DeepPartial<Preferences>) {
    const mergedState = merge({}, updates, markRaw(this.state));
    Object.assign(this.state, mergedState);
    this.handleUpdates(updates);
    this.savePreferences(this.state);
  }

  private _savePreferences(preference: Preferences) {
    this.cache?.setItem(STORAGE_KEY, preference);
    this.cache?.setItem(STORAGE_KEY_LOCALE, preference.app.locale);
    this.cache?.setItem(STORAGE_KEY_THEME, preference.theme.mode);
  }

  private handleUpdates(updates: DeepPartial<Preferences>) {
    const themeUpdates = updates.theme || {};
    const appUpdates = updates.app || {};

    if (themeUpdates && Object.keys(themeUpdates).length > 0) {
      updateCSSVariables(this.state);
    }

    if (
      Reflect.has(appUpdates, 'colorGrayMode') ||
      Reflect.has(appUpdates, 'colorWeakMode')
    ) {
      this.updateColorMode(this.state);
    }
  }

  private initPlatform() {
    const dom = document.documentElement;
    const isMac = navigator.platform.toUpperCase().indexOf('MAC') >= 0;
    dom.dataset.platform = isMac ? 'macOs' : 'window';
  }

  private loadCachedPreferences() {
    return this.cache?.getItem<Preferences>(STORAGE_KEY);
  }

  private loadPreferences(): Preferences {
    return this.loadCachedPreferences() || { ...defaultPreferences };
  }

  private setupWatcher() {
    if (this.isInitialized) return;

    // 监听断点
    const breakpoints = useBreakpoints({ md: 768 });
    const isMobile = breakpoints.smaller('md');
    watch(
      () => isMobile.value,
      (val) => {
        this.updatePreferences({
          app: { isMobile: val },
        });
      },
      { immediate: true },
    );

    // 监听系统主题
    window
      .matchMedia('(prefers-color-scheme: dark)')
      .addEventListener('change', ({ matches: isDark }) => {
        if (this.state.theme.mode === 'auto') {
          this.updatePreferences({
            theme: { mode: isDark ? 'dark' : 'light' },
          });
          this.updatePreferences({
            theme: { mode: 'auto' },
          });
        }
      });
  }

  private updateColorMode(preference: Preferences) {
    if (preference.app) {
      const { colorGrayMode, colorWeakMode } = preference.app;
      const dom = document.documentElement;
      const COLOR_WEAK = 'invert-mode';
      const COLOR_GRAY = 'grayscale-mode';
      colorWeakMode
        ? dom.classList.add(COLOR_WEAK)
        : dom.classList.remove(COLOR_WEAK);
      colorGrayMode
        ? dom.classList.add(COLOR_GRAY)
        : dom.classList.remove(COLOR_GRAY);
    }
  }
}

const preferencesManager = new PreferenceManager();

export { PreferenceManager, preferencesManager };
```

### 3.2 组合式函数

**文件：`packages/@core/preferences/src/use-preferences.ts`**

```typescript
import { computed } from 'vue';

import { diff } from '@vben-core/shared/utils';

import { preferencesManager } from './preferences';
import { isDarkTheme } from './update-css-variables';

export function usePreferences() {
  const preferences = preferencesManager.getPreferences();
  const initialPreferences = preferencesManager.getInitialPreferences();

  const diffPreference = computed(() => {
    return diff(initialPreferences, preferences);
  });

  const appPreferences = computed(() => preferences.app);
  const isDark = computed(() => isDarkTheme(preferences.theme.mode));
  const locale = computed(() => preferences.app.locale);
  const isMobile = computed(() => appPreferences.value.isMobile);
  const theme = computed(() => (isDark.value ? 'dark' : 'light'));

  const layout = computed(() =>
    isMobile.value ? 'sidebar-nav' : appPreferences.value.layout,
  );

  const isShowHeaderNav = computed(() => preferences.header.enable);
  const isFullContent = computed(() => appPreferences.value.layout === 'full-content');
  const isSideNav = computed(() => appPreferences.value.layout === 'sidebar-nav');
  const isHeaderNav = computed(() => appPreferences.value.layout === 'header-nav');
  const isMixedNav = computed(() => appPreferences.value.layout === 'mixed-nav');
  const isSideMode = computed(() =>
    isMixedNav.value || isSideNav.value || appPreferences.value.layout === 'sidebar-mixed-nav',
  );
  const sidebarCollapsed = computed(() => preferences.sidebar.collapsed);
  const keepAlive = computed(() => preferences.tabbar.enable && preferences.tabbar.keepAlive);

  return {
    diffPreference,
    appPreferences,
    isDark,
    locale,
    isMobile,
    theme,
    layout,
    isShowHeaderNav,
    isFullContent,
    isSideNav,
    isHeaderNav,
    isMixedNav,
    isSideMode,
    sidebarCollapsed,
    keepAlive,
  };
}
```

### 3.3 导出入口

**文件：`packages/@core/preferences/src/index.ts`**

```typescript
import { preferencesManager } from './preferences';

export const preferences = preferencesManager.getPreferences();
export const updatePreferences = preferencesManager.updatePreferences.bind(preferencesManager);
export const resetPreferences = preferencesManager.resetPreferences.bind(preferencesManager);
export const clearPreferencesCache = preferencesManager.clearCache.bind(preferencesManager);
export const initPreferences = preferencesManager.initPreferences.bind(preferencesManager);
export { preferencesManager };
export * from './use-preferences';
export * from './config';
export * from './constants';
export * from './types';
export * from './update-css-variables';
```

### 3.4 默认配置

**文件：`packages/@core/preferences/src/config.ts`**

```typescript
import type { Preferences } from '@vben-core/typings';

export const defaultPreferences: Preferences = {
  app: {
    accessMode: 'frontend',
    authPageLayout: 'panel-right',
    checkUpdatesInterval: 1,
    colorGrayMode: false,
    colorWeakMode: false,
    compact: false,
    contentCompact: 'wide',
    contentCompactWidth: 1200,
    contentPadding: 0,
    contentPaddingBottom: 0,
    contentPaddingLeft: 0,
    contentPaddingRight: 0,
    contentPaddingTop: 0,
    defaultAvatar: '',
    defaultHomePath: '/',
    dynamicTitle: true,
    enableCheckUpdates: true,
    enablePreferences: true,
    enableRefreshToken: false,
    isMobile: false,
    layout: 'sidebar-nav',
    locale: 'zh-CN',
    loginExpiredMode: 'page',
    name: 'Vben Admin',
    preferencesButtonPosition: 'auto',
    watermark: false,
    zIndex: 200,
  },
  breadcrumb: {
    enable: true,
    hideOnlyOne: false,
    showHome: false,
    showIcon: true,
    styleType: 'normal',
  },
  copyright: {
    companyName: '',
    companySiteLink: '',
    date: '2024',
    enable: true,
    icp: '',
    icpLink: '',
    settingShow: true,
  },
  footer: {
    enable: false,
    fixed: false,
    height: 32,
  },
  header: {
    enable: true,
    height: 50,
    hidden: false,
    menuAlign: 'start',
    mode: 'fixed',
  },
  logo: {
    enable: true,
    fit: 'contain',
    source: '',
  },
  navigation: {
    accordion: true,
    split: true,
    styleType: 'rounded',
  },
  shortcutKeys: {
    enable: true,
    globalLockScreen: true,
    globalLogout: true,
    globalPreferences: true,
    globalSearch: true,
  },
  sidebar: {
    autoActivateChild: false,
    collapsed: false,
    collapsedButton: true,
    collapsedShowTitle: false,
    collapseWidth: 60,
    enable: true,
    expandOnHover: true,
    extraCollapse: false,
    extraCollapsedWidth: 60,
    fixedButton: true,
    hidden: false,
    mixedWidth: 80,
    width: 224,
  },
  tabbar: {
    draggable: true,
    enable: true,
    height: 38,
    keepAlive: true,
    maxCount: 0,
    middleClickToClose: false,
    persist: true,
    showIcon: true,
    showMaximize: true,
    showMore: true,
    styleType: 'chrome',
    wheelable: true,
  },
  theme: {
    builtinType: 'default',
    colorDestructive: 'hsl(348 100% 61%)',
    colorPrimary: 'hsl(212 100% 45%)',
    colorSuccess: 'hsl(144 57% 58%)',
    colorWarning: 'hsl(42 84% 61%)',
    mode: 'light',
    radius: '0.5',
    semiDarkHeader: false,
    semiDarkSidebar: false,
  },
  transition: {
    enable: true,
    loading: true,
    name: 'fade-slide',
    progress: true,
  },
  widget: {
    fullscreen: true,
    globalSearch: true,
    languageToggle: true,
    lockScreen: true,
    notification: true,
    refresh: true,
    sidebarToggle: true,
    themeToggle: true,
  },
};
```

### 3.5 主题预设常量

**文件：`packages/@core/preferences/src/constants.ts`**

```typescript
import type { BuiltinThemePreset } from './types';

export const BUILT_IN_THEME_PRESETS: BuiltinThemePreset[] = [
  { color: 'hsl(212 100% 45%)', type: 'default' },
  { color: 'hsl(245 82% 67%)', type: 'violet' },
  { color: 'hsl(347 77% 60%)', type: 'pink' },
  { color: 'hsl(42 84% 61%)', type: 'yellow' },
  { color: 'hsl(231 98% 65%)', type: 'sky-blue' },
  { color: 'hsl(161 90% 43%)', type: 'green' },
  { color: 'hsl(240 5% 26%)', darkPrimaryColor: 'hsl(0 0% 98%)', primaryColor: 'hsl(240 5.9% 10%)', type: 'zinc' },
  { color: 'hsl(181 84% 32%)', type: 'deep-green' },
  { color: 'hsl(211 91% 39%)', type: 'deep-blue' },
  { color: 'hsl(18 89% 40%)', type: 'orange' },
  { color: 'hsl(0 75% 42%)', type: 'rose' },
  { color: 'hsl(0 0% 25%)', darkPrimaryColor: 'hsl(0 0% 98%)', primaryColor: 'hsl(240 5.9% 10%)', type: 'neutral' },
  { color: 'hsl(215 25% 27%)', darkPrimaryColor: 'hsl(0 0% 98%)', primaryColor: 'hsl(240 5.9% 10%)', type: 'slate' },
  { color: 'hsl(217 19% 27%)', darkPrimaryColor: 'hsl(0 0% 98%)', primaryColor: 'hsl(240 5.9% 10%)', type: 'gray' },
  { color: '', type: 'custom' },
];

export const COLOR_PRESETS = BUILT_IN_THEME_PRESETS.slice(0, 7);
```

---

## 四、CSS 设计令牌

### 4.1 默认亮色主题

**文件：`packages/@core/base/design/src/design-tokens/default.css`**

```css
@layer base {
  :root {
    --popup-z-index: 2000;
    --font-family:
      -apple-system, blinkmacsystemfont, 'Segoe UI', roboto, 'Helvetica Neue',
      arial, 'Noto Sans', sans-serif;
    --background: 0 0% 100%;
    --background-deep: 216 20.11% 95.47%;
    --foreground: 210 6% 21%;
    --card: 0 0% 100%;
    --card-foreground: 222.2 84% 4.9%;
    --popover: 0 0% 100%;
    --popover-foreground: 222.2 84% 4.9%;
    --primary: 212 100% 45%;
    --primary-foreground: 0 0% 98%;
    --secondary: 240 5% 96%;
    --secondary-foreground: 240 6% 10%;
    --muted: 240 4.8% 95.9%;
    --muted-foreground: 240 3.8% 46.1%;
    --accent: 240 5% 96%;
    --accent-dark: 216 14% 93%;
    --accent-darker: 216 11% 91%;
    --accent-lighter: 240 0% 98%;
    --accent-hover: 200deg 10% 90%;
    --accent-foreground: 240 6% 10%;
    --destructive: 359.33 100% 65.1%;
    --destructive-foreground: 0 0% 98%;
    --info: 240, 5%, 96%;
    --info-foreground: 220, 4%, 58%;
    --success: 144 57% 58%;
    --success-foreground: 0 0% 98%;
    --warning: 42 84% 61%;
    --warning-foreground: 0 0% 98%;
    --border: 240 5.9% 90%;
    --input: 240deg 5.88% 90%;
    --input-placeholder: 217 10.6% 65%;
    --input-background: 0 0% 100%;
    --ring: 222.2 84% 4.9%;
    --radius: 0.5rem;
    --overlay: 0 0% 0% / 45%;
    --overlay-content: 0 0% 95% / 45%;
    --font-size-base: 16px;
    --sidebar: 0 0% 100%;
    --sidebar-deep: 0 0% 100%;
    --menu: var(--sidebar);
    --header: 0 0% 100%;

    accent-color: var(--primary);
    color-scheme: light;
  }
}
```

### 4.2 暗黑主题

**文件：`packages/@core/base/design/src/design-tokens/dark.css`**

```css
@layer base {
  .dark {
    --background: 222.34deg 10.43% 12.27%;
    --background-deep: 220deg 13.06% 9%;
    --foreground: 0 0% 95%;
    --card: 222.34deg 10.43% 12.27%;
    --card-foreground: 210 40% 98%;
    --popover: 0 0% 14.2%;
    --popover-foreground: 210 40% 98%;
    --primary-foreground: 0 0% 98%;
    --secondary: 240 5% 17%;
    --secondary-foreground: 0 0% 98%;
    --muted: 240 3.7% 15.9%;
    --muted-foreground: 240 5% 64.9%;
    --accent: 216 5% 19%;
    --accent-dark: 240 0% 22%;
    --accent-darker: 240 0% 26%;
    --accent-lighter: 216 5% 12%;
    --accent-hover: 216 5% 24%;
    --accent-foreground: 0 0% 98%;
    --heavy: 216 5% 24%;
    --heavy-foreground: var(--accent-foreground);
    --destructive: 359.21 68.47% 56.47%;
    --destructive-foreground: 0 0% 98%;
    --info: 180, 1.54%, 12.75%;
    --info-foreground: 220, 4%, 58%;
    --success: 144 57% 58%;
    --success-foreground: 0 0% 98%;
    --warning: 42 84% 61%;
    --warning-foreground: 0 0% 98%;
    --border: 240 3.7% 22%;
    --input: 0deg 0% 100% / 10%;
    --input-placeholder: 218deg 11% 65%;
    --input-background: 0deg 0% 100% / 5%;
    --ring: 222.2 84% 4.9%;
    --overlay: 0deg 0% 0% / 40%;
    --overlay-content: 0deg 0% 0% / 40%;
    --sidebar: 222.34deg 10.43% 12.27%;
    --sidebar-deep: 220deg 13.06% 9%;
    --header: 222.34deg 10.43% 12.27%;

    color-scheme: dark;
  }
}
```

### 4.3 CSS 变量更新逻辑

**文件：`packages/@core/preferences/src/update-css-variables.ts`**

```typescript
import type { Preferences } from '@vben-core/typings';

import { BUILT_IN_THEME_PRESETS } from './constants';
import { generatorColorVariables, updateCSSVariables as executeUpdateCSSVariables } from '@vben-core/shared';

export function isDarkTheme(theme: string) {
  let dark = theme === 'dark';
  if (theme === 'auto') {
    dark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  }
  return dark;
}

export function updateCSSVariables(preferences: Preferences) {
  const root = document.documentElement;
  if (!root) return;

  const theme = preferences?.theme ?? {};
  const { builtinType, mode, radius } = theme;

  // 切换 dark/light 类
  if (Reflect.has(theme, 'mode')) {
    const dark = isDarkTheme(mode);
    root.classList.toggle('dark', dark);
  }

  // 设置 data-theme
  if (Reflect.has(theme, 'builtinType')) {
    const rootTheme = root.dataset.theme;
    if (rootTheme !== builtinType) {
      root.dataset.theme = builtinType;
    }
  }

  // 获取当前内置主题
  const currentBuiltType = BUILT_IN_THEME_PRESETS.find(
    (item) => item.type === builtinType,
  );

  let builtinTypeColorPrimary: string | undefined = '';

  if (currentBuiltType) {
    const isDark = isDarkTheme(preferences.theme.mode);
    const color = isDark
      ? currentBuiltType.darkPrimaryColor || currentBuiltType.primaryColor
      : currentBuiltType.primaryColor;
    builtinTypeColorPrimary = color || currentBuiltType.color;
  }

  // 更新主色调
  if (
    builtinTypeColorPrimary ||
    Reflect.has(theme, 'colorPrimary') ||
    Reflect.has(theme, 'colorDestructive') ||
    Reflect.has(theme, 'colorSuccess') ||
    Reflect.has(theme, 'colorWarning')
  ) {
    updateMainColorVariables(preferences);
  }

  // 更新圆角
  if (Reflect.has(theme, 'radius')) {
    document.documentElement.style.setProperty('--radius', `${radius}rem`);
  }
}

function updateMainColorVariables(preference: Preferences) {
  if (!preference.theme) return;

  const { colorDestructive, colorPrimary, colorSuccess, colorWarning } =
    preference.theme;

  const colorVariables = generatorColorVariables([
    { color: colorPrimary, name: 'primary' },
    { alias: 'warning', color: colorWarning, name: 'yellow' },
    { alias: 'success', color: colorSuccess, name: 'green' },
    { alias: 'destructive', color: colorDestructive, name: 'red' },
  ]);

  const colorMappings = {
    '--green-500': '--success',
    '--primary-500': '--primary',
    '--red-500': '--destructive',
    '--yellow-500': '--warning',
  };

  Object.entries(colorMappings).forEach(([sourceVar, targetVar]) => {
    const colorValue = colorVariables[sourceVar];
    if (colorValue) {
      document.documentElement.style.setProperty(targetVar, colorValue);
    }
  });

  executeUpdateCSSVariables(colorVariables);
}
```

---

## 五、颜色工具系统

### 5.1 颜色转换

**文件：`packages/@core/base/shared/src/color/convert.ts`**

```typescript
import { TinyColor } from '@ctrl/tinycolor';

export function convertToHsl(color: string) {
  const { a, h, l, s } = new TinyColor(color).toHsl();
  const hsl = `${Math.round(h)} ${Math.round(s * 100)}% ${Math.round(l * 100)}%`;
  return a < 1 ? `${hsl} / ${a}` : hsl;
}

export function convertToHslCssVar(color: string) {
  const { a, h, l, s } = new TinyColor(color).toHsl();
  const hsl = `${Math.round(h)} ${Math.round(s * 100)}% ${Math.round(l * 100)}%`;
  return a < 1 ? `${hsl} / ${a}` : hsl;
}

export { TinyColor };
```

### 5.2 颜色生成器

**文件：`packages/@core/base/shared/src/color/generator.ts`**

```typescript
import { getColors } from 'theme-colors';
import { TinyColor } from '@ctrl/tinycolor';
import { convertToHslCssVar } from './convert';

interface ColorItem {
  alias?: string;
  color: string;
  name: string;
}

export function generatorColorVariables(colorItems: ColorItem[]) {
  const colorVariables: Record<string, string> = {};

  colorItems.forEach(({ alias, color, name }) => {
    if (color) {
      const colorsMap = getColors(new TinyColor(color).toHexString());
      let mainColor = colorsMap['500'];

      Object.keys(colorsMap).forEach((key) => {
        const colorValue = colorsMap[key];
        if (colorValue) {
          const hslColor = convertToHslCssVar(colorValue);
          colorVariables[`--${name}-${key}`] = hslColor;
          if (alias) {
            colorVariables[`--${alias}-${key}`] = hslColor;
          }
          if (key === '500') {
            mainColor = hslColor;
          }
        }
      });
      if (alias && mainColor) {
        colorVariables[`--${alias}`] = mainColor;
      }
    }
  });
  return colorVariables;
}
```

### 5.3 CSS 变量更新工具

**文件：`packages/@core/base/shared/src/utils/update-css-variables.ts`**

```typescript
export function updateCSSVariables(
  variables: { [key: string]: string },
  id = '__app-styles__',
): void {
  const styleElement =
    document.querySelector(`#${id}`) || document.createElement('style');

  styleElement.id = id;

  let cssText = ':root {';
  for (const key in variables) {
    if (Object.prototype.hasOwnProperty.call(variables, key)) {
      cssText += `${key}: ${variables[key]};`;
    }
  }
  cssText += '}';

  styleElement.textContent = cssText;

  if (!document.querySelector(`#${id}`)) {
    setTimeout(() => {
      document.head.append(styleElement);
    });
  }
}
```

### 5.4 对象工具

**文件：`packages/@core/base/shared/src/utils/merge.ts`**

```typescript
import { defu } from 'defu';

export const merge = defu;
```

**文件：`packages/@core/base/shared/src/utils/diff.ts`**

```typescript
export function diff<T extends Record<string, any>>(obj1: T, obj2: T): Partial<T> {
  function findDifferences(o1: any, o2: any): any {
    if (Array.isArray(o1) && Array.isArray(o2)) {
      if (JSON.stringify(o1) !== JSON.stringify(o2)) {
        return o2;
      }
      return undefined;
    }

    if (
      typeof o1 === 'object' &&
      typeof o2 === 'object' &&
      o1 !== null &&
      o2 !== null
    ) {
      const diffResult: any = {};
      const keys = new Set([...Object.keys(o1), ...Object.keys(o2)]);
      keys.forEach((key) => {
        const valueDiff = findDifferences(o1[key], o2[key]);
        if (valueDiff !== undefined) {
          diffResult[key] = valueDiff;
        }
      });
      return Object.keys(diffResult).length > 0 ? diffResult : undefined;
    }

    return o1 === o2 ? undefined : o2;
  }

  return findDifferences(obj1, obj2) || {};
}
```

### 5.5 存储管理器

**文件：`packages/@core/base/shared/src/cache/storage-manager.ts`**

```typescript
type StorageType = 'localStorage' | 'sessionStorage';

interface StorageManagerOptions {
  prefix?: string;
  storageType?: StorageType;
}

interface StorageItem<T> {
  expiry?: number;
  value: T;
}

class StorageManager {
  private prefix: string;
  private storage: Storage;

  constructor({
    prefix = '',
    storageType = 'localStorage',
  }: StorageManagerOptions = {}) {
    this.prefix = prefix;
    this.storage =
      storageType === 'localStorage'
        ? window.localStorage
        : window.sessionStorage;
  }

  clear(): void {
    const keysToRemove: string[] = [];
    for (let i = 0; i < this.storage.length; i++) {
      const key = this.storage.key(i);
      if (key && key.startsWith(this.prefix)) {
        keysToRemove.push(key);
      }
    }
    keysToRemove.forEach((key) => this.storage.removeItem(key));
  }

  getItem<T>(key: string, defaultValue: null | T = null): null | T {
    const fullKey = this.getFullKey(key);
    const itemStr = this.storage.getItem(fullKey);
    if (!itemStr) {
      return defaultValue;
    }

    try {
      const item: StorageItem<T> = JSON.parse(itemStr);
      if (item.expiry && Date.now() > item.expiry) {
        this.storage.removeItem(fullKey);
        return defaultValue;
      }
      return item.value;
    } catch (error) {
      console.error(`Error parsing item with key "${fullKey}":`, error);
      this.storage.removeItem(fullKey);
      return defaultValue;
    }
  }

  removeItem(key: string): void {
    const fullKey = this.getFullKey(key);
    this.storage.removeItem(fullKey);
  }

  setItem<T>(key: string, value: T, ttl?: number): void {
    const fullKey = this.getFullKey(key);
    const expiry = ttl ? Date.now() + ttl : undefined;
    const item: StorageItem<T> = { expiry, value };
    try {
      this.storage.setItem(fullKey, JSON.stringify(item));
    } catch (error) {
      console.error(`Error setting item with key "${fullKey}":`, error);
    }
  }

  private getFullKey(key: string): string {
    return `${this.prefix}-${key}`;
  }
}

export { StorageManager };
```

---

## 六、布局组件系统

### 6.1 布局 Props 定义

**文件：`packages/@core/ui-kit/layout-ui/src/vben-layout.ts`**

```typescript
import type {
  ContentCompactType,
  LayoutHeaderModeType,
  LayoutType,
  ThemeModeType,
} from '@vben-core/typings';

interface VbenLayoutProps {
  contentCompact?: ContentCompactType;
  contentCompactWidth?: number;
  contentPadding?: number;
  contentPaddingBottom?: number;
  contentPaddingLeft?: number;
  contentPaddingRight?: number;
  contentPaddingTop?: number;
  footerEnable?: boolean;
  footerFixed?: boolean;
  footerHeight?: number;
  headerHeight?: number;
  headerHidden?: boolean;
  headerMode?: LayoutHeaderModeType;
  headerTheme?: ThemeModeType;
  headerToggleSidebarButton?: boolean;
  headerVisible?: boolean;
  isMobile?: boolean;
  layout?: LayoutType;
  sidebarCollapse?: boolean;
  sidebarCollapsedButton?: boolean;
  sidebarCollapseShowTitle?: boolean;
  sidebarEnable?: boolean;
  sidebarExtraCollapsedWidth?: number;
  sidebarFixedButton?: boolean;
  sidebarHidden?: boolean;
  sidebarMixedWidth?: number;
  sidebarTheme?: ThemeModeType;
  sidebarWidth?: number;
  sideCollapseWidth?: number;
  tabbarEnable?: boolean;
  tabbarHeight?: number;
  zIndex?: number;
}

export type { VbenLayoutProps };
```

### 6.2 布局 Hook

**文件：`packages/@core/ui-kit/layout-ui/src/hooks/use-layout.ts`**

```typescript
import type { LayoutType } from '@vben-core/typings';
import type { VbenLayoutProps } from '../vben-layout';

import { computed } from 'vue';

export function useLayout(props: VbenLayoutProps) {
  const currentLayout = computed(() =>
    props.isMobile ? 'sidebar-nav' : (props.layout as LayoutType),
  );

  const isFullContent = computed(() => currentLayout.value === 'full-content');

  const isSidebarMixedNav = computed(
    () => currentLayout.value === 'sidebar-mixed-nav',
  );

  const isHeaderNav = computed(() => currentLayout.value === 'header-nav');

  const isMixedNav = computed(
    () =>
      currentLayout.value === 'mixed-nav' ||
      currentLayout.value === 'header-sidebar-nav',
  );

  const isHeaderMixedNav = computed(
    () => currentLayout.value === 'header-mixed-nav',
  );

  return {
    currentLayout,
    isFullContent,
    isHeaderMixedNav,
    isHeaderNav,
    isMixedNav,
    isSidebarMixedNav,
  };
}
```

### 6.3 布局容器组件

**文件：`packages/@core/ui-kit/layout-ui/src/vben-layout.vue`**

```vue
<script setup lang="ts">
import type { CSSProperties } from 'vue';

import { computed, ref } from 'vue';

import { preferences } from '@vben/preferences';

import { useLayout } from './hooks/use-layout';
import { VbenLayoutProps } from './vben-layout';

interface Props extends VbenLayoutProps {}

const props = withDefaults(defineProps<Props>(), {
  contentCompact: 'wide',
  contentCompactWidth: 1200,
  contentPadding: 16,
  contentPaddingBottom: 16,
  contentPaddingLeft: 16,
  contentPaddingRight: 16,
  contentPaddingTop: 16,
  footerEnable: false,
  footerFixed: true,
  footerHeight: 32,
  headerHeight: 48,
  headerHidden: false,
  headerMode: 'fixed',
  headerToggleSidebarButton: false,
  headerVisible: true,
  isMobile: false,
  layout: 'sidebar-nav',
  sidebarCollapse: false,
  sidebarCollapsedButton: true,
  sidebarCollapseShowTitle: true,
  sidebarEnable: true,
  sidebarExtraCollapsedWidth: 48,
  sidebarFixedButton: true,
  sidebarHidden: false,
  sidebarMixedWidth: 80,
  sidebarTheme: 'dark',
  sidebarWidth: 210,
  sideCollapseWidth: 48,
  tabbarEnable: true,
  tabbarHeight: 30,
  zIndex: 100,
});

const { currentLayout, isFullContent, isHeaderMixedNav, isHeaderNav, isMixedNav, isSidebarMixedNav } = useLayout(props);

const collapse = defineModel<boolean>('collapse');
const expandOnHovering = defineModel<boolean>('expandOnHovering');
const expandOnHover = defineModel<boolean>('expandOnHover');
const extraCollapse = defineModel<boolean>('extraCollapse');
const extraVisible = defineModel<boolean>('extraVisible');

const sidebarCollapseState = computed(() => {
  return collapse.value || expandOnHovering.value;
});

const mainStyle = computed((): CSSProperties => {
  const { sidebarCollapse, sidebarEnable, sidebarHidden, sidebarWidth, sideCollapseWidth } = props;

  const width = sidebarEnable && !sidebarHidden
    ? sidebarCollapse ? sideCollapseWidth : sidebarWidth
    : 0;

  return {
    paddingLeft: `${width}px`,
    transition: 'padding-left 0.2s',
  };
});

const contentStyle = computed((): CSSProperties => {
  const { contentPadding, contentPaddingBottom, contentPaddingLeft, contentPaddingRight, contentPaddingTop } = props;

  return {
    padding: `${contentPaddingTop}px ${contentPaddingRight}px ${contentPaddingBottom}px ${contentPaddingLeft}px`,
  };
});

const footerStyle = computed((): CSSProperties => {
  const { footerEnable, footerFixed, footerHeight, sidebarEnable, sidebarHidden, sidebarWidth, sideCollapseWidth } = props;

  const width = sidebarEnable && !sidebarHidden
    ? collapse.value ? sideCollapseWidth : sidebarWidth
    : 0;

  return {
    height: `${footerHeight}px`,
    left: `${width}px`,
    position: footerFixed ? 'fixed' : 'relative',
    width: `calc(100% - ${width}px)`,
  };
});
</script>

<template>
  <div class="vben-layout" :style="{ zIndex: props.zIndex }">
    <!-- 侧边栏 -->
    <LayoutSidebar
      v-if="!isFullContent && sidebarEnable"
      v-model:collapse="collapse"
      v-model:expand-on-hover="expandOnHover"
      v-model:expand-on-hovering="expandOnHovering"
      v-model:extra-collapse="extraCollapse"
      v-model:extra-visible="extraVisible"
      :collapse-width="sideCollapseWidth"
      :extra-width="sidebarMixedWidth"
      :fixed-extra="isSidebarMixedNav"
      :header-height="headerHeight"
      :is-sidebar-mixed="isSidebarMixedNav"
      :mixed-width="sidebarMixedWidth"
      :show="!sidebarHidden"
      :show-collapse-button="sidebarCollapsedButton"
      :show-fixed-button="sidebarFixedButton"
      :theme="sidebarTheme"
      :width="sidebarWidth"
    >
      <template #logo><slot name="logo"></slot></template>
      <template #default><slot name="menu"></slot></template>
      <template #extra-title><slot name="extra-title"></slot></template>
      <template #extra><slot name="extra"></slot></template>
    </LayoutSidebar>

    <!-- 主内容区 -->
    <div class="vben-layout-main" :style="mainStyle">
      <!-- 头部 -->
      <LayoutHeader
        v-if="!isFullContent && headerVisible"
        :full-width="isHeaderNav || isMixedNav || isHeaderMixedNav"
        :height="headerHeight"
        :is-mobile="isMobile"
        :show="!headerHidden"
        :sidebar-width="sidebarWidth"
        :theme="headerTheme"
        :width="isHeaderNav || isMixedNav || isHeaderMixedNav ? '100%' : `calc(100% - ${sidebarWidth}px)`"
        :zIndex="zIndex"
      >
        <template #logo><slot name="logo"></slot></template>
        <template #toggle-button>
          <slot v-if="headerToggleSidebarButton" name="toggle-button"></slot>
        </template>
        <slot name="header"></slot>
      </LayoutHeader>

      <!-- Tab 栏 -->
      <div v-if="tabbarEnable && !isFullContent" :style="{ height: `${tabbarHeight}px` }">
        <slot name="tabbar"></slot>
      </div>

      <!-- 内容 -->
      <LayoutContent
        :content-compact="contentCompact"
        :content-compact-width="contentCompactWidth"
        :padding="contentPadding"
        :padding-bottom="contentPaddingBottom"
        :padding-left="contentPaddingLeft"
        :padding-right="contentPaddingRight"
        :padding-top="contentPaddingTop"
      >
        <template #overlay><slot name="overlay"></slot></template>
        <slot name="content"></slot>
      </LayoutContent>

      <!-- 页脚 -->
      <LayoutFooter
        v-if="footerEnable && !isFullContent"
        :fixed="footerFixed"
        :height="footerHeight"
        :show="true"
        :width="footerStyle.width"
        :zIndex="zIndex"
      >
        <slot name="footer"></slot>
      </LayoutFooter>
    </div>
  </div>
</template>

<style scoped>
.vben-layout {
  display: flex;
  height: 100vh;
  width: 100%;
}

.vben-layout-main {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  transition: padding-left 0.2s;
}
</style>
```

### 6.4 侧边栏组件

**文件：`packages/@core/ui-kit/layout-ui/src/components/layout-sidebar.vue`**

```vue
<script setup lang="ts">
import type { CSSProperties } from 'vue';

import { computed, shallowRef, useSlots, watchEffect } from 'vue';
import { useScrollLock } from '@vueuse/core';

interface Props {
  collapseHeight?: number;
  collapseWidth?: number;
  domVisible?: boolean;
  extraWidth: number;
  fixedExtra?: boolean;
  headerHeight: number;
  isSidebarMixed?: boolean;
  marginTop?: number;
  mixedWidth?: number;
  paddingTop?: number;
  show?: boolean;
  showCollapseButton?: boolean;
  showFixedButton?: boolean;
  theme: string;
  width: number;
  zIndex?: number;
}

const props = withDefaults(defineProps<Props>(), {
  collapseHeight: 42,
  collapseWidth: 48,
  domVisible: true,
  fixedExtra: false,
  isSidebarMixed: false,
  marginTop: 0,
  mixedWidth: 70,
  paddingTop: 0,
  show: true,
  showCollapseButton: true,
  showFixedButton: true,
  zIndex: 0,
});

const emit = defineEmits<{ leave: [] }>();
const collapse = defineModel<boolean>('collapse');
const extraCollapse = defineModel<boolean>('extraCollapse');
const expandOnHovering = defineModel<boolean>('expandOnHovering');
const expandOnHover = defineModel<boolean>('expandOnHover');
const extraVisible = defineModel<boolean>('extraVisible');

const isLocked = useScrollLock(document.body);
const slots = useSlots();
const asideRef = shallowRef<HTMLDivElement | null>();

const hiddenSideStyle = computed((): CSSProperties => calcMenuWidthStyle(true));

const style = computed((): CSSProperties => {
  const { isSidebarMixed, marginTop, paddingTop, zIndex } = props;
  return {
    '--scroll-shadow': 'var(--sidebar)',
    ...calcMenuWidthStyle(false),
    height: `calc(100% - ${marginTop}px)`,
    marginTop: `${marginTop}px`,
    paddingTop: `${paddingTop}px`,
    zIndex,
    ...(isSidebarMixed && extraVisible.value ? { transition: 'none' } : {}),
  };
});

const extraStyle = computed((): CSSProperties => {
  const { extraWidth, show, width, zIndex } = props;
  return {
    left: `${width}px`,
    width: extraVisible.value && show ? `${extraWidth}px` : 0,
    zIndex,
  };
});

function calcMenuWidthStyle(isHiddenDom: boolean): CSSProperties {
  const { extraWidth, fixedExtra, isSidebarMixed, show, width } = props;

  let widthValue =
    width === 0
      ? '0px'
      : `${width + (isSidebarMixed && fixedExtra && extraVisible.value ? extraWidth : 0)}px`;

  const { collapseWidth } = props;

  if (isHiddenDom && expandOnHovering.value && !expandOnHover.value) {
    widthValue = `${collapseWidth}px`;
  }

  return {
    ...(widthValue === '0px' ? { overflow: 'hidden' } : {}),
    flex: `0 0 ${widthValue}`,
    marginLeft: show ? 0 : `-${widthValue}`,
    maxWidth: widthValue,
    minWidth: widthValue,
    width: widthValue,
  };
}

function handleMouseenter(e: MouseEvent) {
  if (e?.offsetX < 10) return;
  if (expandOnHover.value) return;
  if (!expandOnHovering.value) {
    collapse.value = false;
  }
  if (props.isSidebarMixed) {
    isLocked.value = true;
  }
  expandOnHovering.value = true;
}

function handleMouseleave() {
  emit('leave');
  if (props.isSidebarMixed) {
    isLocked.value = false;
  }
  if (expandOnHover.value) return;
  expandOnHovering.value = false;
  collapse.value = true;
  extraVisible.value = false;
}
</script>

<template>
  <div
    v-if="domVisible"
    :class="theme"
    :style="hiddenSideStyle"
    class="h-full transition-all duration-150"
  ></div>
  <aside
    :class="[
      theme,
      {
        'bg-sidebar-deep': isSidebarMixed,
        'bg-sidebar border-border border-r': !isSidebarMixed,
      },
    ]"
    :style="style"
    class="fixed left-0 top-0 h-full transition-all duration-150"
    @mouseenter="handleMouseenter"
    @mouseleave="handleMouseleave"
  >
    <div v-if="slots.logo" :style="{ height: `${headerHeight - 1}px` }">
      <slot name="logo"></slot>
    </div>
    <div :style="{ height: `calc(100% - ${headerHeight + collapseHeight}px)`, paddingTop: '8px' }">
      <slot></slot>
    </div>
    <div :style="{ height: `${collapseHeight}px` }"></div>

    <div
      v-if="isSidebarMixed"
      ref="asideRef"
      :class="{ 'border-l': extraVisible }"
      :style="extraStyle"
      class="border-border bg-sidebar fixed top-0 h-full overflow-hidden border-r transition-all duration-200"
    >
      <div v-if="!extraCollapse" :style="{ height: `${headerHeight - 1}px` }" class="pl-2">
        <slot name="extra-title"></slot>
      </div>
      <div :style="{ height: `calc(100% - ${headerHeight + collapseHeight}px)` }" class="border-border py-2">
        <slot name="extra"></slot>
      </div>
    </div>
  </aside>
</template>
```

### 6.5 头部组件

**文件：`packages/@core/ui-kit/layout-ui/src/components/layout-header.vue`**

```vue
<script setup lang="ts">
import type { CSSProperties } from 'vue';

import { computed, useSlots } from 'vue';

interface Props {
  fullWidth: boolean;
  height: number;
  isMobile: boolean;
  show: boolean;
  sidebarWidth: number;
  theme: string | undefined;
  width: string;
  zIndex: number;
}

const props = withDefaults(defineProps<Props>(), {});
const slots = useSlots();

const style = computed((): CSSProperties => {
  const { fullWidth, height, show } = props;
  const right = !show || !fullWidth ? undefined : 0;
  return {
    height: `${height}px`,
    marginTop: show ? 0 : `-${height}px`,
    right,
  };
});

const logoStyle = computed((): CSSProperties => {
  return {
    minWidth: `${props.isMobile ? 40 : props.sidebarWidth}px`,
  };
});
</script>

<template>
  <header
    :class="theme"
    :style="style"
    class="border-border bg-header top-0 flex w-full flex-[0_0_auto] items-center border-b pl-2 transition-[margin-top] duration-200"
  >
    <div v-if="slots.logo" :style="logoStyle">
      <slot name="logo"></slot>
    </div>
    <slot name="toggle-button"></slot>
    <slot></slot>
  </header>
</template>
```

### 6.6 内容组件

**文件：`packages/@core/ui-kit/layout-ui/src/components/layout-content.vue`**

```vue
<script setup lang="ts">
import type { CSSProperties } from 'vue';
import type { ContentCompactType } from '@vben-core/typings';

import { computed } from 'vue';

interface Props {
  contentCompact: ContentCompactType;
  contentCompactWidth: number;
  padding: number;
  paddingBottom: number;
  paddingLeft: number;
  paddingRight: number;
  paddingTop: number;
}

const props = withDefaults(defineProps<Props>(), {});

const style = computed((): CSSProperties => {
  const { contentCompact, padding, paddingBottom, paddingLeft, paddingRight, paddingTop } = props;

  const compactStyle: CSSProperties =
    contentCompact === 'compact'
      ? { margin: '0 auto', width: `${props.contentCompactWidth}px` }
      : {};

  return {
    ...compactStyle,
    flex: 1,
    padding: `${padding}px`,
    paddingBottom: `${paddingBottom}px`,
    paddingLeft: `${paddingLeft}px`,
    paddingRight: `${paddingRight}px`,
    paddingTop: `${paddingTop}px`,
  };
});
</script>

<template>
  <main :style="style" class="bg-background-deep relative">
    <slot></slot>
  </main>
</template>
```

### 6.7 页脚组件

**文件：`packages/@core/ui-kit/layout-ui/src/components/layout-footer.vue`**

```vue
<script setup lang="ts">
import type { CSSProperties } from 'vue';

import { computed } from 'vue';

interface Props {
  fixed?: boolean;
  height: number;
  show?: boolean;
  width: string;
  zIndex: number;
}

const props = withDefaults(defineProps<Props>(), {
  show: true,
});

const style = computed((): CSSProperties => {
  const { fixed, height, show, width, zIndex } = props;
  return {
    height: `${height}px`,
    marginBottom: show ? '0' : `-${height}px`,
    position: fixed ? 'fixed' : 'static',
    width,
    zIndex,
  };
});
</script>

<template>
  <footer
    :style="style"
    class="bg-background-deep bottom-0 w-full transition-all duration-200"
  >
    <slot></slot>
  </footer>
</template>
```

---

## 七、UI 交互组件

### 7.1 布局总装组件

**文件：`packages/effects/layouts/src/basic/layout.vue`**

```vue
<script lang="ts" setup>
import { computed, watch } from 'vue';

import { useWatermark } from '@vben/hooks';
import { preferences, updatePreferences, usePreferences } from '@vben/preferences';

import { VbenAdminLayout, VbenBackTop } from '@vben-core/layout-ui';

import { useAuthStore, useLockStore } from '#/store';

import { LayoutContent, LayoutContentSpinner } from '../content';
import { LayoutFooter } from '../footer';
import { LayoutHeader } from '../header';
import { LayoutTabbar } from '../tabbar';
import { AuthenticationModalLogin, CheckUpdates, PreferencesDrawer } from '../widgets';

defineOptions({ name: 'BasicLayout' });

const authStore = useAuthStore();
const lockStore = useLockStore();

const { destroyWatermark, updateWatermark } = useWatermark();

const { isDark, isMobile, layout } = usePreferences();

const sidebarTheme = computed(() => {
  const dark = isDark.value || preferences.theme.semiDarkSidebar;
  return dark ? 'dark' : 'light';
});

const headerTheme = computed(() => {
  const dark = isDark.value || preferences.theme.semiDarkHeader;
  return dark ? 'dark' : 'light';
});

const showHeaderNav = computed(() => {
  return layout.value !== 'header-nav' && layout.value !== 'mixed-nav';
});

const showMixedNav = computed(() => layout.value === 'mixed-nav');

const showHeaderMixedNav = computed(() => layout.value === 'header-mixed-nav');

const showSidebarMixedNav = computed(() => layout.value === 'sidebar-mixed-nav');

const contentIsPadding = computed(() => {
  return layout.value === 'header-nav' || layout.value === 'mixed-nav';
});

const contentPadding = computed(() => {
  return contentIsPadding.value ? 16 : 0;
});

const logoClass = computed(() => {
  return layout.value === 'sidebar-mixed-nav' ? 'mr-4 flex-none' : 'flex-center flex-none';
});

const isAuthenticationModalLogin = computed(() => {
  return authStore.isLoginExpired && preferences.app.loginExpiredMode === 'modal';
});

const showBreadcrumb = computed(() => {
  return preferences.navigation.styleType === 'rounded' && layout.value !== 'header-nav';
});

watch(
  () => preferences.app.watermark,
  (enable) => {
    enable ? updateWatermark() : destroyWatermark();
  },
  { immediate: true },
);
</script>

<template>
  <VbenAdminLayout
    v-model:collapse="preferences.sidebar.collapsed"
    v-model:expand-on-hover="preferences.sidebar.expandOnHover"
    v-model:expand-on-hovering="preferences.sidebar.expandOnHovering"
    v-model:extra-collapse="preferences.sidebar.extraCollapse"
    v-model:extra-visible="preferences.sidebar.extraVisible"
    :content-compact="preferences.app.contentCompact"
    :content-compact-width="preferences.app.contentCompactWidth"
    :content-padding="contentPadding"
    :content-padding-bottom="preferences.app.contentPaddingBottom"
    :content-padding-left="preferences.app.contentPaddingLeft"
    :content-padding-right="preferences.app.contentPaddingRight"
    :content-padding-top="preferences.app.contentPaddingTop"
    :footer-enable="preferences.footer.enable"
    :footer-fixed="preferences.footer.fixed"
    :footer-height="preferences.footer.height"
    :header-height="preferences.header.height"
    :header-hidden="preferences.header.hidden"
    :header-mode="preferences.header.mode"
    :header-theme="headerTheme"
    :header-toggle-sidebar-button="preferences.widget.sidebarToggle"
    :header-visible="preferences.header.enable"
    :is-mobile="isMobile"
    :layout="layout"
    :sidebar-collapse="preferences.sidebar.collapsed"
    :sidebar-collapsed-button="preferences.sidebar.collapsedButton"
    :sidebar-collapse-show-title="preferences.sidebar.collapsedShowTitle"
    :sidebar-enable="preferences.sidebar.enable"
    :sidebar-extra-collapsed-width="preferences.sidebar.extraCollapsedWidth"
    :sidebar-fixed-button="preferences.sidebar.fixedButton"
    :sidebar-hidden="preferences.sidebar.hidden"
    :sidebar-mixed-width="preferences.sidebar.mixedWidth"
    :sidebar-theme="sidebarTheme"
    :sidebar-width="preferences.sidebar.width"
    :side-collapse-width="preferences.sidebar.collapseWidth"
    :tabbar-enable="preferences.tabbar.enable"
    :tabbar-height="preferences.tabbar.height"
  >
    <template #logo>
      <slot :class="logoClass" name="logo"></slot>
    </template>

    <template #header>
      <LayoutHeader :theme="theme">
        <template #breadcrumb>
          <slot v-if="showBreadcrumb" name="breadcrumb"></slot>
        </template>
        <template #menu>
          <slot v-if="!showHeaderNav" name="menu"></slot>
        </template>
      </LayoutHeader>
    </template>

    <template #menu>
      <slot v-if="showHeaderNav" name="menu"></slot>
    </template>

    <template #mixed-menu>
      <slot v-if="showMixedNav" name="mixed-menu"></slot>
    </template>

    <template #side-extra-title>
      <slot name="side-extra-title"></slot>
    </template>

    <template #side-extra>
      <slot name="side-extra"></slot>
    </template>

    <template #tabbar>
      <LayoutTabbar v-if="preferences.tabbar.enable" />
    </template>

    <template #content>
      <LayoutContent>
        <template #loading>
          <LayoutContentSpinner />
        </template>
      </LayoutContent>
    </template>

    <template #footer>
      <LayoutFooter />
    </template>
  </VbenAdminLayout>

  <AuthenticationModalLogin v-model:open="isAuthenticationModalLogin" />
  <CheckUpdates check-version />
  <PreferencesDrawer />
  <VbenBackTop />
</template>
```

### 7.2 主题切换按钮

**文件：`packages/effects/layouts/src/widgets/theme-toggle/theme-button.vue`**

```vue
<script lang="ts" setup>
import { computed, nextTick } from 'vue';

interface Props {
  type?: 'icon' | 'normal';
}

defineOptions({ name: 'ThemeToggleButton' });

const props = withDefaults(defineProps<Props>(), {
  type: 'normal',
});

const isDark = defineModel<boolean>();

const theme = computed(() => {
  return isDark.value ? 'light' : 'dark';
});

const bindProps = computed(() => {
  const type = props.type;
  return type === 'normal'
    ? { variant: 'heavy' as const }
    : {
        class: 'rounded-full',
        size: 'icon' as const,
        style: { padding: '7px' },
        variant: 'icon' as const,
      };
});

function toggleTheme(event: MouseEvent) {
  const isAppearanceTransition =
    document.startViewTransition &&
    !window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  if (!isAppearanceTransition || !event) {
    isDark.value = !isDark.value;
    return;
  }

  const x = event.clientX;
  const y = event.clientY;
  const endRadius = Math.hypot(
    Math.max(x, innerWidth - x),
    Math.max(y, innerHeight - y),
  );

  const transition = document.startViewTransition(async () => {
    isDark.value = !isDark.value;
    await nextTick();
  });

  transition.ready.then(() => {
    const clipPath = [
      `circle(0px at ${x}px ${y}px)`,
      `circle(${endRadius}px at ${x}px ${y}px)`,
    ];
    document.documentElement.animate(
      {
        clipPath: isDark.value ? [...clipPath].reverse() : clipPath,
      },
      {
        duration: 450,
        easing: 'ease-in',
        pseudoElement: isDark.value
          ? '::view-transition-old(root)'
          : '::view-transition-new(root)',
      },
    );
  });
}
</script>

<template>
  <button
    :aria-label="theme"
    :class="[`is-${theme}`]"
    aria-live="polite"
    class="theme-toggle cursor-pointer border-none bg-none"
    v-bind="bindProps"
    @click.stop="toggleTheme"
  >
    <svg aria-hidden="true" height="24" viewBox="0 0 24 24" width="24">
      <mask
        id="theme-toggle-moon"
        class="theme-toggle__moon"
        fill="hsl(var(--foreground)/80%)"
        stroke="none"
      >
        <rect fill="white" height="100%" width="100%" x="0" y="0" />
        <circle cx="40" cy="8" fill="black" r="11" />
      </mask>
      <circle
        id="sun"
        class="theme-toggle__sun"
        cx="12"
        cy="12"
        mask="url(#theme-toggle-moon)"
        r="11"
      />
      <g class="theme-toggle__sun-beams">
        <line x1="12" x2="12" y1="1" y2="3" />
        <line x1="12" x2="12" y1="21" y2="23" />
        <line x1="4.22" x2="5.64" y1="4.22" y2="5.64" />
        <line x1="18.36" x2="19.78" y1="18.36" y2="19.78" />
        <line x1="1" x2="3" y1="12" y2="12" />
        <line x1="21" x2="23" y1="12" y2="12" />
        <line x1="4.22" x2="5.64" y1="19.78" y2="18.36" />
        <line x1="18.36" x2="19.78" y1="5.64" y2="4.22" />
      </g>
    </svg>
  </button>
</template>

<style scoped>
.theme-toggle {
  &__moon > circle {
    transition: transform 0.5s cubic-bezier(0, 0, 0.3, 1);
  }

  &__sun {
    fill: hsl(var(--foreground) / 0.9);
    stroke: none;
    transform-origin: center center;
    transition: transform 1.6s cubic-bezier(0.25, 0, 0.2, 1);
  }

  &__sun-beams {
    stroke: hsl(var(--foreground) / 0.9);
    stroke-width: 2px;
    transform-origin: center center;
    transition:
      transform 1.6s cubic-bezier(0.5, 1.5, 0.75, 1.25),
      opacity 0.6s cubic-bezier(0.25, 0, 0.3, 1);
  }

  &.is-light {
    .theme-toggle__sun {
      transform: scale(0.5);
    }
    .theme-toggle__sun-beams {
      transform: rotateZ(0.25turn);
    }
  }

  &.is-dark {
    .theme-toggle__moon > circle {
      transform: translateX(-20px);
    }
    .theme-toggle__sun-beams {
      opacity: 0;
    }
  }
}
</style>
```

### 7.3 主题切换组件

**文件：`packages/effects/layouts/src/widgets/theme-toggle/theme-toggle.vue`**

```vue
<script setup lang="ts">
import { computed } from 'vue';

import { preferences, updatePreferences } from '@vben/preferences';

import ThemeButton from './theme-button.vue';

const modelValue = computed({
  get() {
    return preferences.theme.mode === 'dark';
  },
  set(value) {
    updatePreferences({
      theme: { mode: value ? 'dark' : 'light' },
    });
  },
});
</script>

<template>
  <ThemeButton v-model="modelValue" />
</template>
```

### 7.4 偏好设置抽屉

**文件：`packages/effects/layouts/src/widgets/preferences/preferences-drawer.vue`**

```vue
<script setup lang="ts">
import { computed, ref } from 'vue';

import { preferences, resetPreferences, updatePreferences, usePreferences } from '@vben/preferences';

import { VbenButton, VbenDrawer } from '@vben-core/shadcn-ui';

import { RotateCcw } from '@vben/icons';

import { useAccessStore } from '#/store';

import { PreferenceBreadcrumb, PreferenceContent, PreferenceFooter, PreferenceGeneral, PreferenceHeader, PreferenceLayout, PreferenceNavigation, PreferenceShortcutKeys, PreferenceSidebar, PreferenceTabbar, PreferenceTheme, PreferenceTransition, PreferenceWidget } from './blocks';

const emit = defineEmits<{ clearPreferencesAndLogout: [] }>;

const accessStore = useAccessStore();

const activeTab = ref('appearance');
const { diffPreference } = usePreferences();

const open = computed({
  get() {
    return preferences.app.enablePreferences;
  },
  set(val) {
    updatePreferences({ app: { enablePreferences: val } });
  },
});

const appPreferences = computed(() => {
  return {
    app: preferences.app,
    breadcrumb: preferences.breadcrumb,
    copyright: preferences.copyright,
    footer: preferences.footer,
    header: preferences.header,
    logo: preferences.logo,
    navigation: preferences.navigation,
    shortcutKeys: preferences.shortcutKeys,
    sidebar: preferences.sidebar,
    tabbar: preferences.tabbar,
    transition: preferences.transition,
    widget: preferences.widget,
  };
});

function handleCopy() {
  const { copy } = useClipboard({ legacy: true });
  const preferencesCopy = JSON.stringify(diffPreference.value);
  copy(preferencesCopy);
  // $t('preferences.copySuccess')
}

function handleReset() {
  if (!diffPreference.value) return;
  resetPreferences();
  emit('clearPreferencesAndLogout');
}
</script>

<template>
  <VbenDrawer
    v-model:open="open"
    :z-index="preferences.app.zIndex"
    class="w-[600px]"
    closable
    title="偏好设置"
  >
    <template #extra>
      <VbenButton
        :disabled="!diffPreference"
        size="icon"
        variant="ghost"
        @click="handleReset"
      >
        <RotateCcw class="size-4" />
      </VbenButton>
    </template>

    <PreferenceContent v-model="activeTab">
      <template #appearance>
        <PreferenceTheme
          v-model="preferences.theme.mode"
          v-model:theme-semi-dark-header="preferences.theme.semiDarkHeader"
          v-model:theme-semi-dark-sidebar="preferences.theme.semiDarkSidebar"
        />
        <PreferenceBuiltinTheme
          v-model="preferences.theme.builtinType"
          v-model:theme-color-primary="preferences.theme.colorPrimary"
          :is-dark="isDark"
        />
        <PreferenceRadius
          v-model="preferences.theme.radius"
        />
        <PreferenceGrayMode
          v-model="preferences.app.colorGrayMode"
        />
        <PreferenceColorWeakMode
          v-model="preferences.app.colorWeakMode"
        />
      </template>

      <template #layout>
        <PreferenceLayout
          v-model="preferences.app.layout"
        />
        <PreferenceContentCompact
          v-model="preferences.app.contentCompact"
          v-model:content-compact-width="preferences.app.contentCompactWidth"
        />
        <PreferenceSidebar
          v-model:sidebar-auto-activate-child="preferences.sidebar.autoActivateChild"
          v-model:sidebar-collapsed="preferences.sidebar.collapsed"
          v-model:sidebar-collapsed-button="preferences.sidebar.collapsedButton"
          v-model:sidebar-collapsed-show-title="preferences.sidebar.collapsedShowTitle"
          v-model:sidebar-enable="preferences.sidebar.enable"
          v-model:sidebar-expand-on-hover="preferences.sidebar.expandOnHover"
          v-model:sidebar-fixed-button="preferences.sidebar.fixedButton"
          v-model:sidebar-width="preferences.sidebar.width"
          :current-layout="preferences.app.layout"
          :disabled="preferences.app.isMobile"
        />
        <PreferenceHeader
          v-model:header-enable="preferences.header.enable"
          v-model:header-hidden="preferences.header.hidden"
          v-model:header-menu-align="preferences.header.menuAlign"
          v-model:header-mode="preferences.header.mode"
          :disabled="preferences.app.isMobile"
        />
        <PreferenceNavigation
          v-model:navigation-accordion="preferences.navigation.accordion"
          v-model:navigation-split="preferences.navigation.split"
          v-model:navigation-style-type="preferences.navigation.styleType"
        />
        <PreferenceBreadcrumb
          v-model:breadcrumb-enable="preferences.breadcrumb.enable"
          v-model:breadcrumb-hide-only-one="preferences.breadcrumb.hideOnlyOne"
          v-model:breadcrumb-show-home="preferences.breadcrumb.showHome"
          v-model:breadcrumb-show-icon="preferences.breadcrumb.showIcon"
          v-model:breadcrumb-style-type="preferences.breadcrumb.styleType"
        />
        <PreferenceTabbar
          v-model:tabbar-draggable="preferences.tabbar.draggable"
          v-model:tabbar-enable="preferences.tabbar.enable"
          v-model:tabbar-height="preferences.tabbar.height"
          v-model:tabbar-keep-alive="preferences.tabbar.keepAlive"
          v-model:tabbar-max-count="preferences.tabbar.maxCount"
          v-model:tabbar-show-icon="preferences.tabbar.showIcon"
          v-model:tabbar-show-maximize="preferences.tabbar.showMaximize"
          v-model:tabbar-show-more="preferences.tabbar.showMore"
          v-model:tabbar-style-type="preferences.tabbar.styleType"
        />
        <PreferenceFooter
          v-model:footer-enable="preferences.footer.enable"
          v-model:footer-fixed="preferences.footer.fixed"
          v-model:footer-height="preferences.footer.height"
        />
      </template>

      <template #shortcutKeys>
        <PreferenceShortcutKeys
          v-model:shortcut-keys-enable="preferences.shortcutKeys.enable"
          v-model:shortcut-keys-global-lock-screen="preferences.shortcutKeys.globalLockScreen"
          v-model:shortcut-keys-global-logout="preferences.shortcutKeys.globalLogout"
          v-model:shortcut-keys-global-preferences="preferences.shortcutKeys.globalPreferences"
          v-model:shortcut-keys-global-search="preferences.shortcutKeys.globalSearch"
        />
      </template>

      <template #general>
        <PreferenceWidget
          v-model:widget-fullscreen="preferences.widget.fullscreen"
          v-model:widget-global-search="preferences.widget.globalSearch"
          v-model:widget-language-toggle="preferences.widget.languageToggle"
          v-model:widget-lock-screen="preferences.widget.lockScreen"
          v-model:widget-notification="preferences.widget.notification"
          v-model:widget-refresh="preferences.widget.refresh"
          v-model:widget-sidebar-toggle="preferences.widget.sidebarToggle"
          v-model:widget-theme-toggle="preferences.widget.themeToggle"
        />
        <PreferenceTransition
          v-model:transition-enable="preferences.transition.enable"
          v-model:transition-loading="preferences.transition.loading"
          v-model:transition-name="preferences.transition.name"
          v-model:transition-progress="preferences.transition.progress"
        />
        <PreferenceGeneral
          v-model:dynamic-title="preferences.app.dynamicTitle"
          v-model:enable-check-updates="preferences.app.enableCheckUpdates"
          v-model:locale="preferences.app.locale"
          v-model:preferences-button-position="preferences.app.preferencesButtonPosition"
          v-model:watermark="preferences.app.watermark"
        />
      </template>
    </PreferenceContent>

    <template #footer>
      <div class="flex w-full items-center justify-between">
        <VbenButton size="sm" variant="ghost" @click="handleCopy">
          复制配置
        </VbenButton>
        <VbenButton :disabled="!diffPreference" size="sm" @click="handleReset">
          重置
        </VbenButton>
      </div>
    </template>
  </VbenDrawer>
</template>
```

### 7.5 主题模式选择

**文件：`packages/effects/layouts/src/widgets/preferences/blocks/theme/theme.vue`**

```vue
<script setup lang="ts">
import type { Component } from 'vue';
import type { ThemeModeType } from '@vben/types';

import { MoonStar, Sun, SunMoon } from '@vben/icons';

const modelValue = defineModel<string>({ default: 'auto' });
const themeSemiDarkSidebar = defineModel<boolean>('themeSemiDarkSidebar');
const themeSemiDarkHeader = defineModel<boolean>('themeSemiDarkHeader');

const THEME_PRESET: Array<{ icon: Component; name: ThemeModeType }> = [
  { icon: Sun, name: 'light' },
  { icon: MoonStar, name: 'dark' },
  { icon: SunMoon, name: 'auto' },
];

function activeClass(theme: string): string[] {
  return theme === modelValue.value ? ['outline-box-active'] : [];
}

function nameView(name: string) {
  switch (name) {
    case 'auto': return '跟随系统';
    case 'dark': return '暗黑';
    case 'light': return '亮色';
  }
}
</script>

<template>
  <div class="flex w-full flex-wrap justify-between">
    <template v-for="theme in THEME_PRESET" :key="theme.name">
      <div
        class="flex cursor-pointer flex-col"
        @click="modelValue = theme.name"
      >
        <div
          :class="activeClass(theme.name)"
          class="outline-box flex-center py-4"
        >
          <component :is="theme.icon" class="mx-9 size-5" />
        </div>
        <div class="text-muted-foreground mt-2 text-center text-xs">
          {{ nameView(theme.name) }}
        </div>
      </div>
    </template>

    <SwitchItem
      v-model="themeSemiDarkSidebar"
      :disabled="modelValue === 'dark'"
      class="mt-6"
    >
      暗色侧边栏
    </SwitchItem>
    <SwitchItem v-model="themeSemiDarkHeader" :disabled="modelValue === 'dark'">
      暗色顶栏
    </SwitchItem>
  </div>
</template>
```

### 7.6 内置主题选择

**文件：`packages/effects/layouts/src/widgets/preferences/blocks/theme/builtin.vue`**

```vue
<script setup lang="ts">
import type { BuiltinThemePreset } from '@vben/preferences';
import type { BuiltinThemeType } from '@vben/types';

import { computed, ref, watch } from 'vue';

import { UserRoundPen } from '@vben/icons';
import { BUILT_IN_THEME_PRESETS } from '@vben/preferences';
import { convertToHsl, TinyColor } from '@vben/utils';

import { useThrottleFn } from '@vueuse/core';

const props = defineProps<{ isDark: boolean }>();

const colorInput = ref();
const modelValue = defineModel<BuiltinThemeType>({ default: 'default' });
const themeColorPrimary = defineModel<string>('themeColorPrimary');

const updateThemeColorPrimary = useThrottleFn(
  (value: string) => {
    themeColorPrimary.value = value;
  },
  300,
  true,
  true,
);

const inputValue = computed(() => {
  return new TinyColor(themeColorPrimary.value || '').toHexString();
});

const builtinThemePresets = computed(() => {
  return [...BUILT_IN_THEME_PRESETS];
});

function handleSelect(theme: BuiltinThemePreset) {
  modelValue.value = theme.type;
}

function handleInputChange(e: Event) {
  const target = e.target as HTMLInputElement;
  updateThemeColorPrimary(convertToHsl(target.value));
}

function selectColor() {
  colorInput.value?.[0]?.click?.();
}

watch(
  () => [modelValue.value, props.isDark] as [BuiltinThemeType, boolean],
  ([themeType, isDark]) => {
    const theme = builtinThemePresets.value.find(
      (item) => item.type === themeType,
    );
    if (theme) {
      const primaryColor = isDark
        ? theme.darkPrimaryColor || theme.primaryColor
        : theme.primaryColor;
      themeColorPrimary.value = primaryColor || theme.color;
    }
  },
);
</script>

<template>
  <div class="flex w-full flex-wrap justify-between">
    <template v-for="theme in builtinThemePresets" :key="theme.type">
      <div class="flex cursor-pointer flex-col" @click="handleSelect(theme)">
        <div
          :class="{
            'outline-box-active': theme.type === modelValue,
          }"
          class="outline-box flex-center group cursor-pointer"
        >
          <template v-if="theme.type !== 'custom'">
            <div
              :style="{ backgroundColor: theme.color }"
              class="mx-10 my-2 size-5 rounded-md"
            ></div>
          </template>
          <template v-else>
            <div class="size-full px-10 py-2" @click.stop="selectColor">
              <div class="flex-center relative size-5 rounded-sm">
                <UserRoundPen
                  class="absolute z-10 size-5 opacity-60 group-hover:opacity-100"
                />
                <input
                  ref="colorInput"
                  :value="inputValue"
                  class="absolute inset-0 opacity-0"
                  type="color"
                  @input="handleInputChange"
                />
              </div>
            </div>
          </template>
        </div>
        <div class="text-muted-foreground my-2 text-center text-xs">
          {{ theme.type }}
        </div>
      </div>
    </template>
  </div>
</template>
```

### 7.7 布局模式选择

**文件：`packages/effects/layouts/src/widgets/preferences/blocks/layout/layout.vue`**

```vue
<script setup lang="ts">
import type { Component } from 'vue';
import type { LayoutType } from '@vben/types';

import { computed } from 'vue';

const modelValue = defineModel<LayoutType>({ default: 'sidebar-nav' });

const components: Record<LayoutType, Component> = {
  'full-content': FullContent,
  'header-nav': HeaderNav,
  'mixed-nav': MixedNav,
  'sidebar-mixed-nav': SidebarMixedNav,
  'sidebar-nav': SidebarNav,
  'header-mixed-nav': HeaderMixedNav,
  'header-sidebar-nav': HeaderSidebarNav,
};

const PRESET = computed((): PresetItem[] => [
  { name: '侧边导航', tip: '经典侧边栏布局', type: 'sidebar-nav' },
  { name: '双栏', tip: '侧边栏+扩展菜单', type: 'sidebar-mixed-nav' },
  { name: '顶部导航', tip: '水平导航菜单', type: 'header-nav' },
  { name: '顶部+侧边', tip: '混合布局', type: 'header-sidebar-nav' },
  { name: '混合菜单', tip: '顶部+侧边混合', type: 'mixed-nav' },
  { name: '顶部双栏', tip: '顶部导航+双栏', type: 'header-mixed-nav' },
  { name: '全屏内容', tip: '隐藏所有导航', type: 'full-content' },
]);

function activeClass(theme: string): string[] {
  return theme === modelValue.value ? ['outline-box-active'] : [];
}
</script>

<template>
  <div class="flex w-full flex-wrap gap-5">
    <template v-for="theme in PRESET" :key="theme.name">
      <div
        class="flex w-[100px] cursor-pointer flex-col"
        @click="modelValue = theme.type"
      >
        <div :class="activeClass(theme.type)" class="outline-box flex-center">
          <component :is="components[theme.type]" />
        </div>
        <div class="text-muted-foreground flex-center hover:text-foreground mt-2 text-center text-xs">
          {{ theme.name }}
        </div>
      </div>
    </template>
  </div>
</template>
```

### 7.8 侧边栏配置

**文件：`packages/effects/layouts/src/widgets/preferences/blocks/layout/sidebar.vue`**

```vue
<script setup lang="ts">
import type { LayoutType } from '@vben/types';

import { onMounted } from 'vue';

defineProps<{ currentLayout?: LayoutType; disabled: boolean }>();

const sidebarEnable = defineModel<boolean>('sidebarEnable');
const sidebarWidth = defineModel<number>('sidebarWidth');
const sidebarCollapsedShowTitle = defineModel<boolean>('sidebarCollapsedShowTitle');
const sidebarAutoActivateChild = defineModel<boolean>('sidebarAutoActivateChild');
const sidebarCollapsed = defineModel<boolean>('sidebarCollapsed');
const sidebarExpandOnHover = defineModel<boolean>('sidebarExpandOnHover');

const sidebarButtons = defineModel<string[]>('sidebarButtons', { default: [] });
const sidebarCollapsedButton = defineModel<boolean>('sidebarCollapsedButton');
const sidebarFixedButton = defineModel<boolean>('sidebarFixedButton');

onMounted(() => {
  if (sidebarCollapsedButton.value && !sidebarButtons.value.includes('collapsed')) {
    sidebarButtons.value.push('collapsed');
  }
  if (sidebarFixedButton.value && !sidebarButtons.value.includes('fixed')) {
    sidebarButtons.value.push('fixed');
  }
});

const handleCheckboxChange = () => {
  sidebarCollapsedButton.value = !!sidebarButtons.value.includes('collapsed');
  sidebarFixedButton.value = !!sidebarButtons.value.includes('fixed');
};
</script>

<template>
  <SwitchItem v-model="sidebarEnable" :disabled="disabled">
    显示侧边栏
  </SwitchItem>
  <SwitchItem v-model="sidebarCollapsed" :disabled="!sidebarEnable || disabled">
    折叠侧边栏
  </SwitchItem>
  <SwitchItem
    v-model="sidebarExpandOnHover"
    :disabled="!sidebarEnable || disabled || !sidebarCollapsed"
  >
    悬停展开
  </SwitchItem>
  <SwitchItem
    v-model="sidebarCollapsedShowTitle"
    :disabled="!sidebarEnable || disabled || !sidebarCollapsed"
  >
    折叠时显示标题
  </SwitchItem>
  <SwitchItem
    v-model="sidebarAutoActivateChild"
    :disabled="
      !sidebarEnable ||
      !['sidebar-mixed-nav', 'mixed-nav', 'header-mixed-nav'].includes(
        currentLayout as string,
      ) ||
      disabled
    "
  >
    自动激活子菜单
  </SwitchItem>
  <CheckboxItem
    :items="[
      { label: '折叠按钮', value: 'collapsed' },
      { label: '固定按钮', value: 'fixed' },
    ]"
    multiple
    v-model="sidebarButtons"
    :on-btn-click="handleCheckboxChange"
  >
    按钮
  </CheckboxItem>
  <NumberFieldItem
    v-model="sidebarWidth"
    :disabled="!sidebarEnable || disabled"
    :max="320"
    :min="160"
    :step="10"
  >
    侧边栏宽度
  </NumberFieldItem>
</template>
```

---

## 八、初始化与使用

### 8.1 入口初始化

```typescript
// main.ts
import { createApp } from 'vue';
import App from './App.vue';
import { initPreferences } from '@vben/preferences';

// 引入 CSS 变量
import '@vben-core/design';

const app = createApp(App);

// 初始化偏好设置
initPreferences({
  namespace: 'vben-web-ele',
  overrides: {
    app: {
      name: import.meta.env.VITE_APP_TITLE,
    },
  },
});

app.mount('#app');
```

### 8.2 在组件中使用

```vue
<script setup lang="ts">
import { preferences, updatePreferences, usePreferences } from '@vben/preferences';

const { isDark, layout, sidebarCollapsed } = usePreferences();

// 切换主题
function toggleTheme() {
  updatePreferences({
    theme: { mode: isDark.value ? 'light' : 'dark' },
  });
}

// 切换布局
function changeLayout(newLayout: string) {
  updatePreferences({ app: { layout: newLayout } });
}
</script>
```

---

## 九、完整文件清单

### 9.1 核心包文件

| 文件路径 | 说明 |
|----------|------|
| `packages/@core/preferences/src/index.ts` | 导出入口 |
| `packages/@core/preferences/src/types.ts` | 类型定义 |
| `packages/@core/preferences/src/config.ts` | 默认配置 |
| `packages/@core/preferences/src/constants.ts` | 主题预设常量 |
| `packages/@core/preferences/src/preferences.ts` | 核心状态管理器 |
| `packages/@core/preferences/src/use-preferences.ts` | 组合式函数 |
| `packages/@core/preferences/src/update-css-variables.ts` | CSS 变量更新 |

### 9.2 基础工具包

| 文件路径 | 说明 |
|----------|------|
| `packages/@core/base/shared/src/cache/storage-manager.ts` | 存储管理器 |
| `packages/@core/base/shared/src/color/index.ts` | 颜色工具导出口 |
| `packages/@core/base/shared/src/color/convert.ts` | 颜色转换 |
| `packages/@core/base/shared/src/color/generator.ts` | 颜色变量生成 |
| `packages/@core/base/shared/src/utils/merge.ts` | 对象合并 |
| `packages/@core/base/shared/src/utils/diff.ts` | 对象差异对比 |
| `packages/@core/base/shared/src/utils/update-css-variables.ts` | CSS 变量注入 |

### 9.3 设计令牌

| 文件路径 | 说明 |
|----------|------|
| `packages/@core/base/design/src/design-tokens/default.css` | 亮色主题变量 |
| `packages/@core/base/design/src/design-tokens/dark.css` | 暗色主题变量 |
| `packages/@core/base/design/src/design-tokens/index.ts` | 导出入口 |

### 9.4 布局 UI 组件

| 文件路径 | 说明 |
|----------|------|
| `packages/@core/ui-kit/layout-ui/src/vben-layout.ts` | Props 类型定义 |
| `packages/@core/ui-kit/layout-ui/src/vben-layout.vue` | 布局容器组件 |
| `packages/@core/ui-kit/layout-ui/src/hooks/use-layout.ts` | 布局逻辑 Hook |
| `packages/@core/ui-kit/layout-ui/src/components/layout-sidebar.vue` | 侧边栏 |
| `packages/@core/ui-kit/layout-ui/src/components/layout-header.vue` | 头部 |
| `packages/@core/ui-kit/layout-ui/src/components/layout-content.vue` | 内容区 |
| `packages/@core/ui-kit/layout-ui/src/components/layout-footer.vue` | 页脚 |

### 9.5 布局效果组件

| 文件路径 | 说明 |
|----------|------|
| `packages/effects/layouts/src/basic/layout.vue` | 布局总装 |
| `packages/effects/layouts/src/basic/header/header.vue` | 头部组装 |
| `packages/effects/layouts/src/basic/menu/menu.vue` | 菜单组件 |
| `packages/effects/layouts/src/widgets/theme-toggle/theme-toggle.vue` | 主题切换 |
| `packages/effects/layouts/src/widgets/theme-toggle/theme-button.vue` | 主题按钮 |
| `packages/effects/layouts/src/widgets/preferences/preferences-drawer.vue` | 偏好抽屉 |
| `packages/effects/layouts/src/widgets/preferences/blocks/theme/theme.vue` | 主题模式 |
| `packages/effects/layouts/src/widgets/preferences/blocks/theme/builtin.vue` | 内置主题 |
| `packages/effects/layouts/src/widgets/preferences/blocks/layout/layout.vue` | 布局模式 |
| `packages/effects/layouts/src/widgets/preferences/blocks/layout/sidebar.vue` | 侧边栏配置 |

---

> **文档完成**。本文档包含了 `aryn-mall-ui` 主题与布局配置系统的**完整源码级实现**，涵盖类型系统、状态管理、CSS 变量、颜色工具、布局组件、UI 交互等全部模块。可直接用于在其他 Vue 3 项目中复刻一模一样的功能。
