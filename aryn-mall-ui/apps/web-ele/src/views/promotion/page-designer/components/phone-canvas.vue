<script setup lang="ts">
import type { CanvasThemeVars } from '../schema/theme-presets';
import type { DecorationSection, PageSettings } from '../schema/types';

import type { PageDesignType } from '#/api/promotion/page-design';

import { computed } from 'vue';

import { ElEmpty } from 'element-plus';

import { resolveEffectivePageTheme } from '../schema/effective-theme';
import {
  normalizeNavigationScrollEffect,
  resolveNavigationScrollAppearance,
  resolveNavigationScrollSurface,
} from '../schema/navigation-scroll';
import { resolveNavigationAppearance } from '../schema/navigation-style';
import { withAlpha } from '../schema/theme-presets';
import CanvasPageShell from './canvas-page-shell.vue';
import CanvasSectionList from './canvas-section-list.vue';

const props = defineProps<{
  page: PageSettings;
  pageName: string;
  pageType: PageDesignType;
  /**
   * 画布预览的滚动距离（本地 UI 态，缺省 0）：只参与导航栏「滚动渐显」的
   * 样式计算，绝不写 document、也不触发 patchPage / 撤销记录。
   * 工具栏「顶部 / 滚动后」切换与 0–210px 滑杆修改它。
   */
  scrollTop?: number;
  sections: DecorationSection[];
  selectedId?: string;
  /** 是否展示原生页面骨架（内嵌页型默认开启，运营可关掉只看装修块） */
  showPageShell: boolean;
  /**
   * 主题 CSS 变量（页面引用主题 > 商城默认主题 > 内置默认红），
   * 与 C 端 App.ku.vue / diy 页面级覆盖使用同一套 `--wot-color-theme-*` 变量名，
   * 画布组件用 var() 消费即可与管理端预览、小程序实机三端同源。
   * 除主色/辅色外还包含页面底色与导航配色：画布用它们覆盖页面自存的
   * 品牌色（含模板种子写死的颜色），与 C 端 diy 渲染器有效主题口径一致。
   */
  themeVars?: CanvasThemeVars;
  zoom: number;
}>();

const emit = defineEmits<{
  duplicate: [id: string];
  move: [id: string, toIndex: number, sectionId: string];
  remove: [id: string];
  select: [id?: string];
  selectSection: [sectionId: string];
}>();

/**
 * 内嵌页型（商详/分类/个人中心）：装修块只是页面中的一段内容。
 *
 * C 端这些页面的 `DiyPage` 都传了 `embedded`，渲染器据此**不渲染装修自带的导航栏**
 * 且不撑满 100vh（见 aryn-mall-uniapp/src/components/diy/index.vue）。画布必须同构，
 * 否则运营在画布里看到一条页面中间的导航栏、保存后到实机却没有。
 */
const isEmbeddedPage = computed(() => ['2', '3', '4'].includes(props.pageType));

/** 只有整页 DIY（微页面/首页）才按 Schema 的 navigation 设置画导航栏 */
const showNavigation = computed(
  () => !isEmbeddedPage.value && props.page.navigation.visible,
);

const showShell = computed(() => isEmbeddedPage.value && props.showPageShell);

/**
 * 页面有效主题色：编辑器传入了主题（页面指定主题或商城默认主题）时覆盖
 * 页面自存配色，未传（主题库不可用）时保留页面原色。
 * 与 C 端 diy 渲染器同一解析函数（preview-parity.test.ts 守卫两端同源）。
 */
const effectivePage = computed(() =>
  resolveEffectivePageTheme(props.page, props.themeVars),
);

/**
 * 导航栏外观：页面设置可选「跟随首个区块」，把首区块的背景（渐变顶边起色 /
 * 纯色）直接用作导航背景，运营不必再手动对齐导航色与区块渐变。
 * 解析函数与草稿预览、C 端实机同源（见 schema/navigation-style.ts）。
 */
