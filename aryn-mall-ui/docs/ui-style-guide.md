# 悦航购 UI 风格复刻指南

> 本文档详细分析 `aryn-mall-ui` 项目的 UI 视觉风格，并提供在新项目中复刻该风格的完整方案。

---

## 一、UI 风格总览

### 1.1 风格定位

| 维度 | 特征 |
|------|------|
| **设计语言** | 现代极简 + 企业级管理后台 |
| **组件体系** | Shadcn UI (Radix Vue) + Tailwind CSS + 自定义扩展 |
| **色彩模式** | HSL CSS 变量驱动，支持亮色/暗色/自动切换 |
| **圆角风格** | 中等圆角 (0.5rem 默认)，可通过配置调整 |
| **阴影风格** | 极轻微阴影，以边框分隔为主 |
| **布局密度** | 中等密度，留白适中 |

### 1.2 视觉特征关键词

- **干净清爽**：大量留白，无多余装饰
- **层次分明**：通过背景色深浅区分层级（背景 → 卡片 → 弹出层）
- **色彩克制**：以中性色为主，主题色仅用于强调
- **交互细腻**：悬停、聚焦状态过渡平滑
- **响应式**：移动端自动切换为侧边栏导航

---

## 二、技术栈分析

### 2.1 核心依赖

```json
{
  "vue": "^3.4.21",
  "tailwindcss": "^3.4.1",
  "radix-vue": "^1.9.0",
  "class-variance-authority": "^0.7.0",
  "clsx": "^2.1.1",
  "tailwind-merge": "^2.3.0",
  "tailwindcss-animate": "^1.0.7",
  "@tailwindcss/typography": "^0.5.13",
  "@iconify/tailwind": "^0.1.4"
}
```

### 2.2 架构特点

| 层级 | 技术 | 说明 |
|------|------|------|
| **基础样式** | Tailwind CSS + 自定义 CSS 变量 | 原子化 CSS + HSL 变量 |
| **组件基座** | Radix Vue | 无样式、可访问性优先的 headless 组件 |
| **组件样式** | CVA (class-variance-authority) | 类型安全的变体样式管理 |
| **类名合并** | `cn()` = `clsx` + `tailwind-merge` | 条件类名 + 冲突解决 |
| **图标** | @iconify/tailwind | 动态图标选择器 |
| **动画** | tailwindcss-animate | 预设动画（accordion、collapsible 等） |

---

## 三、设计令牌 (Design Tokens)

### 3.1 CSS 变量体系

所有颜色通过 **HSL 格式**的 CSS 变量定义：

```css
:root {
  /* 基础表面色 */
  --background: 0 0% 100%;           /* 页面背景 */
  --background-deep: 216 20.11% 95.47%; /* 深层背景 */
  --foreground: 210 6% 21%;          /* 主文本 */

  /* 卡片/容器 */
  --card: 0 0% 100%;
  --card-foreground: 222.2 84% 4.9%;

  /* 弹出层 */
  --popover: 0 0% 100%;
  --popover-foreground: 222.2 84% 4.9%;

  /* 主题色 */
  --primary: 212 100% 45%;
  --primary-foreground: 0 0% 98%;

  /* 次要色 */
  --secondary: 240 5% 96%;
  --secondary-foreground: 240 6% 10%;

  /* 强调色 */
  --accent: 240 5% 96%;
  --accent-foreground: 240 6% 10%;
  --accent-hover: 200deg 10% 90%;

  /* 功能色 */
  --destructive: 359.33 100% 65.1%;
  --success: 144 57% 58%;
  --warning: 42 84% 61%;

  /* 边框/输入 */
  --border: 240 5.9% 90%;
  --input: 240deg 5.88% 90%;
  --ring: 222.2 84% 4.9%;

  /* 圆角 */
  --radius: 0.5rem;

  /* 布局 */
  --sidebar: 0 0% 100%;
  --header: 0 0% 100%;
}
```

