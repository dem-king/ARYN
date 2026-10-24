<script setup lang="ts">
import type { CanvasThemeVars } from '../../page-designer/schema/theme-presets';
import type { DecorationDocument } from '../../page-designer/schema/types';

import type { PageDesignType } from '#/api/promotion/page-design';

import { computed } from 'vue';

import { isEmbeddedPageType } from '#/api/promotion/page-design';

import CanvasPageShell from '../../page-designer/components/canvas-page-shell.vue';
import { getComponentDefinition } from '../../page-designer/registry/component-registry';
import { resolveEffectivePageTheme } from '../../page-designer/schema/effective-theme';
import { resolveNavigationAppearance } from '../../page-designer/schema/navigation-style';
import { buildSectionStyle } from '../../page-designer/schema/section-style';
import {
  derivePageBackgroundColor,
  withAlpha,
} from '../../page-designer/schema/theme-presets';

const props = withDefaults(
  defineProps<{
    document: DecorationDocument;
    /** 空装修占位文案；生效卡片给未配置的槽位传「未配置装修」 */
    emptyText?: string;
    /**
     * 传 false 时画布高度随内容收缩（弹窗内嵌用）；
     * 默认撑满视口，供草稿预览整页使用。
     */
    fullHeight?: boolean;
    pageName: string;
    /**
     * 页型决定内嵌页型（商详/分类/个人中心）是否套原生页面骨架，
     * 与编辑器画布（phone-canvas）同构；缺省按微页面整页渲染。
     */
    pageType?: PageDesignType;
    /**
     * 注入的有效主题色（商城默认主题/候选主题，调用方解析后传入）：
     * 覆盖页面自存的品牌色，与编辑器画布、C 端实机三方一致；缺省保留页面原色。
     * 页面自带固化主题（themeSnapshot）时不参与，见 hasPageTheme。
     */
    themeVars?: CanvasThemeVars;
  }>(),
  {
    emptyText: undefined,
    fullHeight: true,
    pageType: '0',
    themeVars: undefined,
  },
);

/**
 * 内嵌页型（商详/分类/个人中心）：装修块只是页面中的一段内容，
 * C 端 `DiyPage` 传 `embedded` 时不渲染装修自带导航栏，预览必须同构。
 */
const isEmbeddedPage = computed(() => isEmbeddedPageType(props.pageType));

/**
 * 页面自带固化主题（发布时 themeRef 固化为 themeSnapshot，migrate 已把快照色
 * 合并进 page 配置并暴露 themePrimaryColor）：与 C 端 diy 渲染器同一规则，
 * 不再叠加调用方注入的主题（生效卡片传商城默认、商城主题页传候选主题），
 * CSS 变量也改挂快照主色/辅色，区块内 var(--wot-color-theme-*) 取色与实机一致。
 */
const hasPageTheme = computed(() => Boolean(props.document.themePrimaryColor));

/** 与 phone-canvas、C 端 diy 渲染器同一解析函数（preview-parity.test.ts 守卫同源） */
const effectivePage = computed(() =>
  resolveEffectivePageTheme(
    props.document.page,
    hasPageTheme.value ? undefined : props.themeVars,
  ),
);

const pageStyle = computed(() => ({
  backgroundColor: effectivePage.value.backgroundColor,
  backgroundImage: props.document.page.backgroundImage
    ? `url(${props.document.page.backgroundImage})`
    : undefined,
}));

/**
 * 导航栏外观：与编辑器画布、C 端 diy 渲染器共用「跟随首个区块」解析函数，
 * 页面设置里把导航背景切到跟随首个区块后，预览必须与实机同样取区块顶边起色。
 */
const navigationAppearance = computed(() =>
  resolveNavigationAppearance(
    props.document.page,
    effectivePage.value,
    props.document.sections,
  ),
);

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
 * 画布根节点同时挂主题 CSS 变量：装修组件预览用 var(--wot-color-theme-*)
 * 取色（与编辑器画布 phone-canvas 同一套变量名）。页面自带固化主题时挂快照
 * 主色/辅色（C 端 diy 的 pageStyle 同款），否则挂调用方注入的主题变量；
 * 内嵌页型背景下沉到骨架内，根节点只挂变量不挂页面背景。
 */
const canvasRootStyle = computed(() => ({
  ...(isEmbeddedPage.value ? {} : pageStyle.value),
  ...(hasPageTheme.value
    ? {
        '--wot-color-theme-primary': props.document.themePrimaryColor,
        '--wot-color-theme-secondary':
          props.document.themeSecondaryColor ??
          props.document.themePrimaryColor,
        // 页面固化主题的浅色面：主色混白 92%（与 C 端主题衍生同口径）
        '--wot-color-theme-background': derivePageBackgroundColor(
          props.document.themePrimaryColor || '',
        ),
      }
    : {
        '--wot-color-theme-primary': props.themeVars?.primaryColor || undefined,
        '--wot-color-theme-secondary':
          props.themeVars?.secondaryColor || undefined,
        '--wot-color-theme-background':
          props.themeVars?.pageBackgroundColor || undefined,
      }),
  // 主色 9% tint（服务承诺圆底等浅色面）：C 端 diy pageStyle 同口径预计算，
  // 有效主色 = 页面固化主题 > 注入主题，非法色值不下发、组件侧走 rgba 兜底
  '--wot-color-theme-primary-soft':
    withAlpha(
      props.document.themePrimaryColor || props.themeVars?.primaryColor || '',
      '17',
    ) || undefined,
}));

