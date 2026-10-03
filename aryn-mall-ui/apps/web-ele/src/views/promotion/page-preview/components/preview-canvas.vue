<script setup lang="ts">
import type { DecorationDocument } from '../../page-designer/schema/types';

import type { PageDesignType } from '#/api/promotion/page-design';

import { computed } from 'vue';

import CanvasPageShell from '../../page-designer/components/canvas-page-shell.vue';
import { getComponentDefinition } from '../../page-designer/registry/component-registry';
import { buildSectionStyle } from '../../page-designer/schema/section-style';

const props = withDefaults(
  defineProps<{
    document: DecorationDocument;
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
  }>(),
  { fullHeight: true, pageType: '0' },
);

/**
 * 内嵌页型（商详/分类/个人中心）：装修块只是页面中的一段内容，
 * C 端 `DiyPage` 传 `embedded` 时不渲染装修自带导航栏，预览必须同构。
 */
const isEmbeddedPage = computed(() => ['2', '3', '4'].includes(props.pageType));

const pageStyle = computed(() => ({
  backgroundColor: props.document.page.backgroundColor,
  backgroundImage: props.document.page.backgroundImage
    ? `url(${props.document.page.backgroundImage})`
    : undefined,
}));

/**
 * 内嵌页型下页面背景只作用在装修块自己那一段（整页底色由原生骨架决定），
 * 口径与 phone-canvas 的 embeddedStyle 一致。
 */
const embeddedStyle = computed(() => ({
  backgroundColor: props.document.page.backgroundColor,
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
    :style="isEmbeddedPage ? undefined : pageStyle"
  >
    <header
      v-if="!isEmbeddedPage && document.page.navigation.visible"
      class="preview-navigation"
      :style="{
        backgroundColor: document.page.navigation.backgroundColor,
        color: document.page.navigation.textColor,
      }"
    >
      {{ document.page.navigation.title || pageName }}
    </header>

    <!-- 内嵌页型套原生骨架：装修块的真实落点与列宽在骨架里才看得见 -->
    <CanvasPageShell v-if="isEmbeddedPage" :page-type="pageType">
      <div class="preview-embedded" :style="embeddedStyle">
        <div v-if="isEmpty" class="preview-empty">该页面还没有装修内容</div>
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
      <div v-if="isEmpty" class="preview-empty">当前草稿还没有组件</div>

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

.preview-empty,
.preview-unknown {
  padding: 48px 20px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.preview-unknown {
  padding: 16px;
  margin: 12px;
  border: 1px dashed var(--el-border-color);
}
</style>