### 3.2 暗色主题覆盖

```css
.dark {
  --background: 222.34deg 10.43% 12.27%;
  --foreground: 0 0% 95%;
  --card: 222.34deg 10.43% 12.27%;
  --popover: 0 0% 14.2%;
  --border: 240 3.7% 22%;
  --sidebar: 222.34deg 10.43% 12.27%;
  --header: 222.34deg 10.43% 12.27%;
  color-scheme: dark;
}
```

### 3.3 使用方式

```html
<!-- 背景色 -->
<div class="bg-background text-foreground">

<!-- 主按钮 -->
<button class="bg-primary text-primary-foreground hover:bg-primary/90">

<!-- 卡片 -->
<div class="bg-card text-card-foreground border border-border rounded-lg">

<!-- 输入框 -->
<input class="bg-background border border-input rounded-md">
```

---

## 四、Tailwind 配置

### 4.1 完整配置

```typescript
// tailwind.config.ts
import type { Config } from 'tailwindcss';

import animate from 'tailwindcss-animate';
import typographyPlugin from '@tailwindcss/typography';

function createColorsPalette(name: string) {
  return {
    50: `hsl(var(--${name}-50))`,
    100: `hsl(var(--${name}-100))`,
    200: `hsl(var(--${name}-200))`,
    300: `hsl(var(--${name}-300))`,
    400: `hsl(var(--${name}-400))`,
    500: `hsl(var(--${name}-500))`,
    600: `hsl(var(--${name}-600))`,
    700: `hsl(var(--${name}-700))`,
    active: `hsl(var(--${name}-700))`,
    'background-light': `hsl(var(--${name}-200))`,
    'background-lighter': `hsl(var(--${name}-100))`,
    'background-lightest': `hsl(var(--${name}-50))`,
    border: `hsl(var(--${name}-400))`,
    'border-light': `hsl(var(--${name}-300))`,
    foreground: `hsl(var(--${name}-foreground))`,
    hover: `hsl(var(--${name}-600))`,
    text: `hsl(var(--${name}-500))`,
    'text-active': `hsl(var(--${name}-700))`,
    'text-hover': `hsl(var(--${name}-600))`,
  };
}

const shadcnUiColors = {
  accent: {
    DEFAULT: 'hsl(var(--accent))',
    foreground: 'hsl(var(--accent-foreground))',
    hover: 'hsl(var(--accent-hover))',
    lighter: 'hsl(var(--accent-lighter))',
  },
  background: {
    deep: 'hsl(var(--background-deep))',
    DEFAULT: 'hsl(var(--background))',
  },
  border: { DEFAULT: 'hsl(var(--border))' },
  card: {
    DEFAULT: 'hsl(var(--card))',
    foreground: 'hsl(var(--card-foreground))',
  },
  destructive: {
    ...createColorsPalette('destructive'),
    DEFAULT: 'hsl(var(--destructive))',
  },
  foreground: { DEFAULT: 'hsl(var(--foreground))' },
  input: {
    background: 'hsl(var(--input-background))',
    DEFAULT: 'hsl(var(--input))',
  },
  muted: {
    DEFAULT: 'hsl(var(--muted))',
    foreground: 'hsl(var(--muted-foreground))',
  },
  popover: {
    DEFAULT: 'hsl(var(--popover))',
    foreground: 'hsl(var(--popover-foreground))',
  },
  primary: {
    ...createColorsPalette('primary'),
    DEFAULT: 'hsl(var(--primary))',
  },
  ring: 'hsl(var(--ring))',
  secondary: {
    DEFAULT: 'hsl(var(--secondary))',
    desc: 'hsl(var(--secondary-desc))',
    foreground: 'hsl(var(--secondary-foreground))',
  },
};

const customColors = {
  green: { ...createColorsPalette('green'), foreground: 'hsl(var(--success-foreground))' },
  header: { DEFAULT: 'hsl(var(--header))' },
  heavy: { DEFAULT: 'hsl(var(--heavy))', foreground: 'hsl(var(--heavy-foreground))' },
  main: { DEFAULT: 'hsl(var(--main))' },
  overlay: { content: 'hsl(var(--overlay-content))', DEFAULT: 'hsl(var(--overlay))' },
  red: { ...createColorsPalette('red'), foreground: 'hsl(var(--destructive-foreground))' },
  sidebar: { deep: 'hsl(var(--sidebar-deep))', DEFAULT: 'hsl(var(--sidebar))' },
  success: { ...createColorsPalette('success'), DEFAULT: 'hsl(var(--success))' },
  warning: { ...createColorsPalette('warning'), DEFAULT: 'hsl(var(--warning))' },
  yellow: { ...createColorsPalette('yellow'), foreground: 'hsl(var(--warning-foreground))' },
};

export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  darkMode: 'selector',
  plugins: [animate, typographyPlugin],
  prefix: '',
  theme: {
    container: {
      center: true,
      padding: '2rem',
      screens: { '2xl': '1400px' },
    },
    extend: {
      animation: {
        'accordion-down': 'accordion-down 0.2s ease-out',
        'accordion-up': 'accordion-up 0.2s ease-out',
        float: 'float 5s linear 0ms infinite',
      },
      borderRadius: {
        lg: 'var(--radius)',
        md: 'calc(var(--radius) - 2px)',
        sm: 'calc(var(--radius) - 4px)',
        xl: 'calc(var(--radius) + 4px)',
      },
      boxShadow: {
        float: `0 6px 16px 0 rgb(0 0 0 / 8%),
          0 3px 6px -4px rgb(0 0 0 / 12%),
          0 9px 28px 8px rgb(0 0 0 / 5%)`,
      },
      colors: { ...customColors, ...shadcnUiColors },
      fontFamily: {
        sans: ['var(--font-family)', 'system-ui', 'sans-serif'],
      },
      keyframes: {
        'accordion-down': {
          from: { height: '0' },
          to: { height: 'var(--radix-accordion-content-height)' },
        },
        'accordion-up': {
          from: { height: 'var(--radix-accordion-content-height)' },
          to: { height: '0' },
        },
        float: {
          '0%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-20px)' },
          '100%': { transform: 'translateY(0)' },
        },
      },
      zIndex: { '100': '100', '1000': '1000' },
    },
  },
  safelist: ['dark'],
} as Config;
```

