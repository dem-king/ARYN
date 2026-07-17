# 主题与布局配置系统集成指南

> 本指南介绍如何将 `aryn-mall-ui` 中的主题切换与布局配置功能集成到另一个 Vue 3 项目中。
> 目标技术栈：Vue 3 + Vite + TypeScript（可选 Tailwind CSS）

---

## 目录

1. [架构概述](#一架构概述)
2. [核心概念](#二核心概念)
3. [集成步骤](#三集成步骤)
4. [文件清单与代码](#四文件清单与代码)
5. [使用方式](#五使用方式)
6. [进阶扩展](#六进阶扩展)
7. [常见问题](#七常见问题)

---

## 一、架构概述

```
┌─────────────────────────────────────────────────────────────┐
│                      用户交互层                               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ ThemeToggle │  │ Preferences │  │  Layout Components  │  │
│  │   主题切换   │  │   偏好抽屉   │  │     布局组件        │  │
│  └──────┬──────┘  └──────┬──────┘  └──────────┬──────────┘  │
│         └─────────────────┴────────────────────┘             │
│                           │                                  │
│                    updatePreferences()                       │
│                           │                                  │
├───────────────────────────┼──────────────────────────────────┤
│                      状态管理层                              │
│              PreferenceManager (Singleton)                   │
│              ├─ reactive state (响应式状态)                   │
│              ├─ localStorage (持久化)                         │
│              ├─ watch (监听系统主题)                          │
│              └─ updateCSSVariables (同步CSS变量)              │
│                           │                                  │
├───────────────────────────┼──────────────────────────────────┤
│                      样式应用层                              │
│              ├─ html.classList.toggle('dark')                │
│              ├─ html.dataset.theme = 'xxx'                   │
│              ├─ CSS Variables → 所有组件自动响应              │
│              └─ document.documentElement.style.setProperty   │
└─────────────────────────────────────────────────────────────┘
```

### 依赖关系图

```
你的项目
  ├── composables/usePreferences.ts      # 组合式函数（读取状态）
  ├── stores/preferenceManager.ts        # 状态管理器（核心）
  ├── components/ThemeToggle.vue         # 主题切换按钮
  ├── components/PreferencesDrawer.vue   # 偏好设置抽屉
  ├── styles/design-tokens.css           # CSS 变量定义
  └── App.vue                            # 根组件初始化
```

---

## 二、核心概念

### 2.1 配置驱动

所有主题和布局配置集中在一个 `Preferences` 对象中：

```typescript
interface Preferences {
  app: AppPreferences;        // 应用级：布局模式、语言、紧凑模式
  theme: ThemePreferences;    // 主题级：模式、主色、圆角、内置主题
  sidebar: SidebarPreferences;// 侧边栏：宽度、折叠、悬停展开
  header: HeaderPreferences;  // 顶栏：高度、隐藏、模式
  footer: FooterPreferences;  // 底栏：启用、固定、高度
  tabbar: TabbarPreferences;  // 标签栏：启用、样式、缓存
  transition: TransitionPreferences; // 动画：页面切换、进度条
  widget: WidgetPreferences;  // 功能部件：搜索、全屏、主题切换
}
```

### 2.2 CSS 变量驱动

主题切换**不依赖任何 UI 框架**，完全基于 CSS 自定义属性：

```css
:root {
  --background: 0 0% 100%;
  --foreground: 210 6% 21%;
  --primary: 212 100% 45%;
  --sidebar: 0 0% 100%;
  --header: 0 0% 100%;
  --radius: 0.5rem;
}

.dark {
  --background: 222.34deg 10.43% 12.27%;
  --foreground: 0 0% 95%;
}
```

组件中使用 `var(--primary)` 即可自动响应主题变化。

### 2.3 响应式状态

使用 Vue 3 `reactive()` 创建全局状态，所有组件通过 `computed` 自动追踪：

```typescript
// 修改状态
updatePreferences({ theme: { mode: 'dark' } });

// 任意组件自动响应
const isDark = computed(() => preferences.theme.mode === 'dark');
```

---

## 三、集成步骤

### 步骤 1：安装依赖

```bash
npm install vue@^3.4.0
npm install @vueuse/core@^10.0.0
npm install @ctrl/tinycolor@^4.0.0
npm install theme-colors@^0.1.0
npm install defu@^6.0.0
npm install lodash.clonedeep lodash.get lodash.isequal lodash.set
npm install -D @types/lodash.clonedeep @types/lodash.get @types/lodash.isequal @types/lodash.set
```

> 如果你使用 **Tailwind CSS**，确保已配置 `tailwind.config.js` 使用 CSS 变量。

---

### 步骤 2：创建类型定义

**文件：`src/types/preferences.ts`**

```typescript
export type LayoutType =
  | 'full-content'
  | 'header-mixed-nav'
  | 'header-nav'
  | 'header-sidebar-nav'
  | 'mixed-nav'
  | 'sidebar-mixed-nav'
  | 'sidebar-nav';

export type ThemeModeType = 'auto' | 'dark' | 'light';

export type BuiltinThemeType =
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

export type ContentCompactType = 'compact' | 'wide';
export type LayoutHeaderModeType = 'auto' | 'auto-scroll' | 'fixed' | 'static';
export type NavigationStyleType = 'plain' | 'rounded';

export interface AppPreferences {
  accessMode: string;
  authPageLayout: string;
  checkUpdatesInterval: number;
  colorGrayMode: boolean;
  colorWeakMode: boolean;
  compact: boolean;
  contentCompact: ContentCompactType;
  contentCompactWidth: number;
  contentPadding: number;
  contentPaddingBottom: number;
  contentPaddingLeft: number;
  contentPaddingRight: number;
  contentPaddingTop: number;
  defaultAvatar: string;
  defaultHomePath: string;
  dynamicTitle: boolean;
  enableCheckUpdates: boolean;
  enablePreferences: boolean;
  enableRefreshToken: boolean;
  isMobile: boolean;
  layout: LayoutType;
  locale: string;
  loginExpiredMode: string;
  name: string;
  preferencesButtonPosition: string;
  watermark: boolean;
  zIndex: number;
}

export interface BreadcrumbPreferences {
  enable: boolean;
  hideOnlyOne: boolean;
  showHome: boolean;
  showIcon: boolean;
  styleType: string;
}

export interface FooterPreferences {
  enable: boolean;
  fixed: boolean;
  height: number;
}

export interface HeaderPreferences {
  enable: boolean;
  height: number;
  hidden: boolean;
  menuAlign: string;
  mode: LayoutHeaderModeType;
}

export interface LogoPreferences {
  enable: boolean;
  fit: 'contain' | 'cover' | 'fill' | 'none' | 'scale-down';
  source: string;
}

export interface NavigationPreferences {
  accordion: boolean;
  split: boolean;
  styleType: NavigationStyleType;
}

export interface SidebarPreferences {
  autoActivateChild: boolean;
  collapsed: boolean;
  collapsedButton: boolean;
  collapsedShowTitle: boolean;
  collapseWidth: number;
  enable: boolean;
  expandOnHover: boolean;
  extraCollapse: boolean;
  extraCollapsedWidth: number;
  fixedButton: boolean;
  hidden: boolean;
  mixedWidth: number;
  width: number;
}

export interface TabbarPreferences {
  draggable: boolean;
  enable: boolean;
  height: number;
  keepAlive: boolean;
  maxCount: number;
  middleClickToClose: boolean;
  persist: boolean;
  showIcon: boolean;
  showMaximize: boolean;
  showMore: boolean;
  styleType: string;
  wheelable: boolean;
}

export interface ThemePreferences {
  builtinType: BuiltinThemeType;
  colorDestructive: string;
  colorPrimary: string;
  colorSuccess: string;
  colorWarning: string;
  mode: ThemeModeType;
  radius: string;
  semiDarkHeader: boolean;
  semiDarkSidebar: boolean;
}

export interface TransitionPreferences {
  enable: boolean;
  loading: boolean;
  name: string;
  progress: boolean;
}

export interface WidgetPreferences {
  fullscreen: boolean;
  globalSearch: boolean;
  languageToggle: boolean;
  lockScreen: boolean;
  notification: boolean;
  refresh: boolean;
  sidebarToggle: boolean;
  themeToggle: boolean;
}

export interface Preferences {
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

export type DeepPartial<T> = {
  [P in keyof T]?: T[P] extends object ? DeepPartial<T[P]> : T[P];
};

export interface InitialOptions {
  namespace: string;
  overrides?: DeepPartial<Preferences>;
}
```

---

### 步骤 3：创建工具函数

#### 3.1 存储管理器

**文件：`src/utils/storage-manager.ts`**

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

#### 3.2 颜色工具

**文件：`src/utils/color.ts`**

```typescript
import { TinyColor } from '@ctrl/tinycolor';
import { getColors } from 'theme-colors';

interface ColorItem {
  alias?: string;
  color: string;
  name: string;
}

export function convertToHslCssVar(color: string): string {
  const { a, h, l, s } = new TinyColor(color).toHsl();
  const hsl = `${Math.round(h)} ${Math.round(s * 100)}% ${Math.round(l * 100)}%`;
  return a < 1 ? `${hsl} / ${a}` : hsl;
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

export { TinyColor };
```

#### 3.3 CSS 变量更新

**文件：`src/utils/update-css-variables.ts`**

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

#### 3.4 对象合并与对比

**文件：`src/utils/object.ts`**

```typescript
import { defu } from 'defu';

export const merge = defu;

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

---

### 步骤 4：创建主题配置

#### 4.1 默认配置

**文件：`src/config/preferences.ts`**

```typescript
import type { Preferences } from '@/types/preferences';

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
    name: 'My App',
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

#### 4.2 主题预设

**文件：`src/config/theme-presets.ts`**

```typescript
import type { BuiltinThemeType } from '@/types/preferences';

interface BuiltinThemePreset {
  color: string;
  darkPrimaryColor?: string;
  primaryColor?: string;
  type: BuiltinThemeType;
}

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

### 步骤 5：创建核心状态管理器

#### 5.1 CSS 变量更新逻辑

**文件：`src/composables/update-css-variables.ts`**

```typescript
import type { Preferences } from '@/types/preferences';

import { BUILT_IN_THEME_PRESETS } from '@/config/theme-presets';
import { generatorColorVariables, updateCSSVariables as executeUpdateCSSVariables } from '@/utils/color';

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

#### 5.2 偏好设置管理器

**文件：`src/composables/preference-manager.ts`**

```typescript
import type { DeepPartial, InitialOptions, Preferences } from '@/types/preferences';

import { markRaw, reactive, readonly, watch } from 'vue';
import { useBreakpoints, useDebounceFn } from '@vueuse/core';

import { defaultPreferences } from '@/config/preferences';
import { StorageManager } from '@/utils/storage-manager';
import { merge } from '@/utils/object';
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

#### 5.3 组合式函数

**文件：`src/composables/use-preferences.ts`**

```typescript
import { computed } from 'vue';
import { diff } from '@/utils/object';
import { preferencesManager } from './preference-manager';
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

#### 5.4 导出入口

**文件：`src/composables/preferences.ts`**

```typescript
import { preferencesManager } from './preference-manager';

export const preferences = preferencesManager.getPreferences();
export const updatePreferences = preferencesManager.updatePreferences.bind(preferencesManager);
export const resetPreferences = preferencesManager.resetPreferences.bind(preferencesManager);
export const clearPreferencesCache = preferencesManager.clearCache.bind(preferencesManager);
export const initPreferences = preferencesManager.initPreferences.bind(preferencesManager);
export { preferencesManager };
export * from './use-preferences';
```

---

### 步骤 6：创建 CSS 变量文件

**文件：`src/styles/design-tokens.css`**

```css
:root {
  --popup-z-index: 2000;
  --font-family:
    -apple-system, blinkmacsystemfont, 'Segoe UI', roboto, 'Helvetica Neue',
    arial, 'Noto Sans', sans-serif;

  /* 基础颜色 */
  --background: 0 0% 100%;
  --background-deep: 216 20.11% 95.47%;
  --foreground: 210 6% 21%;

  /* 卡片 */
  --card: 0 0% 100%;
  --card-foreground: 222.2 84% 4.9%;

  /* 弹出层 */
  --popover: 0 0% 100%;
  --popover-foreground: 222.2 84% 4.9%;

  /* 次要 */
  --muted: 240 4.8% 95.9%;
  --muted-foreground: 240 3.8% 46.1%;

  /* 主题色 */
  --primary: 212 100% 45%;
  --primary-foreground: 0 0% 98%;

  /* 功能色 */
  --destructive: 359.33 100% 65.1%;
  --destructive-foreground: 0 0% 98%;
  --info: 240, 5%, 96%;
  --info-foreground: 220, 4%, 58%;
  --success: 144 57% 58%;
  --success-foreground: 0 0% 98%;
  --warning: 42 84% 61%;
  --warning-foreground: 0 0% 98%;

  /* 次要按钮 */
  --secondary: 240 5% 96%;
  --secondary-foreground: 240 6% 10%;

  /* 强调 */
  --accent: 240 5% 96%;
  --accent-dark: 216 14% 93%;
  --accent-darker: 216 11% 91%;
  --accent-lighter: 240 0% 98%;
  --accent-hover: 200deg 10% 90%;
  --accent-foreground: 240 6% 10%;

  /* 边框 */
  --border: 240 5.9% 90%;
  --input: 240deg 5.88% 90%;
  --input-placeholder: 217 10.6% 65%;
  --input-background: 0 0% 100%;
  --ring: 222.2 84% 4.9%;

  /* 圆角 */
  --radius: 0.5rem;

  /* 遮罩 */
  --overlay: 0 0% 0% / 45%;
  --overlay-content: 0 0% 95% / 45%;

  /* 字体 */
  --font-size-base: 16px;

  /* 组件 */
  --sidebar: 0 0% 100%;
  --sidebar-deep: 0 0% 100%;
  --menu: var(--sidebar);
  --header: 0 0% 100%;

  accent-color: var(--primary);
  color-scheme: light;
}

/* 暗黑模式 */
.dark {
  --background: 222.34deg 10.43% 12.27%;
  --background-deep: 220deg 13.06% 9%;
  --foreground: 0 0% 95%;
  --card: 222.34deg 10.43% 12.27%;
  --card-foreground: 210 40% 98%;
  --popover: 0 0% 14.2%;
  --popover-foreground: 210 40% 98%;
  --muted: 240 3.7% 15.9%;
  --muted-foreground: 240 5% 64.9%;
  --primary-foreground: 0 0% 98%;
  --destructive: 359.21 68.47% 56.47%;
  --destructive-foreground: 0 0% 98%;
  --info: 180, 1.54%, 12.75%;
  --info-foreground: 220, 4%, 58%;
  --success: 144 57% 58%;
  --success-foreground: 0 0% 98%;
  --warning: 42 84% 61%;
  --warning-foreground: 0 0% 98%;
  --secondary: 240 5% 17%;
  --secondary-foreground: 0 0% 98%;
  --accent: 216 5% 19%;
  --accent-dark: 240 0% 22%;
  --accent-darker: 240 0% 26%;
  --accent-lighter: 216 5% 12%;
  --accent-hover: 216 5% 24%;
  --accent-foreground: 0 0% 98%;
  --heavy: 216 5% 24%;
  --heavy-foreground: var(--accent-foreground);
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

/* 灰色模式 */
.grayscale-mode {
  filter: grayscale(100%);
}

/* 色弱模式 */
.invert-mode {
  filter: invert(80%);
}
```

---

### 步骤 7：创建 UI 组件

#### 7.1 主题切换按钮

**文件：`src/components/ThemeToggle.vue`**

```vue
<script setup lang="ts">
import { computed } from 'vue';
import { preferences, updatePreferences, usePreferences } from '@/composables/preferences';

const { isDark } = usePreferences();

function handleChange() {
  updatePreferences({
    theme: { mode: isDark.value ? 'light' : 'dark' },
  });
}

const PRESETS = [
  { name: 'light', title: '亮色' },
  { name: 'dark', title: '暗黑' },
  { name: 'auto', title: '跟随系统' },
];
</script>

<template>
  <div class="theme-toggle">
    <!-- 简化版：切换按钮 -->
    <button
      class="toggle-btn"
      @click="handleChange"
    >
      <span v-if="isDark">🌙</span>
      <span v-else>☀️</span>
    </button>

    <!-- 完整版：选择器 -->
    <div class="theme-options">
      <button
        v-for="item in PRESETS"
        :key="item.name"
        :class="['theme-btn', { active: preferences.theme.mode === item.name }]"
        @click="updatePreferences({ theme: { mode: item.name as any } })"
      >
        {{ item.title }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.theme-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toggle-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid hsl(var(--border));
  background: hsl(var(--background));
  cursor: pointer;
  font-size: 18px;
}

.theme-options {
  display: flex;
  gap: 4px;
}

.theme-btn {
  padding: 4px 12px;
  border-radius: 4px;
  border: 1px solid hsl(var(--border));
  background: hsl(var(--background));
  color: hsl(var(--foreground));
  cursor: pointer;
  font-size: 12px;
}

.theme-btn.active {
  background: hsl(var(--primary));
  color: hsl(var(--primary-foreground));
}
</style>
```

#### 7.2 布局切换组件

**文件：`src/components/LayoutToggle.vue`**

```vue
<script setup lang="ts">
import { preferences, updatePreferences } from '@/composables/preferences';

const LAYOUTS = [
  { name: 'sidebar-nav', title: '侧边导航' },
  { name: 'header-nav', title: '顶部导航' },
  { name: 'mixed-nav', title: '混合导航' },
  { name: 'full-content', title: '全屏内容' },
];
</script>

<template>
  <div class="layout-toggle">
    <div
      v-for="item in LAYOUTS"
      :key="item.name"
      :class="['layout-item', { active: preferences.app.layout === item.name }]"
      @click="updatePreferences({ app: { layout: item.name as any } })"
    >
      <div class="layout-preview" :class="item.name">
        <div class="preview-sidebar" v-if="item.name !== 'header-nav' && item.name !== 'full-content'"></div>
        <div class="preview-header" v-if="item.name !== 'full-content'"></div>
        <div class="preview-content"></div>
      </div>
      <span class="layout-title">{{ item.title }}</span>
    </div>
  </div>
</template>

<style scoped>
.layout-toggle {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.layout-item {
  cursor: pointer;
  padding: 8px;
  border-radius: 8px;
  border: 2px solid transparent;
  transition: all 0.2s;
}

.layout-item:hover {
  border-color: hsl(var(--border));
}

.layout-item.active {
  border-color: hsl(var(--primary));
}

.layout-preview {
  width: 100%;
  height: 60px;
  background: hsl(var(--muted));
  border-radius: 4px;
  position: relative;
  overflow: hidden;
}

.preview-sidebar {
  position: absolute;
  left: 0;
  top: 0;
  width: 25%;
  height: 100%;
  background: hsl(var(--primary) / 0.3);
}

.preview-header {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 25%;
  background: hsl(var(--primary) / 0.3);
}

.preview-content {
  position: absolute;
  right: 4px;
  bottom: 4px;
  width: 60%;
  height: 50%;
  background: hsl(var(--background));
}

.layout-title {
  display: block;
  text-align: center;
  margin-top: 4px;
  font-size: 12px;
  color: hsl(var(--foreground));
}
</style>
```

#### 7.3 偏好设置抽屉

**文件：`src/components/PreferencesDrawer.vue`**

```vue
<script setup lang="ts">
import { ref } from 'vue';
import { preferences, resetPreferences, updatePreferences, usePreferences } from '@/composables/preferences';
import ThemeToggle from './ThemeToggle.vue';
import LayoutToggle from './LayoutToggle.vue';

const visible = ref(false);
const activeTab = ref('appearance');

const { diffPreference, isDark } = usePreferences();

const tabs = [
  { label: '外观', value: 'appearance' },
  { label: '布局', value: 'layout' },
  { label: '通用', value: 'general' },
];

function handleReset() {
  if (!diffPreference.value) return;
  resetPreferences();
}
</script>

<template>
  <div>
    <!-- 触发按钮 -->
    <button class="trigger-btn" @click="visible = true">⚙️ 设置</button>

    <!-- 抽屉 -->
    <Teleport to="body">
      <Transition name="slide">
        <div v-if="visible" class="drawer-overlay" @click="visible = false">
          <div class="drawer" @click.stop>
            <div class="drawer-header">
              <h3>偏好设置</h3>
              <button class="close-btn" @click="visible = false">✕</button>
            </div>

            <div class="drawer-tabs">
              <button
                v-for="tab in tabs"
                :key="tab.value"
                :class="['tab-btn', { active: activeTab === tab.value }]"
                @click="activeTab = tab.value"
              >
                {{ tab.label }}
              </button>
            </div>

            <div class="drawer-body">
              <!-- 外观 -->
              <div v-show="activeTab === 'appearance'">
                <div class="block">
                  <h4>主题模式</h4>
                  <ThemeToggle />
                </div>

                <div class="block">
                  <h4>圆角大小</h4>
                  <input
                    type="range"
                    min="0"
                    max="1"
                    step="0.1"
                    :value="preferences.theme.radius"
                    @input="updatePreferences({ theme: { radius: ($event.target as HTMLInputElement).value } })"
                  />
                  <span>{{ preferences.theme.radius }}rem</span>
                </div>

                <div class="block">
                  <h4>辅助功能</h4>
                  <label>
                    <input
                      type="checkbox"
                      :checked="preferences.app.colorGrayMode"
                      @change="updatePreferences({ app: { colorGrayMode: ($event.target as HTMLInputElement).checked } })"
                    />
                    灰色模式
                  </label>
                  <label>
                    <input
                      type="checkbox"
                      :checked="preferences.app.colorWeakMode"
                      @change="updatePreferences({ app: { colorWeakMode: ($event.target as HTMLInputElement).checked } })"
                    />
                    色弱模式
                  </label>
                </div>
              </div>

              <!-- 布局 -->
              <div v-show="activeTab === 'layout'">
                <div class="block">
                  <h4>布局模式</h4>
                  <LayoutToggle />
                </div>

                <div class="block">
                  <h4>侧边栏</h4>
                  <label>
                    <input
                      type="checkbox"
                      :checked="preferences.sidebar.collapsed"
                      @change="updatePreferences({ sidebar: { collapsed: ($event.target as HTMLInputElement).checked } })"
                    />
                    折叠侧边栏
                  </label>
                  <label>
                    <input
                      type="checkbox"
                      :checked="preferences.sidebar.expandOnHover"
                      @change="updatePreferences({ sidebar: { expandOnHover: ($event.target as HTMLInputElement).checked } })"
                    />
                    悬停展开
                  </label>
                </div>

                <div class="block">
                  <h4>功能部件</h4>
                  <label>
                    <input
                      type="checkbox"
                      :checked="preferences.widget.themeToggle"
                      @change="updatePreferences({ widget: { themeToggle: ($event.target as HTMLInputElement).checked } })"
                    />
                    显示主题切换
                  </label>
                  <label>
                    <input
                      type="checkbox"
                      :checked="preferences.widget.fullscreen"
                      @change="updatePreferences({ widget: { fullscreen: ($event.target as HTMLInputElement).checked } })"
                    />
                    显示全屏按钮
                  </label>
                </div>
              </div>

              <!-- 通用 -->
              <div v-show="activeTab === 'general'">
                <div class="block">
                  <h4>动画</h4>
                  <label>
                    <input
                      type="checkbox"
                      :checked="preferences.transition.enable"
                      @change="updatePreferences({ transition: { enable: ($event.target as HTMLInputElement).checked } })"
                    />
                    启用页面切换动画
                  </label>
                </div>
              </div>
            </div>

            <div class="drawer-footer">
              <button
                :disabled="!diffPreference"
                class="reset-btn"
                @click="handleReset"
              >
                重置
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.trigger-btn {
  padding: 8px 16px;
  border-radius: 6px;
  border: 1px solid hsl(var(--border));
  background: hsl(var(--background));
  color: hsl(var(--foreground));
  cursor: pointer;
}

.drawer-overlay {
  position: fixed;
  inset: 0;
  background: hsl(var(--overlay));
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
}

.drawer {
  width: 320px;
  height: 100%;
  background: hsl(var(--background));
  display: flex;
  flex-direction: column;
}

.drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid hsl(var(--border));
}

.drawer-header h3 {
  margin: 0;
  font-size: 16px;
}

.close-btn {
  background: none;
  border: none;
  font-size: 18px;
  cursor: pointer;
  color: hsl(var(--foreground));
}

.drawer-tabs {
  display: flex;
  gap: 4px;
  padding: 8px 16px;
  border-bottom: 1px solid hsl(var(--border));
}

.tab-btn {
  padding: 6px 12px;
  border-radius: 4px;
  border: none;
  background: transparent;
  color: hsl(var(--foreground));
  cursor: pointer;
  font-size: 13px;
}

.tab-btn.active {
  background: hsl(var(--primary));
  color: hsl(var(--primary-foreground));
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.block {
  margin-bottom: 20px;
}

.block h4 {
  margin: 0 0 12px;
  font-size: 14px;
  color: hsl(var(--muted-foreground));
}

.block label {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
  cursor: pointer;
}

.drawer-footer {
  padding: 16px;
  border-top: 1px solid hsl(var(--border));
}

.reset-btn {
  width: 100%;
  padding: 10px;
  border-radius: 6px;
  border: 1px solid hsl(var(--border));
  background: hsl(var(--background));
  color: hsl(var(--foreground));
  cursor: pointer;
}

.reset-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 动画 */
.slide-enter-active,
.slide-leave-active {
  transition: opacity 0.3s;
}

.slide-enter-active .drawer,
.slide-leave-active .drawer {
  transition: transform 0.3s;
}

.slide-enter-from,
.slide-leave-to {
  opacity: 0;
}

.slide-enter-from .drawer,
.slide-leave-to .drawer {
  transform: translateX(100%);
}
</style>
```

---

### 步骤 8：在应用中使用

#### 8.1 初始化

**文件：`src/main.ts`**

```typescript
import { createApp } from 'vue';
import App from './App.vue';
import { initPreferences } from './composables/preferences';

// 引入 CSS 变量
import './styles/design-tokens.css';

const app = createApp(App);

// 初始化偏好设置（在挂载前）
initPreferences({
  namespace: 'my-app',
  overrides: {
    app: {
      name: 'My Application',
    },
    theme: {
      mode: 'light',
    },
  },
});

app.mount('#app');
```

#### 8.2 根组件

**文件：`src/App.vue`**

```vue
<script setup lang="ts">
import { usePreferences } from './composables/preferences';
import ThemeToggle from './components/ThemeToggle.vue';
import PreferencesDrawer from './components/PreferencesDrawer.vue';

const { theme, layout, isSideMode, sidebarCollapsed } = usePreferences();
</script>

<template>
  <div :class="['app-wrapper', theme]">
    <!-- 布局容器 -->
    <div :class="['layout', layout]">
      <!-- 侧边栏 -->
      <aside
        v-if="isSideMode"
        :class="['sidebar', { collapsed: sidebarCollapsed }]"
      >
        <div class="logo">Logo</div>
        <nav class="menu">
          <a href="#">菜单 1</a>
          <a href="#">菜单 2</a>
          <a href="#">菜单 3</a>
        </nav>
      </aside>

      <!-- 主内容区 -->
      <div class="main">
        <!-- 顶部栏 -->
        <header class="header">
          <div class="header-left">
            <button v-if="isSideMode" @click="sidebarCollapsed = !sidebarCollapsed">
              ☰
            </button>
            <span>面包屑 / 路径</span>
          </div>
          <div class="header-right">
            <ThemeToggle />
            <PreferencesDrawer />
          </div>
        </header>

        <!-- 内容 -->
        <main class="content">
          <h1>页面内容</h1>
          <p>当前主题: {{ theme }}</p>
          <p>当前布局: {{ layout }}</p>
        </main>
      </div>
    </div>
  </div>
</template>

<style>
/* 基础布局样式 */
.app-wrapper {
  min-height: 100vh;
  background: hsl(var(--background));
  color: hsl(var(--foreground));
}

.layout {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  width: 224px;
  background: hsl(var(--sidebar));
  border-right: 1px solid hsl(var(--border));
  transition: width 0.3s;
}

.sidebar.collapsed {
  width: 60px;
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.header {
  height: 50px;
  background: hsl(var(--header));
  border-bottom: 1px solid hsl(var(--border));
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.content {
  flex: 1;
  padding: 16px;
}

/* 顶部导航布局 */
.layout.header-nav {
  flex-direction: column;
}

.layout.header-nav .sidebar {
  display: none;
}

/* 全屏内容布局 */
.layout.full-content .sidebar,
.layout.full-content .header {
  display: none;
}
</style>
```

---

## 四、文件清单与代码

### 完整文件结构

```
src/
├── types/
│   └── preferences.ts              # 类型定义
├── config/
│   ├── preferences.ts              # 默认配置
│   └── theme-presets.ts            # 主题预设
├── utils/
│   ├── storage-manager.ts          # 存储管理
│   ├── color.ts                    # 颜色工具
│   ├── update-css-variables.ts     # CSS 变量更新
│   └── object.ts                   # 对象合并/对比
├── composables/
│   ├── preference-manager.ts       # 核心状态管理器
│   ├── update-css-variables.ts     # 主题 CSS 变量逻辑
│   ├── use-preferences.ts          # 组合式函数
│   └── preferences.ts              # 导出入口
├── components/
│   ├── ThemeToggle.vue             # 主题切换按钮
│   ├── LayoutToggle.vue            # 布局切换
│   └── PreferencesDrawer.vue       # 偏好设置抽屉
├── styles/
│   └── design-tokens.css           # CSS 变量定义
├── App.vue                         # 根组件
└── main.ts                         # 入口文件
```

---

## 五、使用方式

### 5.1 在任意组件中读取配置

```vue
<script setup lang="ts">
import { usePreferences } from '@/composables/preferences';

const { isDark, layout, sidebarCollapsed, theme } = usePreferences();
</script>

<template>
  <div :class="{ 'dark-mode': isDark }">
    当前布局: {{ layout }}
  </div>
</template>
```

### 5.2 修改配置

```typescript
import { updatePreferences } from '@/composables/preferences';

// 切换主题
updatePreferences({ theme: { mode: 'dark' } });

// 切换布局
updatePreferences({ app: { layout: 'header-nav' } });

// 折叠侧边栏
updatePreferences({ sidebar: { collapsed: true } });

// 批量修改
updatePreferences({
  theme: { mode: 'dark', radius: '0.75' },
  sidebar: { collapsed: true },
});
```

### 5.3 监听配置变化

```vue
<script setup lang="ts">
import { watch } from 'vue';
import { preferences } from '@/composables/preferences';

watch(
  () => preferences.theme.mode,
  (newMode) => {
    console.log('主题切换为:', newMode);
  },
);
</script>
```

---

## 六、进阶扩展

### 6.1 添加自定义主题色

在 `src/config/theme-presets.ts` 中添加：

```typescript
export const BUILT_IN_THEME_PRESETS: BuiltinThemePreset[] = [
  // ... 现有预设
  {
    color: 'hsl(280 80% 50%)',
    type: 'purple',
  },
];
```

在 `src/styles/design-tokens.css` 中添加对应的 CSS：

```css
[data-theme='purple'] {
  --primary: 280 80% 50%;
  --ring: 280 80% 50%;
}
```

### 6.2 添加新的布局模式

1. 在 `src/types/preferences.ts` 的 `LayoutType` 中添加新类型
2. 在 `src/composables/use-preferences.ts` 中添加对应的 `computed`
3. 在 `src/App.vue` 中实现新布局的渲染逻辑

### 6.3 与 Tailwind CSS 集成

在 `tailwind.config.js` 中使用 CSS 变量：

```javascript
module.exports = {
  theme: {
    extend: {
      colors: {
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))',
        },
        border: 'hsl(var(--border))',
        // ... 更多
      },
      borderRadius: {
        DEFAULT: 'var(--radius)',
      },
    },
  },
};
```

然后组件中可以直接使用 Tailwind 类：

```html
<div class="bg-background text-foreground border-border">
  内容
</div>
```

### 6.4 服务端渲染 (SSR) 支持

在 SSR 场景下，需要在服务端注入初始主题：

```typescript
// 服务端入口
const theme = req.cookies.theme || 'light';
const html = template.replace(
  '<html>',
  `<html class="${theme === 'dark' ? 'dark' : ''}" data-theme="default">`,
);
```

---

## 七、常见问题

### Q1: 主题切换后部分组件没有响应？

确保组件使用了 CSS 变量而不是硬编码颜色：

```css
/* ✅ 正确 */
.my-component {
  background: hsl(var(--background));
  color: hsl(var(--foreground));
}

/* ❌ 错误 */
.my-component {
  background: #ffffff;
  color: #333333;
}
```

### Q2: 配置没有持久化？

检查 `initPreferences` 是否传入了 `namespace`：

```typescript
initPreferences({
  namespace: 'my-app', // 必须
});
```

### Q3: 如何禁用系统主题自动跟随？

移除 `setupWatcher` 中的媒体查询监听代码，或设置主题为固定值：

```typescript
updatePreferences({ theme: { mode: 'light' } }); // 固定为亮色
```

### Q4: 如何扩展配置项？

1. 在 `src/types/preferences.ts` 中添加新接口字段
2. 在 `src/config/preferences.ts` 的 `defaultPreferences` 中添加默认值
3. 在组件中使用 `updatePreferences` 修改新配置

### Q5: 与 Pinia/Vuex 如何共存？

本系统使用独立的响应式对象，与 Pinia/Vuex 不冲突。如果需要集成到 Pinia：

```typescript
// stores/preferences.ts
import { defineStore } from 'pinia';
import { preferences, updatePreferences } from '@/composables/preferences';

export const usePreferenceStore = defineStore('preferences', () => {
  return {
    preferences,
    updatePreferences,
  };
});
```

---

## 附录：核心 API 速查

| API | 类型 | 说明 |
|-----|------|------|
| `initPreferences(options)` | Function | 初始化偏好设置 |
| `preferences` | Reactive | 全局响应式配置对象 |
| `updatePreferences(updates)` | Function | 更新配置（支持部分更新） |
| `resetPreferences()` | Function | 重置为默认值 |
| `usePreferences()` | Composable | 获取计算属性 |
| `isDark` | Computed | 是否为暗黑模式 |
| `layout` | Computed | 当前布局模式 |
| `theme` | Computed | 当前主题 'dark' \| 'light' |

---

> **提示**：本指南提供了最小可用的实现。原系统中的 `VbenAdminLayout` 组件、菜单系统、标签页系统等属于更复杂的布局组件，如需完整功能可参考原项目源码进一步集成。