/**
 * 内嵌页型下页面背景只作用在装修块自己那一段（整页底色由原生骨架决定），
 * 口径与 phone-canvas 的 embeddedStyle 一致。
 */
const embeddedStyle = computed(() => ({
  backgroundColor: effectivePage.value.backgroundColor,
  backgroundImage: props.document.page.backgroundImage
    ? `url(${props.document.page.backgroundImage})`
    : undefined,
  backgroundPosition: props.document.page.backgroundImage
    ? 'top center'
    : undefined,
  backgroundRepeat: props.document.page.backgroundImage
    ? 'no-repeat'
    : undefined,
  backgroundSize: props.document.page.backgroundImage ? '100% auto' : undefined,
}));

const isEmpty = computed(() =>
  props.document.sections.every((section) => section.components.length === 0),
);

/** 空区块不渲染（与编辑画布、C 端 diy 渲染器同口径），避免删空组件后残留灰条占位 */
const visibleSections = computed(() =>
  props.document.sections.filter((section) => section.components.length > 0),
);

function sectionStyle(section: DecorationDocument['sections'][number]) {
  // 吸顶层级 10：与 C 端实机一致（此页不与编辑器操作按钮共存）
  return buildSectionStyle(section.style, { stickyZIndex: 10 });
}
</script>

<template>
  <main
    class="preview-canvas"
    :class="{ 'preview-canvas--inline': !fullHeight }"
    :style="canvasRootStyle"
  >
    <header
      v-if="!isEmbeddedPage && document.page.navigation.visible"
      class="preview-navigation"
      :style="navigationStyle"
    >
      {{ document.page.navigation.title || pageName }}
    </header>

    <!-- 内嵌页型套原生骨架：装修块的真实落点与列宽在骨架里才看得见 -->
    <CanvasPageShell v-if="isEmbeddedPage" :page-type="pageType">
      <div class="preview-embedded" :style="embeddedStyle">
        <div v-if="isEmpty" class="preview-empty">
          {{ emptyText ?? '该页面还没有装修内容' }}
        </div>
        <div
          v-for="section in visibleSections"
          :key="section.id"
          class="preview-section"
          :style="sectionStyle(section)"
        >
          <template v-for="component in section.components" :key="component.id">
            <component
              :is="getComponentDefinition(component.type)?.preview"
              v-if="getComponentDefinition(component.type)?.preview"
              :show-data="component.props"
            />
            <div v-else class="preview-unknown">
              暂不支持预览组件：{{ component.type }}
            </div>
          </template>
        </div>
      </div>
    </CanvasPageShell>

    <template v-else>
      <div v-if="isEmpty" class="preview-empty">
        {{ emptyText ?? '当前草稿还没有组件' }}
      </div>

      <div
        v-for="section in visibleSections"
        :key="section.id"
        class="preview-section"
        :style="sectionStyle(section)"
      >
        <template v-for="component in section.components" :key="component.id">
          <component
            :is="getComponentDefinition(component.type)?.preview"
            v-if="getComponentDefinition(component.type)?.preview"
            :show-data="component.props"
          />
          <div v-else class="preview-unknown">
            暂不支持预览组件：{{ component.type }}
          </div>
        </template>
      </div>
    </template>
  </main>
</template>

<style scoped>
@media (max-width: 375px) {
  .preview-canvas {
    width: 100%;
    box-shadow: none;
  }
}

.preview-canvas {
  width: min(100%, 375px);
  min-height: 100dvh;
  margin: 0 auto;
  overflow: hidden;
  background-repeat: no-repeat;
  background-position: top center;
  background-size: 100% auto;
  box-shadow: 0 0 32px rgb(15 23 42 / 10%);
}

/* 弹窗等内嵌场景：高度随内容收缩，去掉整页投影 */
.preview-canvas--inline {
  min-height: auto;
  box-shadow: none;
}

.preview-navigation {
  display: flex;
  align-items: end;
  justify-content: center;
  min-height: 84px;
  padding: 24px 44px 10px;
  font-size: 16px;
  font-weight: 600;
  text-align: center;
}

.preview-embedded {
  background-repeat: no-repeat;
  background-position: top center;
  background-size: 100% auto;
}

/* 空装修位用虚线 + 浅底占位：纯文字/纯 --el-fill-color 在白色画布上等同白屏 */
.preview-empty {
  padding: 28px 12px;
  margin: 12px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  text-align: center;
  background: var(--el-fill-color-lighter);
  border: 1px dashed var(--el-border-color-darker, #cbd5e1);
  border-radius: 8px;
}

.preview-unknown {
  /* 合并原重复选择器：保留后一条规则的最终生效值，避免样式优先级歧义 */
  padding: 16px;
  margin: 12px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  text-align: center;
  border: 1px dashed var(--el-border-color);
}
</style>