### 4.2 关键配置说明

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `darkMode` | `'selector'` | 通过 `.dark` 类切换暗色模式 |
| `colors` | HSL 变量 | 所有颜色引用 CSS 变量 |
| `borderRadius` | `var(--radius)` | 圆角通过 CSS 变量控制 |
| `fontFamily` | `var(--font-family)` | 字体通过 CSS 变量控制 |
| `safelist` | `['dark']` | 确保 dark 类不被 tree-shake |

---

## 五、组件样式规范

### 5.1 按钮 (Button)

**技术实现**：CVA (class-variance-authority)

```typescript
// button.ts
import { cva } from 'class-variance-authority';

export const buttonVariants = cva(
  // 基础样式
  'inline-flex items-center justify-center whitespace-nowrap rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      variant: {
        default: 'bg-primary text-primary-foreground shadow hover:bg-primary/90',
        destructive: 'bg-destructive text-destructive-foreground shadow-sm hover:bg-destructive/90',
        outline: 'border border-input bg-background shadow-sm hover:bg-accent hover:text-accent-foreground',
        secondary: 'bg-secondary text-secondary-foreground shadow-sm hover:bg-secondary/80',
        ghost: 'hover:bg-accent hover:text-accent-foreground',
        link: 'text-primary underline-offset-4 hover:underline',
        dashed: 'border border-dashed hover:border-solid',
        icon: 'rounded-full',
        // 扩展变体
        'destructive-ghost': 'text-destructive hover:bg-destructive/10 hover:text-destructive',
        'destructive-outline': 'border border-destructive text-destructive hover:bg-destructive/10 hover:text-destructive',
        'primary-ghost': 'text-primary hover:bg-primary/10 hover:text-primary',
        'primary-outline': 'border border-primary text-primary hover:bg-primary/10 hover:text-primary',
        'success-ghost': 'text-success hover:bg-success/10 hover:text-success',
        'success-outline': 'border border-success text-success hover:bg-success/10 hover:text-success',
        'warning-ghost': 'text-warning hover:bg-warning/10 hover:text-warning',
        'warning-outline': 'border border-warning text-warning hover:bg-warning/10 hover:text-warning',
      },
      size: {
        default: 'h-9 px-4 py-2',
        sm: 'h-8 rounded-md px-3 text-xs',
        lg: 'h-10 rounded-md px-8',
        icon: 'h-9 w-9',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  },
);
```