const navigationAppearance = computed(() =>
  resolveNavigationAppearance(props.page, effectivePage.value, props.sections),
);

/** 导航栏内联样式：左右渐变整段复用；其余方向就是纯色顶边起色 */
const navigationStyle = computed(() => ({
  backgroundColor: navigationAppearance.value.backgroundColor,
  backgroundImage: navigationAppearance.value.backgroundImage,
  backgroundPosition: navigationAppearance.value.backgroundImage
    ? 'top center'
    : undefined,
  backgroundRepeat: navigationAppearance.value.backgroundImage
    ? 'no-repeat'
    : undefined,
  backgroundSize: navigationAppearance.value.backgroundImage
    ? '100% 100%'
    : undefined,
  color: navigationAppearance.value.textColor,
}));

/**
 * 滚动渐显（沉浸式导航）：与草稿预览、C 端 hr-navbar 共用同一份解析函数。
 *
 * 两层 DOM 分工（与 C 端同构）：
 *   · base 层 = 当前静态外观（纯色 / 左右渐变），opacity = 1-p 渐隐；
 *   · surface 层 = 目标底色（磨砂白 / 主题色 / 自定义色），opacity = p 渐显；
 *   · hairline 只在目标底色为浅色时出现，p 过半后渐显。
 * mode!=='fade' 时整条链路返回 progress=0，渲染与改动前完全一致。
 */
const scrollEffect = computed(() =>
  normalizeNavigationScrollEffect(props.page.navigation.scrollEffect),
);
const fadeEnabled = computed(() => scrollEffect.value.mode === 'fade');
/** 静态部分：目标底色外观（只随页面配置/主题变化） */
const scrollSurface = computed(() =>
  resolveNavigationScrollSurface(
    props.page,
    effectivePage.value,
    navigationAppearance.value,
  ),
);
/** 动态部分：滚动进度 → 导航外观（scrollTop 是本组件入参，不进 document） */
const scrollAppearance = computed(() =>
  resolveNavigationScrollAppearance(
    navigationAppearance.value,
    scrollSurface.value,
    scrollEffect.value,
    Math.max(0, props.scrollTop ?? 0),
  ),
);

/** 导航外层样式：无渐显时沿用现状；渐显时背景交给 base/surface 两层，只留文字色 */
const navigationOuterStyle = computed(() =>
  fadeEnabled.value
    ? { color: scrollAppearance.value.textColor }
    : navigationStyle.value,
);

/** base 层：静态外观整体按 1-p 渐隐（渐变用背景图，不做颜色过渡） */
const navigationBaseLayerStyle = computed(() => ({
  ...navigationStyle.value,
  opacity: scrollAppearance.value.baseOpacity,
}));

/** surface 层：目标底色带目标态 alpha，层透明度 = 进度 p */
const navigationSurfaceLayerStyle = computed(() => ({
  backgroundColor: scrollLayerColor(
    scrollAppearance.value.surfaceColor,
    scrollAppearance.value.surfaceAlpha,
  ),
  opacity: scrollAppearance.value.surfaceOpacity,
  // 管理端只跑桌面浏览器，写标准属性即可；-webkit- 前缀由 C 端 hr-navbar 负责
  backdropFilter: scrollAppearance.value.blur
    ? 'blur(18px) saturate(160%)'
    : undefined,
}));

/**
 * 目标底色 + 目标态 alpha 拼成 CSS 颜色：hex / rgb() / rgba() 统一成 rgba()。
 * 与 page-preview/components/preview-canvas.vue 同名同实现（两个渲染面各留一份，
 * 跨目录复用会破坏「预览画布独立于编辑器」的既有依赖方向）。
 */