**使用示例**：

```vue
<template>
  <button :class="cn(buttonVariants({ variant: 'default', size: 'sm' }))">
    默认按钮
  </button>
  <button :class="cn(buttonVariants({ variant: 'outline' }))">
    边框按钮
  </button>
  <button :class="cn(buttonVariants({ variant: 'ghost', size: 'icon' }))">
    <Icon icon="lucide:plus" />
  </button>
</template>
```

### 5.2 徽章 (Badge)

```typescript
// badge.ts
import { cva } from 'class-variance-authority';

export const badgeVariants = cva(
  'inline-flex items-center rounded-md border border-border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2',
  {
    variants: {
      variant: {
        default: 'border-transparent bg-accent hover:bg-accent text-primary-foreground shadow',
        destructive: 'border-transparent bg-destructive text-destructive-foreground shadow hover:bg-destructive-hover',
        outline: 'text-foreground',
        secondary: 'border-transparent bg-secondary text-secondary-foreground hover:bg-secondary/80',
      },
    },
    defaultVariants: { variant: 'default' },
  },
);
```

### 5.3 输入框 (Input)

```vue
<template>
  <input
    :class="cn(
      'flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50',
      props.class,
    )"
  />
</template>
```

**特征**：
- 高度 `h-9` (36px)
- 圆角 `rounded-md`
- 边框 `border-input`
- 聚焦环 `focus-visible:ring-1 focus-visible:ring-ring`
- 占位符颜色 `placeholder:text-muted-foreground`

### 5.4 开关 (Switch)

```vue
<template>
  <SwitchRoot
    class="peer inline-flex h-5 w-9 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 data-[state=checked]:bg-primary data-[state=unchecked]:bg-input"
  >
    <SwitchThumb
      class="pointer-events-none block h-4 w-4 rounded-full bg-background shadow-lg ring-0 transition-transform data-[state=checked]:translate-x-4 data-[state=unchecked]:translate-x-0"
    />
  </SwitchRoot>
</template>
```

**特征**：
- 尺寸：w-9 × h-5 (36×20px)
- 滑块：w-4 × h-4 (16×16px)
- 动画：translate-x 过渡
- 状态：`data-[state=checked]` 选择器

### 5.5 卡片 (Card)

```vue
<template>
  <div class="rounded-xl border bg-card text-card-foreground shadow">
    <div v-if="$slots.header" class="flex flex-col space-y-1.5 p-6">
      <slot name="header" />
    </div>
    <div v-if="$slots.default" class="p-6 pt-0">
      <slot />
    </div>
  </div>
</template>
```

**特征**：
- 圆角：`rounded-xl`
- 背景：`bg-card`
- 边框：`border` (默认 border-border)
- 阴影：`shadow` (极轻微)

### 5.6 表格 (Table)