function scrollLayerColor(color: string, alpha: number): string {
  const value = color.trim();
  const hex = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(value);
  if (hex) {
    const raw = hex[1] ?? '';
    const base =
      raw.length === 3 ? [...raw].map((char) => char + char).join('') : raw;
    const channels = [0, 2, 4].map((index) =>
      Number.parseInt(base.slice(index, index + 2), 16),
    );
    return `rgba(${channels[0]}, ${channels[1]}, ${channels[2]}, ${alpha})`;
  }
  const rgb = /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)/i.exec(value);
  if (!rgb) return `rgba(255, 255, 255, ${alpha})`;
  return `rgba(${rgb[1]}, ${rgb[2]}, ${rgb[3]}, ${alpha})`;
}

/**
 * 页面背景色/图在**整页 DIY**（微页面/首页）下铺满整个手机壳；
 * 内嵌页型下改由骨架内的装修段落承接（见 embeddedStyle）。
 */
const canvasStyle = computed(() => ({
  backgroundColor: showShell.value
    ? undefined
    : effectivePage.value.backgroundColor,
  backgroundImage:
    !showShell.value && props.page.backgroundImage
      ? `url(${props.page.backgroundImage})`
      : undefined,
  // 主题变量沿手机壳节点树继承，画布组件里的 var(--wot-color-theme-*) 由此取值。
  // -background 是页面底色（主色混白 92%），C 端由 wd-config-provider 的
  // colorThemeBackground 下发；画布此前漏挂，导致促销组件的浅色面在非暖色主题
  // 下落到 #fff1ee 兜底（蓝/绿主题预览偏粉），这里补齐同口径变量。
  // -primary-soft 是主色 9% tint（服务承诺圆底等浅色面），C 端 diy pageStyle
  // 同口径预计算（withAlpha），非法色值不下发、组件侧走 rgba 兜底。
  '--wot-color-theme-primary': props.themeVars?.primaryColor || undefined,
  '--wot-color-theme-secondary': props.themeVars?.secondaryColor || undefined,
  '--wot-color-theme-background':
    props.themeVars?.pageBackgroundColor || undefined,
  '--wot-color-theme-primary-soft':
    withAlpha(props.themeVars?.primaryColor || '', '17') || undefined,
  transform: `scale(${props.zoom})`,
}));

/**
 * 内嵌页型下页面背景只作用在装修块自己那一段。
 *
 * C 端 `diy/index.vue` 的 `pageStyle`（页面背景色/图）绑在 `.diy-page` 根节点上，
 * 内嵌场景该节点只包住装修区块，整页底色仍由各页面自己决定（分类页是 #f4f5f7）。
 */
const embeddedStyle = computed(() => ({
  backgroundColor: effectivePage.value.backgroundColor,
  backgroundImage: props.page.backgroundImage
    ? `url(${props.page.backgroundImage})`
    : undefined,
  backgroundPosition: props.page.backgroundImage ? 'top center' : undefined,
  backgroundRepeat: props.page.backgroundImage ? 'no-repeat' : undefined,
  backgroundSize: props.page.backgroundImage ? '100% auto' : undefined,
}));

const isEmpty = computed(() =>
  props.sections.every((section) => section.components.length === 0),
);
</script>