```vue
<template>
  <div class="relative w-full overflow-auto">
    <table class="w-full caption-bottom text-sm">
      <thead class="[&_tr]:border-b">
        <tr class="border-b transition-colors hover:bg-muted/50 data-[state=selected]:bg-muted">
          <th class="h-10 px-2 text-left align-middle font-medium text-muted-foreground">
            表头
          </th>
        </tr>
      </thead>
      <tbody class="[&_tr:last-child]:border-0">
        <tr class="border-b transition-colors hover:bg-muted/50 data-[state=selected]:bg-muted">
          <td class="p-2 align-middle">单元格</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
```

**特征**：
- 表头：`text-muted-foreground` 灰色文字
- 行悬停：`hover:bg-muted/50`
- 边框：`border-b` 底部边框分隔
- 紧凑：`p-2` 内边距

### 5.7 头像 (Avatar)

```typescript
// avatar.ts
import { cva } from 'class-variance-authority';

export const avatarVariant = cva(
  'inline-flex items-center justify-center font-normal text-foreground select-none shrink-0 bg-secondary overflow-hidden',
  {
    variants: {
      shape: {
        circle: 'rounded-full',
        square: 'rounded-md',
      },
      size: {
        base: 'h-16 w-16 text-2xl',
        lg: 'h-32 w-32 text-5xl',
        sm: 'h-10 w-10 text-xs',
      },
    },
  },
);
```

---

## 六、布局风格

### 6.1 管理后台布局

```
┌─────────────────────────────────────────┐
│  Sidebar (224px)  │  Header (50px)      │
│                   ├─────────────────────┤
│  Logo             │                     │
│  ──────────────── │  Breadcrumb         │
│  Menu Item        │                     │
│  Menu Item        ├─────────────────────┤
│  Menu Item        │                     │
│  Menu Item        │  Content            │
│  Menu Item        │  (padding: 16px)    │
│  Menu Item        │                     │
│  ──────────────── │                     │
│  Collapse Btn     │                     │
└───────────────────┴─────────────────────┘
```

### 6.2 布局特征

| 元素 | 尺寸/样式 |
|------|----------|
| 侧边栏宽度 | 224px (展开) / 60px (折叠) |
| 顶部栏高度 | 50px |
| 内容区内边距 | 16px |
| 侧边栏背景 | `bg-sidebar` (白色或暗色) |
| 顶部栏背景 | `bg-header` (白色或暗色) |
| 内容区背景 | `bg-background-deep` (浅灰) |

### 6.3 菜单样式

```vue
<template>
  <div
    :class="[
      'flex w-full cursor-pointer items-center justify-between overflow-hidden px-5 py-[9px] text-sm transition-all',
      active
        ? 'text-foreground font-medium'
        : 'text-muted-foreground hover:text-foreground hover:bg-accent',
    ]"
  >
    <div class="flex items-center gap-3 overflow-hidden">
      <Icon v-if="icon" :icon="icon" class="size-4 flex-shrink-0" />
      <span class="truncate">{{ title }}</span>
    </div>
    <Icon v-if="children?.length" icon="lucide:chevron-right" class="size-4" />
  </div>
</template>
```

**特征**：
- 高度：`py-[9px]` (约 36px 总高)
- 内边距：`px-5`
- 文字：`text-sm`
- 激活态：`text-foreground font-medium`
- 悬停态：`hover:bg-accent hover:text-foreground`
- 图标：`size-4` (16px)

---

## 七、全局样式

### 7.1 基础重置

```css
/* global.css */
@layer base {
  html {
    font-size: 16px;
  }

  body {
    font-family: var(--font-family);
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;
  }

  *,
  ::before,
  ::after {
    border-color: hsl(var(--border));
  }
}
```

### 7.2 滚动条样式

```css
/* 全局滚动条 */
::-webkit-scrollbar {
  height: 6px;
  width: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: hsl(var(--border));
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: hsl(var(--muted-foreground) / 0.3);
}

/* 暗色模式滚动条 */
.dark ::-webkit-scrollbar-thumb {
  background: hsl(var(--border));
}
```