<template>
  <div class="canvas-stage" @click.self="emit('select', undefined)">
    <div class="phone-shell" :style="canvasStyle">
      <div
        v-if="showNavigation"
        class="phone-navigation"
        :style="navigationOuterStyle"
      >
        <!-- 渐显态：base（当前外观，1-p 渐隐）+ surface（目标底色，p 渐显）+ 细线 -->
        <template v-if="fadeEnabled">
          <div
            class="phone-navigation__base"
            :style="navigationBaseLayerStyle"
          ></div>
          <div
            class="phone-navigation__surface"
            :class="{ 'is-blur': scrollAppearance.blur }"
            :style="navigationSurfaceLayerStyle"
          ></div>
          <div
            class="phone-navigation__hairline"
            :style="{ opacity: scrollAppearance.hairlineOpacity }"
          ></div>
        </template>
        <span class="phone-navigation__title">
          {{ page.navigation.title || pageName }}
        </span>
      </div>

      <!--
        内嵌页型套原生骨架：让运营看到装修块落在页面哪一段、能用的真实宽度是多少
        （分类页装修在右栏商品流内，可用宽度只有 375 - 88 = 287px）。
        骨架里的插槽就是装修块的真实落点，区块仍在外层受拖拽/选中逻辑管辖。
      -->
      <CanvasPageShell
        v-if="showShell"
        :page-type="pageType"
        @click.self="emit('select', undefined)"
      >
        <div :style="embeddedStyle">
          <ElEmpty
            v-if="isEmpty"
            :image-size="72"
            description="从左侧添加组件"
          />
          <CanvasSectionList
            v-else
            :sections="sections"
            :selected-id="selectedId"
            :page-background="effectivePage.backgroundColor"
            @duplicate="emit('duplicate', $event)"
            @move="(id, index, sectionId) => emit('move', id, index, sectionId)"
            @remove="emit('remove', $event)"
            @select="emit('select', $event)"
            @select-section="emit('selectSection', $event)"
          />
        </div>
      </CanvasPageShell>

      <template v-else>
        <ElEmpty v-if="isEmpty" :image-size="72" description="从左侧添加组件" />
        <CanvasSectionList
          v-else
          :sections="sections"
          :selected-id="selectedId"
          :page-background="effectivePage.backgroundColor"
          @duplicate="emit('duplicate', $event)"
          @move="(id, index, sectionId) => emit('move', id, index, sectionId)"
          @remove="emit('remove', $event)"
          @select="emit('select', $event)"
          @select-section="emit('selectSection', $event)"
        />
      </template>
    </div>
  </div>
</template>

<style scoped>
.canvas-stage {
  display: flex;
  justify-content: center;
  min-width: 520px;
  min-height: 100%;
  padding: 32px 72px 100px;
  overflow: auto;
  background: var(--el-fill-color-light);
}

.phone-shell {
  /* 内容宽必须正好是设备屏宽 375px（= 750rpx）：骨架里分类页左栏 88px 是实数，
     内容区若被边框吃掉 2px，右栏量出来就是 285px 而不是真实的 287px。
     故用 content-box + 外扩 1px 的 box-shadow 画描边：不占布局，也不会像
     内偏移 outline 那样盖住静态的导航条（区块是定位元素能盖住 outline，
     结果导航两侧比首区块各窄 1px，接缝处看着对不齐）。 */
  box-sizing: content-box;
  width: 375px;
  min-height: 667px;
  overflow: visible;

  /* 屏体兜底底色：行内会按页面有效主题色覆盖，这里保持中性令牌 */
  background: var(--el-bg-color);
  background-repeat: no-repeat;
  background-position: top center;
  background-size: 100% auto;
  box-shadow:
    0 0 0 1px var(--el-border-color),
    var(--el-box-shadow);
  transform-origin: top center;
}

.phone-navigation {
  position: relative;
  display: flex;
  align-items: end;
  justify-content: center;
  height: 84px;
  padding: 0 44px 10px;
  font-size: 16px;
  font-weight: 600;
  text-align: center;
}

.phone-navigation__title {
  position: relative;
  z-index: 1;
  transition: color 0.16s linear;
}

/* 背景两层绝对铺满导航条：base = 当前外观，surface = 目标底色 */
.phone-navigation__base,
.phone-navigation__surface {
  position: absolute;
  inset: 0;
  pointer-events: none;
  transition: opacity 0.16s linear;
}

.phone-navigation__surface.is-blur {
  backdrop-filter: blur(18px) saturate(160%);
}

.phone-navigation__hairline {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 1px;
  pointer-events: none;
  background: rgb(15 23 42 / 8%);
  transition: opacity 0.16s linear;
}
</style>