### 7.3 辅助功能模式

```css
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

## 八、工具函数

### 8.1 cn() 函数

```typescript
// utils.ts
import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
```

**作用**：
- `clsx`：条件类名合并
- `twMerge`：解决 Tailwind 类名冲突（如 `px-2 px-4` → `px-4`）

### 8.2 使用示例

```vue
<script setup lang="ts">
import { cn } from '@/utils';

const props = defineProps<{ class?: string }>();
</script>

<template>
  <div :class="cn('bg-primary text-primary-foreground', props.class)">
    内容
  </div>
</template>
```

---

## 九、在新项目中复刻

### 9.1 步骤 1：安装依赖

```bash
npm install vue@^3.4 tailwindcss@^3.4 postcss autoprefixer
npm install radix-vue class-variance-authority clsx tailwind-merge
npm install tailwindcss-animate @tailwindcss/typography
npm install -D @types/node typescript
```

### 9.2 步骤 2：初始化 Tailwind

```bash
npx tailwindcss init -p
```

### 9.3 步骤 3：配置 Tailwind

将上文 **4.1 完整配置** 复制到 `tailwind.config.ts`。

### 9.4 步骤 4：创建 CSS 变量文件

**`src/styles/design-tokens.css`**

将上文 **3.1 CSS 变量体系** 和 **3.2 暗色主题覆盖** 复制到此文件。

### 9.5 步骤 5：创建全局样式

**`src/styles/global.css`**

```css
@tailwind base;
@tailwind components;
@tailwind utilities;

@layer base {
  :root {
    /* 复制 design-tokens.css 内容 */
  }

  .dark {
    /* 复制暗色主题内容 */
  }

  html {
    font-size: 16px;
  }

  body {
    font-family: var(--font-family);
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;
  }

  *, ::before, ::after {
    border-color: hsl(var(--border));
  }
}

/* 滚动条样式 */
::-webkit-scrollbar { height: 6px; width: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: hsl(var(--border)); border-radius: 3px; }
::-webkit-scrollbar-thumb:hover { background: hsl(var(--muted-foreground) / 0.3); }
```

### 9.6 步骤 6：创建工具函数

**`src/utils/cn.ts`**

```typescript
import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
```

### 9.7 步骤 7：创建基础组件

复制上文的 **Button、Badge、Input、Switch、Card、Table、Avatar** 组件代码。

### 9.8 步骤 8：在 main.ts 中引入

```typescript
import { createApp } from 'vue';
import App from './App.vue';

import './styles/global.css';

createApp(App).mount('#app');
```

---

## 十、风格检查清单

在新项目中实现时，对照以下清单确保风格一致：

| 检查项 | 标准 | ✅ |
|--------|------|-----|
| 颜色格式 | 使用 HSL CSS 变量 | ☐ |
| 圆角 | 默认 0.5rem，组件统一使用 `rounded-md`/`rounded-lg` | ☐ |
| 边框 | 使用 `border-border` 变量，1px 实线 | ☐ |
| 阴影 | 极轻微或无边框阴影为主 | ☐ |
| 字体 | 系统字体栈，16px 基准 | ☐ |
| 按钮高度 | 默认 h-9 (36px)，小尺寸 h-8 (32px) | ☐ |
| 输入框高度 | h-9 (36px)，与按钮一致 | ☐ |
| 间距 | 4px 基准（Tailwind 默认） | ☐ |
| 过渡 | 所有交互元素使用 `transition-colors` | ☐ |
| 聚焦环 | `focus-visible:ring-1 focus-visible:ring-ring` | ☐ |
| 暗色模式 | 通过 `.dark` 类切换，使用 `darkMode: 'selector'` | ☐ |
| 禁用态 | `disabled:opacity-50 disabled:pointer-events-none` | ☐ |

---

> **文档完成**。按照以上指南，你可以在新项目中复刻出与 `aryn-mall-ui` 完全一致的 UI 视觉风格。
